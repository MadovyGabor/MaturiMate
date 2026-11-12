package com.madovygabor.maturimate.pipeline

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class R2UploaderTest {

    // The example of the AWS documentation ("Signature calculations for the authorization header": GET Object with a range).
    @Test
    fun theSignatureMatchesTheAwsDocumentationExample() {
        val headers = SigV4.sign(
            method = "GET", host = "examplebucket.s3.amazonaws.com", path = "/test.txt",
            payloadHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            time = Instant.parse("2013-05-24T00:00:00Z"),
            accessKeyId = "AKIAIOSFODNN7EXAMPLE", secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1", service = "s3", extraHeaders = mapOf("Range" to "bytes=0-9"),
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, " +
                "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41",
            headers["authorization"],
        )
    }

    @Test
    fun anUploadIsSignedForTheEuEndpointOfTheBucket() {
        val uploader = R2Uploader("acct", "maturimate-content", "key", "secret")
        val headers = uploader.sign("PUT", "/maturimate-content/packs/7/1/2026111201/content.db", sha256Hex("x".toByteArray()), Instant.parse("2026-11-12T10:00:00Z"))
        assertEquals("acct.eu.r2.cloudflarestorage.com", headers["host"])
        assertTrue(headers.getValue("authorization").contains("/20261112/auto/s3/aws4_request"))
    }
}
