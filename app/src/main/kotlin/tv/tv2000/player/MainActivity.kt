package tv.tv2000.player

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    companion object {
        private const val STREAM_URL =
            "https://hls-live-tv2000.akamaized.net/hls/live/2028510/tv2000/master.m3u8"
        private const val RELOAD_INTERVAL_MS  = 30 * 60 * 1000L // 30 min auto-reload
        private const val OVERLAY_HIDE_MS     = 5_000L
        private const val RETRY_BASE_DELAY_MS = 3_000L
        private const val MAX_RETRIES         = 5
    }

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var playerView: PlayerView
    private lateinit var overlay: View
    private lateinit var statusView: View
    private lateinit var statusText: TextView
    private lateinit var reloadBtn: Button
    private lateinit var qualityBtn: Button
    private lateinit var countdownBar: ProgressBar
    private lateinit var countdownLabel: TextView

    // ── Player ────────────────────────────────────────────────────────────────
    private lateinit var player: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector

    // ── State ─────────────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private var overlayRunnable: Runnable? = null
    private var countdown: CountDownTimer? = null
    private var retryCount = 0
    private var isOverlayVisible = false
    private var qualityLevel = 0   // 0=Auto  1=SD(480p)  2=HD(720p)

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen, screen-on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        setContentView(R.layout.activity_main)

        playerView    = findViewById(R.id.player_view)
        overlay       = findViewById(R.id.overlay)
        statusView    = findViewById(R.id.status_view)
        statusText    = findViewById(R.id.status_text)
        reloadBtn     = findViewById(R.id.reload_btn)
        qualityBtn    = findViewById(R.id.quality_btn)
        countdownBar  = findViewById(R.id.countdown_bar)
        countdownLabel= findViewById(R.id.countdown_label)

        setupPlayer()
        setupButtons()
        startStream()
        showOverlay()
    }

    override fun onStart()   { super.onStart();   player.play() }
    override fun onStop()    { super.onStop();    player.pause() }
    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
        overlayRunnable?.let { handler.removeCallbacks(it) }
        player.release()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ── Player setup ──────────────────────────────────────────────────────────
    private fun setupPlayer() {
        trackSelector = DefaultTrackSelector(this)

        // Low-RAM buffer: 20 s max instead of ExoPlayer's default ~50 s
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                12_000,   // minBufferMs
                25_000,   // maxBufferMs
                1_500,    // bufferForPlaybackMs
                2_500     // bufferForPlaybackAfterRebufferMs
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()

        playerView.player = player
        playerView.useController = false

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) = when (state) {
                Player.STATE_BUFFERING -> showStatus(getString(R.string.status_buffering))
                Player.STATE_READY     -> { hideStatus(); retryCount = 0 }
                Player.STATE_ENDED     -> startStream()   // live stream dropped — restart
                else                   -> Unit
            }
            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
    }

    // ── Stream ────────────────────────────────────────────────────────────────
    private fun startStream() {
        showStatus(getString(R.string.status_connecting))

        val dataSource = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true) // follow Akamai → Meride redirect

        val source = HlsMediaSource.Factory(dataSource)
            .createMediaSource(MediaItem.fromUri(STREAM_URL))

        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true

        startCountdown()
    }

    fun hardReload() {
        retryCount = 0
        countdown?.cancel()
        player.stop()
        player.clearMediaItems()
        handler.postDelayed(::startStream, 400)
    }

    private fun handleError(error: PlaybackException) {
        if (retryCount < MAX_RETRIES) {
            retryCount++
            showStatus(getString(R.string.status_retry, retryCount, MAX_RETRIES))
            val delay = RETRY_BASE_DELAY_MS * retryCount
            handler.postDelayed({ player.prepare() }, delay)
        } else {
            showStatus(getString(R.string.status_failed))
        }
    }

    // ── Quality cycling ───────────────────────────────────────────────────────
    private fun cycleQuality() {
        qualityLevel = (qualityLevel + 1) % 3
        val params = trackSelector.buildUponParameters()
        when (qualityLevel) {
            0 -> { params.clearVideoSizeConstraints();         qualityBtn.text = "Auto" }
            1 -> { params.setMaxVideoSize(854, 480);           qualityBtn.text = "SD 480p" }
            2 -> { params.setMaxVideoSize(1280, 720);          qualityBtn.text = "HD 720p" }
        }
        trackSelector.setParameters(params)
    }

    // ── Overlay ───────────────────────────────────────────────────────────────
    fun showOverlay() {
        overlay.visibility = View.VISIBLE
        isOverlayVisible   = true
        reloadBtn.requestFocus()                         // always land on Reload first
        overlayRunnable?.let { handler.removeCallbacks(it) }
        overlayRunnable = Runnable { hideOverlay() }.also {
            handler.postDelayed(it, OVERLAY_HIDE_MS)
        }
    }

    private fun hideOverlay() {
        overlay.visibility = View.GONE
        isOverlayVisible   = false
    }

    // ── Status ────────────────────────────────────────────────────────────────
    private fun showStatus(msg: String) {
        statusView.visibility = View.VISIBLE
        statusText.text       = msg
    }

    private fun hideStatus() {
        statusView.visibility = View.GONE
    }

    // ── Countdown ─────────────────────────────────────────────────────────────
    private fun startCountdown() {
        countdown?.cancel()
        countdown = object : CountDownTimer(RELOAD_INTERVAL_MS, 1_000) {
            override fun onTick(left: Long) {
                val pct  = (left.toFloat() / RELOAD_INTERVAL_MS * 100).toInt()
                val mins = left / 60_000
                val secs = (left % 60_000) / 1_000
                countdownBar.progress   = pct
                countdownLabel.text     = "%d:%02d".format(mins, secs)
            }
            override fun onFinish() = hardReload()
        }.start()
    }

    // ── Buttons ───────────────────────────────────────────────────────────────
    private fun setupButtons() {
        reloadBtn.setOnClickListener  { hardReload() }
        qualityBtn.setOnClickListener { cycleQuality() }
    }

    // ── D-pad / remote key handling ───────────────────────────────────────────
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {

            // Any D-pad movement wakes the overlay
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!isOverlayVisible) showOverlay()
                true
            }

            // Left/Right navigate between buttons when overlay is open
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isOverlayVisible) { showOverlay(); true }
                else false  // let Android handle D-pad focus traversal
            }

            // Center / Enter: show overlay if hidden, else let focused view handle
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!isOverlayVisible) { showOverlay(); true }
                else false
            }

            // Back: hide overlay or exit
            KeyEvent.KEYCODE_BACK -> {
                if (isOverlayVisible) { hideOverlay(); true }
                else super.onKeyDown(keyCode, event)
            }

            // Media keys
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play(); true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY  -> { player.play();  true }
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); true }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    // ── System UI ─────────────────────────────────────────────────────────────
    private fun hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }
}
