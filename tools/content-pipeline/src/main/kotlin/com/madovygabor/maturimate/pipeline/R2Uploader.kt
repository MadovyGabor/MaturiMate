package com.madovygabor.maturimate.pipeline

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Where `publish` puts the packs. */
fun interface ObjectStore {
    fun put(key: String, bytes: ByteArray)
}

/**
 * Puts objects into a Cloudflare R2 bucket over its S3 API, signed with AWS signature version 4. The token used is limited
 * to the content bucket and may write only there (the app and the Worker never see it).
 */
class R2Uploader(
    private val accountId: String,
    private val bucket: String,
    private val accessKeyId: String,
    private val secretAccessKey: String,
    private val client: HttpClient = HttpClient.newHttpClient(),
    private val now: () -> Instant = { Instant.now() },
) : ObjectStore {

    private val host get() = "$accountId.eu.r2.cloudflarestorage.com"

    override fun put(key: String, bytes: ByteArray) {
        val path = "/$bucket/" + key.split("/").joinToString("/") { uriEncode(it) }
        val payloadHash = sha256Hex(bytes)
        val headers = sign("PUT", path, payloadHash, now())
        val request = HttpRequest.newBuilder(URI("https://$host$path"))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .also { builder -> headers.forEach { (name, value) -> if (name != "host") builder.header(name, value) } }
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw ExportException("R2 refused $key: ${response.statusCode()} ${response.body().take(300)}")
    }

    /** The headers of a signed request (`host`, `x-amz-content-sha256`, `x-amz-date`, `authorization`). */
    internal fun sign(method: String, path: String, payloadHash: String, time: Instant): Map<String, String> =
        SigV4.sign(method, host, path, payloadHash, time, accessKeyId, secretAccessKey, region = "auto", service = "s3")

    private fun uriEncode(part: String): String =
        part.toByteArray().joinToString("") { b ->
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-_.~") c.toString() else "%%%02X".format(b)
        }
}

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** AWS signature version 4 for one request with a few headers; just what the upload needs. */
internal object SigV4 {
    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun sign(
        method: String,
        host: String,
        path: String,
        payloadHash: String,
        time: Instant,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        service: String,
        extraHeaders: Map<String, String> = emptyMap(),
        query: String = "",
    ): Map<String, String> {
        val stamp = dateTime.format(time)
        val day = stamp.take(8)
        val headers = (extraHeaders + mapOf("host" to host, "x-amz-content-sha256" to payloadHash, "x-amz-date" to stamp))
            .mapKeys { it.key.lowercase() }.toSortedMap()
        val signedNames = headers.keys.joinToString(";")
        val canonical = listOf(
            method, path, query,
            headers.entries.joinToString("") { "${it.key}:${it.value.trim()}\n" },
            signedNames, payloadHash,
        ).joinToString("\n")
        val scope = "$day/$region/$service/aws4_request"
        val toSign = listOf("AWS4-HMAC-SHA256", stamp, scope, sha256Hex(canonical.toByteArray())).joinToString("\n")
        val signingKey = listOf(day, region, service, "aws4_request")
            .fold("AWS4$secretAccessKey".toByteArray()) { key, data -> hmac(key, data) }
        val signature = hmac(signingKey, toSign).joinToString("") { "%02x".format(it) }
        return headers + ("authorization" to "AWS4-HMAC-SHA256 Credential=$accessKeyId/$scope, SignedHeaders=$signedNames, Signature=$signature")
    }

    private fun hmac(key: ByteArray, data: String): ByteArray =
        Mac.getInstance("HmacSHA256").also { it.init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data.toByteArray())
}
