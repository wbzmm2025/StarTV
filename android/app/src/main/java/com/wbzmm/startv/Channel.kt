package com.wbzmm.startv

import org.json.JSONArray
import org.json.JSONObject

/**
 * 频道数据模型, 对应 startv.json 的 channels[] 字段:
 * {"name":"CCTV-1 综合","group":"央视","logo":"http://...","urls":["http://..."]}
 */
data class Channel(
    val name: String,
    val group: String,
    val logo: String,
    val urls: List<String>
)

/** startv.json 顶层结构 */
data class SourceData(
    val updateTime: String,
    val channels: List<Channel>,
    /** true 表示当前展示的是 APK 内置的离线兜底源 */
    val isBuiltin: Boolean = false
)

/** startv.json 解析器 (org.json, 系统自带, 无第三方依赖) */
object TvJsonParser {

    /** 当前支持的格式版本 */
    private const val SUPPORTED_VERSION = 1

    fun parse(json: String): SourceData {
        val root = JSONObject(json)
        val updateTime = root.optString("update_time", "")
        val version = root.optInt("version", SUPPORTED_VERSION)
        // 预留: 未来 version > 1 时在此按新结构做兼容解析
        val channels = mutableListOf<Channel>()
        val arr: JSONArray = root.optJSONArray("channels") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val group = o.optString("group", "其他").trim().ifEmpty { "其他" }
            val logo = o.optString("logo", "").trim()
            val urls = mutableListOf<String>()
            val u = o.optJSONArray("urls")
            if (u != null) {
                for (j in 0 until u.length()) {
                    val s = u.optString(j).trim()
                    if (s.isNotEmpty() && !urls.contains(s)) urls.add(s)
                }
            }
            channels.add(Channel(name, group, logo, urls))
        }
        return SourceData(updateTime, channels)
    }
}
