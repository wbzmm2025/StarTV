package com.wbzmm.startv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据源仓库:
 * - 当天首次打开: 通过第三方镜像加速下载 wbzmm2025/StarTV 的 startv.json 到本地
 * - 当天已下载过: 直接使用本地缓存, 不重复下载
 * - 联网失败: 回退使用上一次的本地缓存
 */
object SourceRepository {

    private const val PREFS = "startv_prefs"
    private const val KEY_LAST_FETCH = "last_fetch_date"
    private const val FILE_NAME = "startv.json"
    /** APK 内置的离线兜底源 (GitHub 完全打不开时使用) */
    private const val DEFAULT_ASSET = "startv_default.json"

    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 20_000

    /**
     * 下载地址列表: 第三方 GitHub 镜像加速在前, 官方直连在后,
     * 依次尝试, 前一个失败自动切换下一个。
     */
    private val MIRROR_URLS = listOf(
        "https://ghfast.top/https://raw.githubusercontent.com/wbzmm2025/StarTV/main/startv.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/wbzmm2025/StarTV/main/startv.json",
        "https://ghproxy.net/https://raw.githubusercontent.com/wbzmm2025/StarTV/main/startv.json",
        "https://cdn.jsdelivr.net/gh/wbzmm2025/StarTV@main/startv.json",
        "https://raw.githubusercontent.com/wbzmm2025/StarTV/main/startv.json",
    )

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    private fun localFile(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun fetchedToday(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_FETCH, "")
        return last == today()
    }

    private fun markFetched(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_FETCH, today()).apply()
    }

    /** 依次尝试官方地址与镜像, 成功返回内容, 全部失败返回 null */
    private fun download(): String? {
        for (url in MIRROR_URLS) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = CONNECT_TIMEOUT
                conn.readTimeout = READ_TIMEOUT
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "StarTV/1.0")
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    // 简单有效性检查: 必须包含 channels 字段, 防止镜像返回错误页
                    if (text.contains("\"channels\"")) return text
                }
            } catch (_: Exception) {
                // 换下一个镜像
            }
        }
        return null
    }

    /**
     * 主入口: 返回源数据。加载优先级:
     * 1. 当天未下载 -> 联网下载并缓存 (镜像依次尝试)
     * 2. 本地缓存有效 -> 使用本地缓存
     * 3. 都没有 -> 使用 APK 内置离线源 (assets/startv_default.json)
     */
    suspend fun load(context: Context): SourceData = withContext(Dispatchers.IO) {
        val file = localFile(context)
        if (!fetchedToday(context)) {
            val text = download()
            if (text != null) {
                try {
                    // 先校验再落盘, 避免坏数据覆盖好缓存
                    TvJsonParser.parse(text)
                    file.writeText(text)
                    markFetched(context)
                } catch (_: Exception) {
                    // 下载内容不是合法 JSON, 弃用
                }
            }
        }
        val local = parseLocal(file)
        if (local.channels.isNotEmpty()) local else parseAssets(context) ?: local
    }

    private fun parseLocal(file: File): SourceData {
        if (file.exists()) {
            try {
                return TvJsonParser.parse(file.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                // 本地文件损坏, 当作无数据
            }
        }
        return SourceData("", emptyList())
    }

    /** 读取 APK 内置离线源 */
    private fun parseAssets(context: Context): SourceData? = try {
        val text = context.assets.open(DEFAULT_ASSET)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        TvJsonParser.parse(text).copy(isBuiltin = true)
    } catch (_: Exception) {
        null
    }
}
