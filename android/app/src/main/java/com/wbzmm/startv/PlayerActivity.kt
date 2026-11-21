package com.wbzmm.startv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 播放界面:
 * - 按 urls 顺序播放, 第一条失败(报错/超时/结束)自动切换下一条
 * - 全部线路失败时明确告知用户
 * - 遥控器: 上/下 换台, OK 键呼出左侧频道列表, 返回键退出
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
        /** 单条线路起播超时(毫秒) */
        private const val START_TIMEOUT_MS = 15_000L
    }

    private var player: ExoPlayer? = null
    private var channelIndex = 0
    private var urlIndex = 0

    private lateinit var playerView: PlayerView
    private lateinit var progress: View
    private lateinit var tvError: TextView
    private lateinit var tvChannelName: TextView
    private lateinit var tvLine: TextView
    private lateinit var overlay: View
    private lateinit var rvOverlay: RecyclerView
    private lateinit var overlayAdapter: OverlayAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        if (player?.isPlaying != true && player?.playbackState != Player.STATE_READY) {
            nextUrl()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    handler.removeCallbacks(timeoutRunnable)
                    progress.visibility = View.GONE
                    tvError.visibility = View.GONE
                }
                // 直播流意外结束也视为该线路失败
                Player.STATE_ENDED -> nextUrl()
                else -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            nextUrl()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        hideSystemBars()

        playerView = findViewById(R.id.player_view)
        progress = findViewById(R.id.progress)
        tvError = findViewById(R.id.tv_error)
        tvChannelName = findViewById(R.id.tv_channel_name)
        tvLine = findViewById(R.id.tv_line)
        overlay = findViewById(R.id.overlay)
        rvOverlay = findViewById(R.id.rv_overlay)

        channelIndex = intent.getIntExtra(EXTRA_INDEX, 0)
            .coerceIn(0, (ChannelStore.channels.size - 1).coerceAtLeast(0))

        overlayAdapter = OverlayAdapter(ChannelStore.channels) { position ->
            hideOverlay()
            playChannel(position)
        }
        rvOverlay.layoutManager = LinearLayoutManager(this)
        rvOverlay.adapter = overlayAdapter

        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.addListener(playerListener)
        }

        playChannel(channelIndex)
    }

    // ------------------------------------------------------------------ 播放

    private fun playChannel(index: Int) {
        channelIndex = index
        urlIndex = 0
        val channel = ChannelStore.channels[channelIndex]
        tvChannelName.text = channel.name
        overlayAdapter.setCurrentIndex(channelIndex)
        tvError.visibility = View.GONE
        tryPlayUrl()
    }

    private fun tryPlayUrl() {
        val channel = ChannelStore.channels.getOrNull(channelIndex) ?: return

        if (urlIndex >= channel.urls.size) {
            // 所有线路都失败了 -> 明确告知用户
            showAllFailed(channel.urls.isEmpty())
            return
        }

        progress.visibility = View.VISIBLE
        tvError.visibility = View.GONE
        tvLine.text = "线路 ${urlIndex + 1}/${channel.urls.size}"

        val mediaItem = MediaItem.Builder()
            .setUri(channel.urls[urlIndex])
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true

        // 起播超时保护: 15 秒内没播出来就切换下一条线路
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, START_TIMEOUT_MS)
    }

    private fun nextUrl() {
        handler.removeCallbacks(timeoutRunnable)
        urlIndex++
        tryPlayUrl()
    }

    private fun showAllFailed(noUrls: Boolean) {
        progress.visibility = View.GONE
        tvLine.text = "线路 0/0"
        tvError.text = getString(if (noUrls) R.string.no_urls else R.string.all_failed)
        tvError.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------ 浮层

    private fun showOverlay() {
        overlay.visibility = View.VISIBLE
        overlayAdapter.setCurrentIndex(channelIndex)
        rvOverlay.post {
            val lm = rvOverlay.layoutManager as? LinearLayoutManager ?: return@post
            lm.scrollToPositionWithOffset(channelIndex, rvOverlay.height / 2)
            rvOverlay.findViewHolderForAdapterPosition(channelIndex)?.itemView?.requestFocus()
        }
    }

    private fun hideOverlay() {
        overlay.visibility = View.GONE
    }

    private fun isOverlayVisible(): Boolean = overlay.visibility == View.VISIBLE

    // ------------------------------------------------------------------ 遥控器

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isOverlayVisible()) {
            when (keyCode) {
                // 浮层打开时, 交给列表处理方向键; OK/返回 关闭浮层
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> {
                    hideOverlay()
                    playerView.requestFocus()
                    return true
                }
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                switchChannel(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                switchChannel(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                showOverlay()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun switchChannel(delta: Int) {
        val size = ChannelStore.channels.size
        if (size == 0) return
        val next = ((channelIndex + delta) % size + size) % size
        playChannel(next)
    }

    // ------------------------------------------------------------------

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(timeoutRunnable)
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeoutRunnable)
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }
}
