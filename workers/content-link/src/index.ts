// The Worker that gives the app a short-lived link to a content pack (roadmap 12.4c, plan-12 section 6.3).
//
// POST /link  { "subject_id": 1, "schema_version": 7 }  with  Authorization: Bearer <Supabase access token>
//
// 1. verifies the token (signature, expiry, audience, issuer) against the project's keys;
// 2. calls the SQL function register_content_download with the USER'S OWN token, so it runs as the user under row
//    level security: it checks that the user studies the subject and the limit of the day, and writes the log row;
// 3. signs a GET link for the pack in the private R2 bucket, valid for 10 minutes.
//
// The logic (which subject, how many a day) lives in SQL and is tested there (supabase/tests/sync_test.sql). This file
// only verifies, calls and signs.

import { AwsClient } from "aws4fetch";
import { createLocalJWKSet, createRemoteJWKSet, jwtVerify, type JSONWebKeySet } from "jose";

export interface Env {
  SUPABASE_URL: string;
  SUPABASE_ANON_KEY: string;
  R2_ACCOUNT_ID: string;
  R2_BUCKET: string;
  R2_READ_ACCESS_KEY_ID: string;
  R2_READ_SECRET_ACCESS_KEY: string;
  /** Only when the project still signs its tokens with the old shared secret. */
  SUPABASE_JWT_SECRET?: string;
}

export const LINK_SECONDS = 600;

export interface Deps {
  /** Throws when the token is not valid. */
  verifyToken(token: string): Promise<void>;
  fetch: typeof fetch;
  now(): number;
}

interface Release {
  object_path: string;
  content_version: number;
  size_bytes: number;
  sha256: string;
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

export function makeHandler(env: Env, deps: Deps) {
  return async (request: Request): Promise<Response> => {
    const url = new URL(request.url);
    if (url.pathname !== "/link") return json(404, { error: "not_found" });
    if (request.method !== "POST") return json(405, { error: "method_not_allowed" });

    const token = /^Bearer (.+)$/i.exec(request.headers.get("authorization") ?? "")?.[1];
    if (!token) return json(401, { error: "unauthorized" });
    try {
      await deps.verifyToken(token);
    } catch {
      return json(401, { error: "unauthorized" });
    }

    let body: { subject_id?: unknown; schema_version?: unknown };
    try {
      body = await request.json();
    } catch {
      return json(400, { error: "bad_request" });
    }
    const subjectId = Number(body.subject_id);
    const schemaVersion = Number(body.schema_version);
    if (!Number.isInteger(subjectId) || !Number.isInteger(schemaVersion) || subjectId < 1 || schemaVersion < 1) {
      return json(400, { error: "bad_request" });
    }

    // the function runs as the user: their token, the project's public key
    let answer: Response;
    try {
      answer = await deps.fetch(`${env.SUPABASE_URL}/rest/v1/rpc/register_content_download`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          apikey: env.SUPABASE_ANON_KEY,
          authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ p_subject_id: subjectId, p_schema_version: schemaVersion }),
      });
    } catch {
      return json(502, { error: "upstream" });
    }
    if (!answer.ok) {
      const detail = (await answer.json().catch(() => ({}))) as { code?: string; message?: string };
      if (answer.status === 401 || detail.code === "PGRST301") return json(401, { error: "unauthorized" });
      if (detail.message === "download_limit") return json(429, { error: "download_limit" });
      if (detail.code === "42501") return json(403, { error: "subject_not_chosen" });
      if (detail.code === "P0002") return json(404, { error: "no_release" });
      return json(502, { error: "upstream" });
    }
    const rows = (await answer.json()) as Release[] | Release;
    const release = Array.isArray(rows) ? rows[0] : rows;
    if (!release?.object_path) return json(404, { error: "no_release" });

    const link = await presign(env, release.object_path);
    return json(200, {
      url: link,
      content_version: release.content_version,
      schema_version: schemaVersion,
      size_bytes: release.size_bytes,
      sha256: release.sha256,
      expires_at: deps.now() + LINK_SECONDS * 1000,
    });
  };
}

/** A GET link for one object, valid for [LINK_SECONDS]: the signature is in the query, nothing else is needed. */
export async function presign(env: Env, objectPath: string): Promise<string> {
  const client = new AwsClient({
    accessKeyId: env.R2_READ_ACCESS_KEY_ID,
    secretAccessKey: env.R2_READ_SECRET_ACCESS_KEY,
    service: "s3",
    region: "auto",
  });
  const target = new URL(
    `https://${env.R2_ACCOUNT_ID}.eu.r2.cloudflarestorage.com/${env.R2_BUCKET}/${objectPath.split("/").map(encodeURIComponent).join("/")}`,
  );
  target.searchParams.set("X-Amz-Expires", String(LINK_SECONDS));
  const signed = await client.sign(new Request(target, { method: "GET" }), { aws: { signQuery: true } });
  return signed.url;
}

/** The token check against the keys of the Supabase project (or the old shared secret when the project still uses one). */
export function tokenVerifier(env: Env, jwks?: JSONWebKeySet): Deps["verifyToken"] {
  const issuer = `${env.SUPABASE_URL}/auth/v1`;
  if (env.SUPABASE_JWT_SECRET) {
    const key = new TextEncoder().encode(env.SUPABASE_JWT_SECRET);
    return async (token) => {
      await jwtVerify(token, key, { issuer, audience: "authenticated", algorithms: ["HS256"] });
    };
  }
  const keys = jwks ? createLocalJWKSet(jwks) : createRemoteJWKSet(new URL(`${issuer}/.well-known/jwks.json`));
  return async (token) => {
    await jwtVerify(token, keys, { issuer, audience: "authenticated" });
  };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    return makeHandler(env, { verifyToken: tokenVerifier(env), fetch: (...args) => fetch(...args), now: () => Date.now() })(request);
  },
};
