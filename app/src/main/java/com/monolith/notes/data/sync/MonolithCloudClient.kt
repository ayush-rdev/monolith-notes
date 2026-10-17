package com.monolith.notes.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CloudAuthResult(
    val isSuccess: Boolean,
    val token: String? = null,
    val email: String? = null,
    val message: String
)

data class CloudSyncItem(
    val fileName: String,
    val dataBase64: String,
    val clientModified: Long,
    val serverModified: Long = 0L,
    val isDeleted: Boolean = false
)

data class CloudPullResult(
    val isSuccess: Boolean,
    val items: List<CloudSyncItem> = emptyList(),
    val serverTime: Long = 0L,
    val message: String
)

data class CloudPushResult(
    val isSuccess: Boolean,
    val pushedCount: Int = 0,
    val serverTime: Long = 0L,
    val message: String
)

data class MonolithCloudConfig(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val email: String = "",
    val token: String = "",
    val encryptionKey: String = "",
    val autoSyncOnLaunch: Boolean = true,
    val lastServerSyncTime: Long = 0L
) {
    val isLoggedIn: Boolean
        get() = token.isNotBlank() && email.isNotBlank()

    companion object {
        const val DEFAULT_SERVER_URL = "https://sync.monolithnotes.com"
    }
}

object MonolithCloudClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun sanitizeUrl(serverUrl: String): String {
        var url = serverUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url.removeSuffix("/")
    }

    suspend fun register(serverUrl: String, email: String, password: String): CloudAuthResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${sanitizeUrl(serverUrl)}/api/v1/auth/register"
            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("password", password)
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }

                if (response.isSuccessful && json.optBoolean("success", false)) {
                    val token = json.getString("token")
                    val resEmail = json.optString("email", email)
                    CloudAuthResult(isSuccess = true, token = token, email = resEmail, message = "Account created successfully")
                } else {
                    val errorMsg = json.optString("message", "Registration failed (HTTP ${response.code})")
                    CloudAuthResult(isSuccess = false, message = errorMsg)
                }
            }
        } catch (e: Exception) {
            CloudAuthResult(isSuccess = false, message = "Connection error: ${e.message}")
        }
    }

    suspend fun login(serverUrl: String, email: String, password: String): CloudAuthResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${sanitizeUrl(serverUrl)}/api/v1/auth/login"
            val payload = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("password", password)
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }

                if (response.isSuccessful && json.optBoolean("success", false)) {
                    val token = json.getString("token")
                    val resEmail = json.optString("email", email)
                    CloudAuthResult(isSuccess = true, token = token, email = resEmail, message = "Login successful")
                } else {
                    val errorMsg = json.optString("message", "Login failed (HTTP ${response.code})")
                    CloudAuthResult(isSuccess = false, message = errorMsg)
                }
            }
        } catch (e: Exception) {
            CloudAuthResult(isSuccess = false, message = "Connection error: ${e.message}")
        }
    }

    suspend fun push(serverUrl: String, token: String, items: List<CloudSyncItem>): CloudPushResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            return@withContext CloudPushResult(isSuccess = true, pushedCount = 0, message = "No items to push")
        }

        try {
            val endpoint = "${sanitizeUrl(serverUrl)}/api/v1/sync/push"
            val jsonArray = JSONArray()

            for (item in items) {
                jsonArray.put(JSONObject().apply {
                    put("file_name", item.fileName)
                    put("data_base64", item.dataBase64)
                    put("client_modified", item.clientModified)
                    put("is_deleted", item.isDeleted)
                })
            }

            val payload = JSONObject().apply {
                put("items", jsonArray)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }

                if (response.isSuccessful && json.optBoolean("success", false)) {
                    val pushedCount = json.optInt("pushed_count", items.size)
                    val serverTime = json.optLong("server_time", System.currentTimeMillis())
                    CloudPushResult(isSuccess = true, pushedCount = pushedCount, serverTime = serverTime, message = "Pushed $pushedCount items")
                } else {
                    val errorMsg = json.optString("message", "Push failed (HTTP ${response.code})")
                    CloudPushResult(isSuccess = false, message = errorMsg)
                }
            }
        } catch (e: Exception) {
            CloudPushResult(isSuccess = false, message = "Push error: ${e.message}")
        }
    }

    suspend fun pull(serverUrl: String, token: String, since: Long): CloudPullResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${sanitizeUrl(serverUrl)}/api/v1/sync/pull?since=$since"

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                val json = try { JSONObject(bodyStr) } catch (_: Exception) { JSONObject() }

                if (response.isSuccessful && json.optBoolean("success", false)) {
                    val jsonItems = json.optJSONArray("items") ?: JSONArray()
                    val resultItems = mutableListOf<CloudSyncItem>()

                    for (i in 0 until jsonItems.length()) {
                        val obj = jsonItems.getJSONObject(i)
                        resultItems.add(
                            CloudSyncItem(
                                fileName = obj.getString("file_name"),
                                dataBase64 = obj.getString("data_base64"),
                                clientModified = obj.optLong("client_modified", 0L),
                                serverModified = obj.optLong("server_modified", 0L),
                                isDeleted = obj.optBoolean("is_deleted", false)
                            )
                        )
                    }

                    val serverTime = json.optLong("server_time", System.currentTimeMillis())
                    CloudPullResult(isSuccess = true, items = resultItems, serverTime = serverTime, message = "Pulled ${resultItems.size} items")
                } else {
                    val errorMsg = json.optString("message", "Pull failed (HTTP ${response.code})")
                    CloudPullResult(isSuccess = false, message = errorMsg)
                }
            }
        } catch (e: Exception) {
            CloudPullResult(isSuccess = false, message = "Pull error: ${e.message}")
        }
    }
}
