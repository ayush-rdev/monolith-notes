package com.monolith.notes.data.sync

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class RemoteDavFile(
    val href: String,
    val name: String,
    val isDirectory: Boolean,
    val contentLength: Long,
    val lastModifiedMillis: Long,
    val etag: String
)

data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val e2eeEnabled: Boolean = false,
    val encryptionPassphrase: String = "",
    val autoSyncOnLaunch: Boolean = false
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank()
}

class WebDavClient(private val config: WebDavConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val authHeader: String
        get() = Credentials.basic(config.username, config.password)

    private val normalizedBaseUrl: String
        get() {
            var url = config.serverUrl.trim()
            if (!url.endsWith("/")) url += "/"
            return url
        }

    /**
     * Tests connectivity and credentials with the WebDAV server.
     */
    fun testConnection(): Result<String> {
        return try {
            val request = Request.Builder()
                .url(normalizedBaseUrl)
                .addHeader("Authorization", authHeader)
                .addHeader("Depth", "0")
                .method("PROPFIND", null)
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful || response.code == 207 -> {
                        Result.success("Connected successfully (${response.code})")
                    }
                    response.code == 401 -> {
                        Result.failure(Exception("Authentication failed: invalid username or password"))
                    }
                    response.code == 404 -> {
                        // Directory doesn't exist yet, try creating it
                        val mkcolSuccess = createDirectory("")
                        if (mkcolSuccess) {
                            Result.success("Vault directory created on remote server")
                        } else {
                            Result.failure(Exception("Remote directory not found and could not be created"))
                        }
                    }
                    else -> {
                        Result.failure(Exception("Server returned status ${response.code}: ${response.message}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a directory / collection on the WebDAV server via MKCOL.
     */
    fun createDirectory(subPath: String): Boolean {
        val targetUrl = buildUrl(subPath)
        val request = Request.Builder()
            .url(targetUrl)
            .addHeader("Authorization", authHeader)
            .method("MKCOL", null)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 405 // 405 means already exists
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Lists remote files and collections in the specified directory via PROPFIND Depth: 1.
     */
    fun listDirectory(subPath: String = ""): List<RemoteDavFile> {
        val targetUrl = buildUrl(subPath)
        val requestBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:">
                <D:prop>
                    <D:displayname/>
                    <D:getcontentlength/>
                    <D:getlastmodified/>
                    <D:resourcetype/>
                    <D:getetag/>
                </D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(targetUrl)
            .addHeader("Authorization", authHeader)
            .addHeader("Depth", "1")
            .method("PROPFIND", requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 207) {
                    return emptyList()
                }
                val body = response.body?.string() ?: return emptyList()
                parsePropfindXml(body, targetUrl)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Uploads file data to WebDAV server via PUT.
     */
    fun uploadFile(subPath: String, data: ByteArray): Boolean {
        val targetUrl = buildUrl(subPath)
        val body = data.toRequestBody("application/octet-stream".toMediaType())

        val request = Request.Builder()
            .url(targetUrl)
            .addHeader("Authorization", authHeader)
            .put(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 200..204
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads file data from WebDAV server via GET.
     */
    fun downloadFile(subPath: String): ByteArray? {
        val targetUrl = buildUrl(subPath)
        val request = Request.Builder()
            .url(targetUrl)
            .addHeader("Authorization", authHeader)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Deletes a file or directory from WebDAV server via DELETE.
     */
    fun delete(subPath: String): Boolean {
        val targetUrl = buildUrl(subPath)
        val request = Request.Builder()
            .url(targetUrl)
            .addHeader("Authorization", authHeader)
            .delete()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 404
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildUrl(subPath: String): String {
        val cleanSubPath = subPath.trimStart('/')
        return if (cleanSubPath.isEmpty()) normalizedBaseUrl else "$normalizedBaseUrl$cleanSubPath"
    }

    /**
     * Parses WebDAV multistatus XML response into RemoteDavFile list.
     */
    private fun parsePropfindXml(xml: String, requestUrl: String): List<RemoteDavFile> {
        val result = mutableListOf<RemoteDavFile>()
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentHref = ""
            var currentDisplayName = ""
            var currentContentLength = 0L
            var currentLastModified = 0L
            var isDirectory = false
            var currentEtag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "response" -> {
                                currentHref = ""
                                currentDisplayName = ""
                                currentContentLength = 0L
                                currentLastModified = 0L
                                isDirectory = false
                                currentEtag = ""
                            }
                            "href" -> {
                                currentHref = parser.nextText().trim()
                            }
                            "displayname" -> {
                                currentDisplayName = parser.nextText().trim()
                            }
                            "getcontentlength" -> {
                                currentContentLength = parser.nextText().trim().toLongOrNull() ?: 0L
                            }
                            "getlastmodified" -> {
                                val dateStr = parser.nextText().trim()
                                try {
                                    currentLastModified = dateFormat.parse(dateStr)?.time ?: 0L
                                } catch (_: Exception) {}
                            }
                            "getetag" -> {
                                currentEtag = parser.nextText().trim().trim('"')
                            }
                            "collection" -> {
                                isDirectory = true
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "response") {
                            // Extract relative file name from href
                            val cleanHref = currentHref.removeSuffix("/")
                            val fileName = cleanHref.substringAfterLast('/')

                            // Skip the parent directory itself
                            if (fileName.isNotEmpty() && !isSelfReference(currentHref, requestUrl)) {
                                val effectiveName = currentDisplayName.ifBlank { fileName }
                                result.add(
                                    RemoteDavFile(
                                        href = currentHref,
                                        name = effectiveName,
                                        isDirectory = isDirectory,
                                        contentLength = currentContentLength,
                                        lastModifiedMillis = currentLastModified,
                                        etag = currentEtag
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}

        return result
    }

    private fun isSelfReference(href: String, requestUrl: String): Boolean {
        val cleanHref = href.trimEnd('/')
        val cleanReq = requestUrl.substringAfter("://").substringAfter('/').trimEnd('/')
        return cleanHref.endsWith(cleanReq) || cleanReq.endsWith(cleanHref)
    }
}
