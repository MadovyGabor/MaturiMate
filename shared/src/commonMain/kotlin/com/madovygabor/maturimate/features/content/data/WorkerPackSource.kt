package com.madovygabor.maturimate.features.content.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.content.domain.ContentError
import com.madovygabor.maturimate.features.content.domain.PackSource
import com.madovygabor.maturimate.features.sync.domain.ContentRelease
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The session token of the signed-in user, and a way to get a fresh one. */
interface AccessTokens {
    suspend fun current(): String?
    suspend fun refreshed(): String?
}

/**
 * [PackSource] over the Cloudflare Worker (`workers/content-link`): asks a 10-minute link with the user's token, then
 * downloads the file. The size and the hash are checked by the installer against the release the server announced in the
 * sync, never against what the link answer says.
 */
class WorkerPackSource(
    private val linkUrl: String,
    private val tokens: AccessTokens,
    private val client: HttpClient,
) : PackSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun download(release: ContentRelease): OperationResult<ByteArray, ContentError> {
        if (linkUrl.isBlank()) return OperationResult.Error(ContentError.NOT_CONFIGURED)
        return try {
            val link = when (val first = askLink(release, tokens.current())) {
                // an expired session is refreshed once
                is LinkAnswer.Unauthorized -> askLink(release, tokens.refreshed())
                else -> first
            }
            when (link) {
                is LinkAnswer.Link -> {
                    val file = client.get(link.url)
                    if (!file.status.isSuccess()) OperationResult.Error(ContentError.NETWORK)
                    else {
                        val bytes = file.bodyAsBytes()
                        if (bytes.size.toLong() != release.sizeBytes) OperationResult.Error(ContentError.CORRUPT)
                        else OperationResult.Success(bytes)
                    }
                }
                is LinkAnswer.Failed -> OperationResult.Error(link.error)
                LinkAnswer.Unauthorized -> OperationResult.Error(ContentError.NOT_SIGNED_IN)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Error(ContentError.NETWORK)
        }
    }

    private suspend fun askLink(release: ContentRelease, token: String?): LinkAnswer {
        if (token == null) return LinkAnswer.Unauthorized
        val response = client.post(linkUrl) {
            header("authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LinkRequest.serializer(), LinkRequest(release.subjectId, release.schemaVersion)))
        }
        return when (response.status.value) {
            200 -> LinkAnswer.Link(json.decodeFromString(LinkResponse.serializer(), response.bodyAsText()).url)
            401 -> LinkAnswer.Unauthorized
            403 -> LinkAnswer.Failed(ContentError.FORBIDDEN)
            429 -> LinkAnswer.Failed(ContentError.LIMIT)
            else -> LinkAnswer.Failed(ContentError.NETWORK)
        }
    }

    private sealed interface LinkAnswer {
        data class Link(val url: String) : LinkAnswer
        data object Unauthorized : LinkAnswer
        data class Failed(val error: ContentError) : LinkAnswer
    }

    @Serializable
    private data class LinkRequest(
        @SerialName("subject_id") val subjectId: Long,
        @SerialName("schema_version") val schemaVersion: Long,
    )

    @Serializable
    private data class LinkResponse(val url: String)
}
