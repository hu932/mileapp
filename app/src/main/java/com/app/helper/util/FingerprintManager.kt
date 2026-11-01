package com.app.helper.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.app.helper.network.ApiConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object FingerprintManager {
    private const val TAG = "FingerprintManager"
    private const val FP_DIR = "fps"
    private const val ACTIVE_FP_KEY = "active_fp"
    private val JSON = "application/json".toMediaType()

    /** 最近一次获取失败的原因，UI 直接展示给用户。 */
    @Volatile
    var lastError: String = ""

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun getFpDir(context: Context): File {
        val dir = File(context.filesDir, FP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveFingerprintData(context: Context, filename: String, data: String) {
        try {
            File(getFpDir(context), filename).writeText(data)
        } catch (e: Exception) {
            Log.e(TAG, "Save FP failed: $filename", e)
        }
    }

    fun loadFingerprintData(context: Context, filename: String): String? {
        return try {
            val file = File(getFpDir(context), filename)
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    fun setActiveFp(context: Context, filename: String) {
        context.getSharedPreferences("FP", Context.MODE_PRIVATE)
            .edit()
            .putString("useFp", filename)
            .apply()
        context.getSharedPreferences("fp_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(ACTIVE_FP_KEY, filename)
            .apply()
    }

    fun getActiveFpName(context: Context): String? {
        val legacy = context.getSharedPreferences("FP", Context.MODE_PRIVATE)
            .getString("useFp", null)
        if (!legacy.isNullOrEmpty()) return legacy
        return context.getSharedPreferences("fp_prefs", Context.MODE_PRIVATE)
            .getString(ACTIVE_FP_KEY, null)
    }

    fun loadCurrentFp(context: Context): String? {
        val activeName = getActiveFpName(context)
        if (activeName != null) {
            val data = loadFingerprintData(context, activeName)
            if (data != null) return data
        }
        return getFpDir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull()
            ?.let {
                setActiveFp(context, it.name)
                it.readText()
            }
    }

    fun listFpFiles(context: Context): List<FpFileInfo> {
        val activeName = getActiveFpName(context)
        return getFpDir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { FpFileInfo(it.name, it.length(), it.lastModified(), it.name == activeName) }
            ?: emptyList()
    }

    fun deleteFpFile(context: Context, filename: String): Boolean {
        val result = try {
            File(getFpDir(context), filename).delete()
        } catch (_: Exception) {
            false
        }
        if (getActiveFpName(context) == filename) {
            context.getSharedPreferences("FP", Context.MODE_PRIVATE)
                .edit()
                .remove("useFp")
                .apply()
            context.getSharedPreferences("fp_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove(ACTIVE_FP_KEY)
                .apply()
        }
        return result
    }

    /**
     * 向后端申请一份指纹环境。
     *
     * 本地不再生成任何字段 —— 全部由后台下发：
     *   - 每个号拿到独立的一套（内存 / 存储 / 系统包签名等硬件底噪都不同），不会互相牵连
     *   - 后台按手机号做白名单授权，并限制每日额度
     *
     * @return 成功返回后端下发的 fp 对象；失败返回 null，原因见 [lastError]。
     */
    fun generateFullFp(context: Context): JSONObject? {
        val accountId = TokenManager.getUsername().trim()
        if (accountId.isEmpty()) {
            lastError = "请先登录下发ID"
            return null
        }
        val token = TokenManager.getToken().ifBlank { "ajie-inv-$accountId" }
        val payload = JSONObject().apply {
            put("accountId", accountId)
            put("token", token)
            put("platform", "android")
        }
        return try {
            val req = Request.Builder()
                .url(ApiConfig.FP_ENV_URL)
                .post(payload.toString().toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty().trim()
                if (text.isEmpty()) {
                    lastError = "服务端无响应（HTTP ${resp.code}）"
                    return null
                }
                if (!text.startsWith("{")) {
                    lastError = "服务器返回的不是接口数据（HTTP ${resp.code}），请检查后台地址"
                    return null
                }
                val obj = JSONObject(text)
                if (!obj.optBoolean("success", false)) {
                    lastError = obj.optString("error", "获取失败（HTTP ${resp.code}）")
                    return null
                }
                val fp = obj.optJSONObject("data")?.optJSONObject("fp")
                if (fp == null) {
                    lastError = "返回缺少 fp 字段"
                    return null
                }
                lastError = ""
                fp
            }
        } catch (e: Exception) {
            lastError = "网络错误：${e.message}"
            Log.e(TAG, "generate fp failed", e)
            null
        }
    }

    data class FpFileInfo(
        val name: String,
        val size: Long,
        val lastModified: Long,
        val isActive: Boolean
    )
}
