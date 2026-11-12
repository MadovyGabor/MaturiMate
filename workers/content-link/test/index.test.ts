import { exportJWK, generateKeyPair, SignJWT, type JSONWebKeySet } from "jose";
import { beforeAll, describe, expect, it } from "vitest";
import { type Env, makeHandler, tokenVerifier } from "../src/index";

const env: Env = {
  SUPABASE_URL: "https://project.supabase.co",
  SUPABASE_ANON_KEY: "anon-key",
  R2_ACCOUNT_ID: "acct",
  R2_BUCKET: "maturimate-content",
  R2_READ_ACCESS_KEY_ID: "read-key",
  R2_READ_SECRET_ACCESS_KEY: "read-secret",
};

let privateKey: CryptoKey;
let jwks: JSONWebKeySet;

beforeAll(async () => {
  const pair = await generateKeyPair("ES256");
  privateKey = pair.privateKey;
  jwks = { keys: [{ ...(await exportJWK(pair.publicKey)), kid: "k1", alg: "ES256", use: "sig" }] };
});

const sign = (claims: { aud?: string; iss?: string; exp?: string | number } = {}, key?: CryptoKey) =>
  new SignJWT({ sub: "user-1", role: "authenticated" })
    .setProtectedHeader({ alg: "ES256", kid: "k1" })
    .setAudience(claims.aud ?? "authenticated")
    .setIssuer(claims.iss ?? `${env.SUPABASE_URL}/auth/v1`)
    .setExpirationTime(claims.exp ?? "1h")
    .sign(key ?? privateKey);

const release = { object_path: "packs/7/1/2026111201/content.db", content_version: 2026111201, size_bytes: 1000, sha256: "a".repeat(64) };

function handler(rpc: (init: RequestInit) => Response | Promise<Response>) {
  const calls: { url: string; init: RequestInit }[] = [];
  const h = makeHandler(env, {
    verifyToken: tokenVerifier(env, jwks),
    fetch: async (input, init) => {
      calls.push({ url: String(input), init: init ?? {} });
      return rpc(init ?? {});
    },
    now: () => 1_000_000,
  });
  return { h, calls };
}

const post = (token: string | null, body: unknown = { subject_id: 1, schema_version: 7 }, path = "/link") =>
  new Request(`https://worker.example${path}`, {
    method: "POST",
    headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  });

const ok = () => new Response(JSON.stringify([release]), { status: 200, headers: { "content-type": "application/json" } });
const failure = (status: number, code: string, message = "") =>
  new Response(JSON.stringify({ code, message }), { status, headers: { "content-type": "application/json" } });

describe("the token", () => {
  it("a missing token is 401", async () => {
    const { h, calls } = handler(ok);
    expect((await h(post(null))).status).toBe(401);
    expect(calls).toHaveLength(0);
  });

  it("an expired token is 401", async () => {
    const { h } = handler(ok);
    expect((await h(post(await sign({ exp: Math.floor(Date.now() / 1000) - 60 })))).status).toBe(401);
  });

  it("a forged signature is 401", async () => {
    const other = await generateKeyPair("ES256");
    const { h, calls } = handler(ok);
    expect((await h(post(await sign({}, other.privateKey)))).status).toBe(401);
    expect(calls).toHaveLength(0);
  });

  it("another audience or issuer is 401", async () => {
    const { h } = handler(ok);
    expect((await h(post(await sign({ aud: "anon" })))).status).toBe(401);
    expect((await h(post(await sign({ iss: "https://evil.example/auth/v1" })))).status).toBe(401);
  });
});

describe("the link", () => {
  it("a good call returns a link for the right key that expires in 10 minutes", async () => {
    const token = await sign();
    const { h, calls } = handler(ok);

    const response = await h(post(token));

    expect(response.status).toBe(200);
    const body = (await response.json()) as Record<string, unknown>;
    const link = new URL(body.url as string);
    expect(link.host).toBe("acct.eu.r2.cloudflarestorage.com");
    expect(link.pathname).toBe("/maturimate-content/packs/7/1/2026111201/content.db");
    expect(link.searchParams.get("X-Amz-Expires")).toBe("600");
    expect(link.searchParams.get("X-Amz-Signature")).toBeTruthy();
    expect(link.searchParams.get("X-Amz-Credential")).toContain("read-key/");
    expect(body.sha256).toBe(release.sha256);
    expect(body.size_bytes).toBe(1000);
    expect(body.expires_at).toBe(1_000_000 + 600_000);
    // the function was called as the user, with the project's public key and the user's own token
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe("https://project.supabase.co/rest/v1/rpc/register_content_download");
    const headers = calls[0].init.headers as Record<string, string>;
    expect(headers.authorization).toBe(`Bearer ${token}`);
    expect(headers.apikey).toBe("anon-key");
    expect(JSON.parse(calls[0].init.body as string)).toEqual({ p_subject_id: 1, p_schema_version: 7 });
  });

  it("the errors of the function become 403, 429 and 404", async () => {
    const token = await sign();
    expect((await handler(() => failure(403, "42501", "subject not chosen")).h(post(token))).status).toBe(403);
    expect((await handler(() => failure(400, "P0001", "download_limit")).h(post(token))).status).toBe(429);
    expect((await handler(() => failure(404, "P0002", "no release")).h(post(token))).status).toBe(404);
    expect((await handler(() => failure(500, "XX000", "boom")).h(post(token))).status).toBe(502);
  });

  it("a Supabase that does not answer is 502", async () => {
    const token = await sign();
    const { h } = handler(() => {
      throw new Error("down");
    });
    expect((await h(post(token))).status).toBe(502);
  });

  it("a bad body or path is refused", async () => {
    const token = await sign();
    const { h } = handler(ok);
    expect((await h(post(token, { subject_id: "x" }))).status).toBe(400);
    expect((await h(post(token, {}))).status).toBe(400);
    expect((await h(post(token, { subject_id: 1, schema_version: 7 }, "/other"))).status).toBe(404);
    expect((await h(new Request("https://worker.example/link", { method: "GET" }))).status).toBe(405);
  });
});
