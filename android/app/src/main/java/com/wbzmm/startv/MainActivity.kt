package com.wbzmm.startv

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

/**
 * 主界面: 下载/读取 startv.json -> 按 央视/卫视 分组展示频道网格。
 * 遥控器: 方向键移动焦点, OK 键进入播放。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var progress: View
    private lateinit var tvStatus: android.widget.TextView
    private lateinit var tvHeader: android.widget.TextView
    private var rows: List<ChannelAdapter.Row> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemBars()

        rv = findViewById(R.id.rv_channels)
        progress = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tv_status)
        tvHeader = findViewById(R.id.tv_header)

        rv.layoutManager = GridLayoutManager(this, 4).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    // 分组头占整行
                    if (position < rows.size && rows[position] is ChannelAdapter.Row.Header) 4 else 1
            }
        }

        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        tvStatus.visibility = View.GONE
        rv.visibility = View.GONE

        lifecycleScope.launch {
            val data = try {
                SourceRepository.load(this@MainActivity)
            } catch (_: Exception) {
                null
            }

            progress.visibility = View.GONE

            if (data == null || data.channels.isEmpty()) {
                tvStatus.text = getString(R.string.load_failed)
                tvStatus.visibility = View.VISIBLE
                return@launch
            }

            ChannelStore.channels = data.channels
            tvHeader.text = buildString {
                append("StarTV")
                append("    源更新: ${data.updateTime.ifEmpty { "未知" }}")
                if (data.isBuiltin) append(" (内置源)")
                append("    共 ${data.channels.size} 个频道")
            }

            val rows = buildRows(data.channels)
            this@MainActivity.rows = rows
            rv.adapter = ChannelAdapter(rows) { channel ->
                val index = ChannelStore.channels.indexOfFirst { it.name == channel.name }
                startActivity(
                    android.content.Intent(this@MainActivity, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_INDEX, if (index >= 0) index else 0)
                )
            }
            rv.visibility = View.VISIBLE
        }
    }

    private fun buildRows(channels: List<Channel>): List<ChannelAdapter.Row> {
        val rows = mutableListOf<ChannelAdapter.Row>()
        // 保持 json 中的分组顺序, 通常为 央视 -> 卫视
        val groups = linkedMapOf<String, MutableList<Channel>>()
        for (c in channels) groups.getOrPut(c.group) { mutableListOf() }.add(c)
        for ((group, list) in groups) {
            rows.add(ChannelAdapter.Row.Header(group, list.size))
            for (c in list) rows.add(ChannelAdapter.Row.Chan(c))
        }
        return rows
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
