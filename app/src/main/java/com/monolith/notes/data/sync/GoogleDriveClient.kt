package com.monolith.notes.data.sync

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GoogleDriveFile(
    val id: String,
    val name: String,
    val modifiedTime: Long,
    val size: Long
)

object GoogleDriveClient {
    private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"
    private const val BASE_URL = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Obtains an OAuth 2.0 Bearer access token for Google Drive appDataFolder access.
     */
    suspend fun fetchAccessToken(context: Context, account: Account): String? = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lists all files currently stored in the hidden Google Drive appDataFolder.
     */
    suspend fun listAppDataFiles(accessToken: String): List<GoogleDriveFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<GoogleDriveFile>()
        var pageToken: String? = null

        do {
            val urlBuilder = StringBuilder("$BASE_URL?spaces=appDataFolder&fields=nextPageToken,files(id,name,modifiedTime,size)&pageSize=1000")
            if (pageToken != null) {
                urlBuilder.append("&pageToken=").append(pageToken)
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Failed to list files from Google Drive: ${response.code} - $errorBody")
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            val filesArray = json.optJSONArray("files")
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val fileObj = filesArray.getJSONObject(i)
                    val id = fileObj.getString("id")
                    val name = fileObj.getString("name")
                    val size = fileObj.optLong("size", 0L)
                    val modTimeStr = fileObj.optString("modifiedTime", "")
                    val modifiedTime = parseIsoTime(modTimeStr)
                    result.add(GoogleDriveFile(id, name, modifiedTime, size))
                }
            }
            pageToken = if (json.has("nextPageToken")) json.getString("nextPageToken") else null
        } while (pageToken != null)

        result
    }

    /**
     * Downloads binary content of a file stored in Google Drive.
     */
    suspend fun downloadFile(accessToken: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Failed to download file $fileId from Google Drive: ${response.code}")
        }
        response.body?.bytes() ?: ByteArray(0)
    }

    /**
     * Uploads a new file into the hidden appDataFolder using multipart/related upload.
     */
    suspend fun uploadNewFile(accessToken: String, fileName: String, data: ByteArray): String = withContext(Dispatchers.IO) {
        val boundary = "====MonolithDriveBoundary${System.currentTimeMillis()}===="
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", org.json.JSONArray().put("appDataFolder"))
        }.toString()

        val CRLF = "\r\n"
        val headerPart = "--$boundary$CRLF" +
                "Content-Type: application/json; charset=UTF-8$CRLF$CRLF" +
                metadataJson + CRLF +
                "--$boundary$CRLF" +
                "Content-Type: application/octet-stream$CRLF$CRLF"

        val footerPart = "$CRLF--$boundary--$CRLF"

        val headerBytes = headerPart.toByteArray(Charsets.UTF_8)
        val footerBytes = footerPart.toByteArray(Charsets.UTF_8)

        val totalBytes = ByteArray(headerBytes.size + data.size + footerBytes.size)
        System.arraycopy(headerBytes, 0, totalBytes, 0, headerBytes.size)
        System.arraycopy(data, 0, totalBytes, headerBytes.size, data.size)
        System.arraycopy(footerBytes, 0, totalBytes, headerBytes.size + data.size, footerBytes.size)

        val mediaType = "multipart/related; boundary=$boundary".toMediaType()
        val body = totalBytes.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$UPLOAD_URL?uploadType=multipart")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            throw IOException("Failed to upload file $fileName to Google Drive: ${response.code} - $errorBody")
        }

        val json = JSONObject(response.body?.string() ?: "{}")
        json.getString("id")
    }

    /**
     * Updates content of an existing file in Google Drive.
     */
    suspend fun updateExistingFile(accessToken: String, fileId: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val mediaType = "application/octet-stream".toMediaType()
        val body = data.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$UPLOAD_URL/$fileId?uploadType=media")
            .addHeader("Authorization", "Bearer $accessToken")
            .patch(body)
            .build()

        val response = httpClient.newCall(request).execute()
        response.isSuccessful
    }

    /**
     * Deletes a file from Google Drive.
     */
    suspend fun deleteFile(accessToken: String, fileId: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/$fileId")
            .addHeader("Authorization", "Bearer $accessToken")
            .delete()
            .build()

        val response = httpClient.newCall(request).execute()
        response.isSuccessful
    }

    private fun parseIsoTime(isoString: String): Long {
        if (isoString.isBlank()) return System.currentTimeMillis()
        return try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            try {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                format.parse(isoString)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
