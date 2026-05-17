package org.dvbviewer.controller.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log
import android.util.Rational
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dvbviewer.controller.R
import org.dvbviewer.controller.data.DbHelper
import org.dvbviewer.controller.data.xmltv.XmltvChannelMapper
import org.dvbviewer.controller.ui.fragments.ChannelList
import org.dvbviewer.controller.data.entities.DVBViewerPreferences
import org.dvbviewer.controller.databinding.ActivityPlayerBinding
import org.dvbviewer.controller.utils.ServerConsts
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class PlayerActivity : AppCompatActivity() {

    // ── Constants ──────────────────────────────────────────────────────────

    companion object {
        const val EXTRA_URL       = "extra_url"
        const val EXTRA_MIME_TYPE = "extra_mime_type"
        const val EXTRA_TITLE     = "extra_title"
        const val EXTRA_EPG_TITLE = "extra_epg_title"

        private const val TIMEOUT_MS           = 30_000L
        private const val SWIPE_HIDE_DELAY_MS  = 1_500L
        private const val CONTROLS_HIDE_DELAY  = 3_000L
        private const val TAG                  = "PlayerActivity"

        private const val PREF_PLAYER          = "player_prefs"
        private const val KEY_LAST_SPU_TRACK   = "KEY_LAST_SPU_TRACK"

        private const val ACTION_PIP_CONTROL   = "org.dvbviewer.controller.PIP_CONTROL"
        private const val EXTRA_PIP_ACTION     = "pip_action"
        private const val PIP_ACTION_PLAY      = 1
        private const val PIP_REQUEST_CODE     = 101
    }

    // ── Fields ─────────────────────────────────────────────────────────────

    private lateinit var binding: ActivityPlayerBinding
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var gestureDetector: GestureDetectorCompat

    private var currentUrl      = ""
    private var currentMimeType = ""
    private var currentTitle    = ""
    private var currentEpgTitle = ""

    private var streamStarted     = false
    private var subtitleApplied   = false
    private var currentBrightness = -1f   // −1 = follow system
    private var currentSpuScale   = 1.0f  // 1.0 = 100 %; read from prefs at init

    private var timeoutJob: Job? = null
    private var epgRefreshJob: Job? = null
    private val swipeHandler    = Handler(Looper.getMainLooper())
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val subtitleHandler = Handler(Looper.getMainLooper())

    private var pipOnHome    = true
    private var pipReceiver: BroadcastReceiver? = null
    private val isReleased   = AtomicBoolean(false)
    // Not tied to activity lifecycle — survives past super.finish() for background cleanup
    private val playerScope  = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupFullscreen()

        currentUrl      = intent.getStringExtra(EXTRA_URL)       ?: ""
        currentMimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: ""
        currentTitle    = intent.getStringExtra(EXTRA_TITLE)     ?: ""
        currentEpgTitle = intent.getStringExtra(EXTRA_EPG_TITLE) ?: ""

        val prefs  = DVBViewerPreferences(this)
        pipOnHome  = prefs.getBoolean(DVBViewerPreferences.KEY_PLAYER_PIP_ON_HOME, true)

        // Read subtitle scale; values <= 49 are legacy sp-based (pre-LibVLC) → treat as 100 %
        val rawScale = prefs.getInt(DVBViewerPreferences.KEY_PLAYER_SUBTITLE_SIZE, 100)
        currentSpuScale = (if (rawScale < 50) 100 else rawScale.coerceIn(50, 200)) / 100f

        binding.tvTitle.text = currentTitle
        if (currentEpgTitle.isNotBlank()) {
            binding.tvEpgTitle.text       = currentEpgTitle
            binding.tvEpgTitle.visibility = View.VISIBLE
        }

        setupButtons()
        setupGestures()
        showControls()   // visible initially; auto-hides after CONTROLS_HIDE_DELAY

        if (currentUrl.isBlank()) {
            showError(getString(R.string.player_error_generic))
        } else {
            initializePlayer()
        }
    }

    override fun onStart() {
        super.onStart()
        if (mediaPlayer == null && currentUrl.isNotBlank()) initializePlayer()
    }

    override fun onResume() {
        super.onResume()
        setupFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupFullscreen()
    }

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode) mediaPlayer?.pause()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        controlsHandler.removeCallbacksAndMessages(null)
        swipeHandler.removeCallbacksAndMessages(null)
        releasePlayer()
        pipReceiver?.let { runCatching { unregisterReceiver(it) } }
    }

    override fun finish() {
        if (isReleased.compareAndSet(false, true)) {
            // Capture player refs before nulling — sequence is on main thread, so no race
            val mp  = mediaPlayer
            val vlc = libVLC
            mediaPlayer = null
            libVLC      = null

            // Cancel lightweight jobs synchronously (these are cheap)
            timeoutJob?.cancel()
            epgRefreshJob?.cancel()
            subtitleHandler.removeCallbacksAndMessages(null)
            controlsHandler.removeCallbacksAndMessages(null)
            swipeHandler.removeCallbacksAndMessages(null)

            // Heavy stop+release on IO thread — UI closes immediately below
            playerScope.launch {
                runCatching {
                    mp?.stop()
                    mp?.setEventListener(null)
                    mp?.detachViews()
                    mp?.release()
                    vlc?.release()
                }
                Log.d(TAG, "finish: player released on background thread")
            }
        }
        super.finish()   // closes UI immediately, does not wait for playerScope
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipOnHome) enterPip() else mediaPlayer?.pause()
    }

    // ── Player initialisation ──────────────────────────────────────────────

    private fun initializePlayer() {
        isReleased.set(false)
        streamStarted   = false
        subtitleApplied = false
        showLoading()

        val prefs = DVBViewerPreferences(this)

        // ── Build URL with decoded credentials embedded ────────────────────
        // LibVLC has a known issue with :http-extra-headers for authentication;
        // credentials passed directly in the URL work reliably.
        // StreamUtils may embed credentials URL-encoded (e.g. %21 → !).
        // Android's Uri.userInfo already returns the decoded value, so we can
        // reconstruct scheme://user:pass@host:port/path with the real password.
        val rawUri = android.net.Uri.parse(currentUrl)

        val authUser: String
        val authPassword: String
        if (rawUri.userInfo != null) {
            // Credentials embedded in URL — already decoded by Android's Uri parser
            authUser     = rawUri.userInfo!!.substringBefore(':')
            authPassword = rawUri.userInfo!!.substringAfter(':', "")
        } else {
            // No credentials in URL — use ServerConsts / preferences
            authUser     = ServerConsts.REC_SERVICE_USER_NAME.ifBlank {
                prefs.getString(DVBViewerPreferences.KEY_RS_USERNAME)
            }
            authPassword = ServerConsts.REC_SERVICE_PASSWORD.ifBlank {
                prefs.getString(DVBViewerPreferences.KEY_RS_PASSWORD)
            }
        }

        // Reconstruct URL: scheme://user:pass@host:port/encodedPath[?encodedQuery]
        val host     = rawUri.host ?: ""
        val portPart = if (rawUri.port > 0) ":${rawUri.port}" else ""
        val authPart = if (authUser.isNotBlank()) "$authUser:$authPassword@" else ""
        val queryPart = rawUri.encodedQuery?.let { "?$it" } ?: ""
        val cleanUrl  = "${rawUri.scheme}://$authPart$host$portPart${rawUri.encodedPath}$queryPart"

        Log.d(TAG, "initializePlayer: url=${cleanUrl.replace(authPassword, "***")}  user=\"$authUser\"")

        val spuScalePct = (currentSpuScale * 100).toInt()
        libVLC = LibVLC(this, arrayListOf(
            "--network-caching=3000",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--file-caching=1500",
            "--live-caching=3000",
            "--no-audio-time-stretch",
            "--sub-text-scale=$spuScalePct",   // subtitle text size (100 = default)
            "--verbose=0"
        ))

        mediaPlayer = MediaPlayer(libVLC).also { mp ->
            mp.attachViews(binding.vlcLayout, null, false, false)

            val media = Media(libVLC, android.net.Uri.parse(cleanUrl))
            mp.media = media
            media.release()

            mp.setEventListener(eventListener)
            mp.play()
        }

        startBufferingTimeout()
    }

    private fun releasePlayer() {
        if (!isReleased.compareAndSet(false, true)) return   // already released — skip
        timeoutJob?.cancel()
        epgRefreshJob?.cancel()
        epgRefreshJob = null
        subtitleHandler.removeCallbacksAndMessages(null)
        mediaPlayer?.stop()
        mediaPlayer?.setEventListener(null)
        mediaPlayer?.detachViews()
        mediaPlayer?.release()
        mediaPlayer = null
        libVLC?.release()
        libVLC = null
        Log.d(TAG, "releasePlayer: stream stopped and resources freed")
    }

    // ── VLC event listener (called on background thread) ──────────────────

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> runOnUiThread {
                if (!streamStarted) {
                    streamStarted = true
                    cancelBufferingTimeout()
                    hideLoading()
                    updatePipParams()
                    startEpgRefresh()
                }
                updatePlayPauseButton()
                // Fallback subtitle apply: wait 600 ms after first play so all ES are registered
                if (!subtitleApplied) {
                    subtitleHandler.removeCallbacksAndMessages(null)
                    subtitleHandler.postDelayed({
                        mediaPlayer?.let { applySubtitlePreference(it) }
                    }, 600)
                }
            }

            MediaPlayer.Event.Paused -> runOnUiThread {
                updatePlayPauseButton()
            }

            MediaPlayer.Event.Stopped -> runOnUiThread {
                updatePlayPauseButton()
            }

            MediaPlayer.Event.Buffering -> {
                Log.v(TAG, "Buffering: ${event.buffering.toInt()}%")
            }

            MediaPlayer.Event.EncounteredError -> runOnUiThread {
                Log.e(TAG, "VLC EncounteredError")
                cancelBufferingTimeout()
                hideLoading()
                showError(getString(R.string.player_error_generic))
            }

            MediaPlayer.Event.ESAdded -> {
                Log.d(TAG, "ESAdded type=${event.esChangedType}")
                // Debounce: each new ES resets the 500 ms timer; fires once all streams added
                subtitleHandler.removeCallbacksAndMessages(null)
                subtitleHandler.postDelayed({
                    if (!subtitleApplied) mediaPlayer?.let { applySubtitlePreference(it) }
                }, 500)
            }
        }
    }

    private fun updatePlayPauseButton() {
        val isPlaying = mediaPlayer?.isPlaying == true
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp
        )
    }

    // ── Buffering / HDD-sleep timeout ──────────────────────────────────────

    private fun startBufferingTimeout() {
        timeoutJob?.cancel()
        timeoutJob = lifecycleScope.launch {
            delay(TIMEOUT_MS)
            if (!streamStarted) {
                hideLoading()
                showError(getString(R.string.player_timeout))
            }
        }
    }

    private fun cancelBufferingTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    // ── EPG real-time refresh (every 60 s) ────────────────────────────────

    private fun startEpgRefresh() {
        epgRefreshJob?.cancel()
        epgRefreshJob = lifecycleScope.launch {
            while (true) {
                delay(60_000)
                if (currentTitle.isBlank()) continue
                refreshEpgOverlay()
            }
        }
    }

    private suspend fun refreshEpgOverlay() {
        val channelName = currentTitle
        withContext(Dispatchers.IO) {
            try {
                val dbHelper = DbHelper(applicationContext)
                val xmltvNow = dbHelper.getXmltvNowPlaying(System.currentTimeMillis())
                if (xmltvNow.isEmpty()) return@withContext
                val mapper   = XmltvChannelMapper(applicationContext)
                val resolved = mapper.resolve(channelName, xmltvNow.keys.toList()) ?: return@withContext
                val entry    = xmltvNow[resolved.xmltvName]              ?: return@withContext
                val newTitle = ChannelList.xmltvDisplayTitle(entry)      ?: return@withContext
                withContext(Dispatchers.Main) {
                    if (newTitle != currentEpgTitle) {
                        currentEpgTitle = newTitle
                        binding.tvEpgTitle.text       = newTitle
                        binding.tvEpgTitle.visibility = View.VISIBLE
                        Log.d(TAG, "EPG updated: \"$newTitle\"")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "EPG refresh error: ${e.message}")
            }
        }
    }

    // ── Overlay helpers ────────────────────────────────────────────────────

    private fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.errorOverlay.visibility   = View.GONE
        binding.tvLoadingMsg.text         = getString(R.string.player_connecting)
    }

    private fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.errorOverlay.visibility   = View.VISIBLE
        binding.loadingOverlay.visibility = View.GONE
        binding.tvErrorMsg.text           = message
    }

    // ── Controls auto-hide ─────────────────────────────────────────────────

    private fun showControls() {
        if (!isInPictureInPictureMode) binding.topBar.visibility = View.VISIBLE
        controlsHandler.removeCallbacksAndMessages(null)
        controlsHandler.postDelayed({ hideControls() }, CONTROLS_HIDE_DELAY)
    }

    private fun hideControls() {
        binding.topBar.visibility = View.GONE
    }

    private fun toggleControls() {
        if (binding.topBar.visibility == View.VISIBLE) hideControls() else showControls()
    }

    // ── Button wiring ──────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener    { finish() }
        binding.btnRetry.setOnClickListener     { retry() }
        binding.btnSubtitles.setOnClickListener    { showSubtitleDialog() }
        binding.btnSubtitleSize.setOnClickListener { showSubtitleSizeDialog() }
        binding.btnPip.setOnClickListener       { enterPip() }
        binding.btnClose.setOnClickListener     { finish() }
        binding.btnPlayPause.setOnClickListener {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause() else mediaPlayer?.play()
            showControls()
        }
    }

    private fun retry() {
        binding.errorOverlay.visibility = View.GONE
        releasePlayer()
        initializePlayer()
    }

    // ── Subtitle track selection ───────────────────────────────────────────

    private fun applySubtitlePreference(mp: MediaPlayer) {
        val tracks = mp.spuTracks ?: return
        if (tracks.isEmpty()) return
        subtitleApplied = true

        val savedIndex = getSharedPreferences(PREF_PLAYER, MODE_PRIVATE)
            .getInt(KEY_LAST_SPU_TRACK, -1)

        if (savedIndex in tracks.indices) {
            mp.spuTrack = tracks[savedIndex].id
            Log.d(TAG, "Restored SPU index=$savedIndex id=${tracks[savedIndex].id}")
        } else {
            // No saved preference → auto-select first real subtitle track (id >= 0)
            val firstReal = tracks.indexOfFirst { it.id >= 0 }
            if (firstReal >= 0) {
                mp.spuTrack = tracks[firstReal].id
                Log.d(TAG, "Auto-selected SPU index=$firstReal id=${tracks[firstReal].id}")
            }
        }
    }

    private fun showSubtitleDialog() {
        val tracks = mediaPlayer?.spuTracks
        if (tracks.isNullOrEmpty()) return

        val labels = tracks.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.player_subtitles)
            .setItems(labels) { _, which ->
                mediaPlayer?.spuTrack = tracks[which].id
                getSharedPreferences(PREF_PLAYER, MODE_PRIVATE)
                    .edit().putInt(KEY_LAST_SPU_TRACK, which).apply()
            }
            .show()
    }

    private fun showSubtitleSizeDialog() {
        val currentPct = (currentSpuScale * 100).toInt()
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val dp8  = (8  * resources.displayMetrics.density).toInt()

        val tvValue = TextView(this).apply {
            text       = "$currentPct%"
            gravity    = Gravity.CENTER
            textSize   = 16f
            setPadding(0, dp8, 0, 0)
        }
        val seekBar = SeekBar(this).apply {
            max      = 150                  // steps from 50 to 200
            progress = currentPct - 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    tvValue.text = "${p + 50}%"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16 * 2, dp16, dp16 * 2, dp8)
            addView(seekBar)
            addView(tvValue)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.player_subtitle_size_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val pct   = seekBar.progress + 50
                val scale = pct / 100f
                if (scale != currentSpuScale) {
                    currentSpuScale = scale
                    DVBViewerPreferences(this)
                        .prefs.edit()
                        .putInt(DVBViewerPreferences.KEY_PLAYER_SUBTITLE_SIZE, pct)
                        .apply()
                    Log.d(TAG, "SPU text scale → $pct%; restarting stream")
                    // --sub-text-scale is a LibVLC init option → must recreate player
                    binding.errorOverlay.visibility = View.GONE
                    releasePlayer()
                    initializePlayer()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Swipe gestures — brightness (left half) / volume (right half) ─────

    private fun setupGestures() {
        gestureDetector = GestureDetectorCompat(this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    toggleControls()
                    return true
                }

                override fun onScroll(
                    e1: MotionEvent?, e2: MotionEvent,
                    distanceX: Float, distanceY: Float
                ): Boolean {
                    val startX = e1?.x ?: return false
                    val screenW = binding.root.width.toFloat()
                    val screenH = binding.root.height.toFloat()
                    val delta = distanceY / screenH    // positive = swipe up = increase

                    if (startX < screenW / 2) {
                        adjustBrightness(delta * 0.8f)
                        showSwipeOverlay(isVolume = false)
                    } else {
                        adjustVolume(delta * 0.8f)
                        showSwipeOverlay(isVolume = true)
                    }
                    return true
                }
            })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun adjustBrightness(delta: Float) {
        val current = if (currentBrightness < 0f)
            android.provider.Settings.System.getFloat(
                contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128f
            ) / 255f
        else currentBrightness
        currentBrightness = (current + delta).coerceIn(0.01f, 1.0f)
        val lp = window.attributes
        lp.screenBrightness = currentBrightness
        window.attributes   = lp
    }

    private fun adjustVolume(delta: Float) {
        val mp  = mediaPlayer ?: return
        val cur = mp.volume                                  // 0–200 in LibVLC
        val newVol = (cur + delta * 100).toInt().coerceIn(0, 200)
        mp.volume = newVol
    }

    private fun showSwipeOverlay(isVolume: Boolean) {
        if (isVolume) {
            val vlcVol = mediaPlayer?.volume ?: 100   // 0–200; 100 = normal
            val pct    = vlcVol / 2                   // display as 0–100 %
            binding.pbSwipe.progress  = pct
            binding.tvSwipeValue.text = "$pct%"
        } else {
            val brightness = if (currentBrightness < 0f)
                android.provider.Settings.System.getFloat(
                    contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128f
                ) / 255f
            else currentBrightness
            val pct = (brightness * 100).toInt()
            binding.pbSwipe.progress  = pct
            binding.tvSwipeValue.text = "$pct%"
        }
        binding.ivSwipeIcon.setImageResource(
            if (isVolume) R.drawable.ic_volume_up_24dp else R.drawable.ic_brightness_24dp
        )
        binding.swipeOverlay.visibility = View.VISIBLE
        swipeHandler.removeCallbacksAndMessages(null)
        swipeHandler.postDelayed({ binding.swipeOverlay.visibility = View.GONE }, SWIPE_HIDE_DELAY_MS)
    }

    // ── Picture-in-Picture ─────────────────────────────────────────────────

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerPipReceiver()
            enterPictureInPictureMode(buildPipParams())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(): PictureInPictureParams {
        val isPlaying = mediaPlayer?.isPlaying == true
        val icon = Icon.createWithResource(
            this, if (isPlaying) android.R.drawable.ic_media_pause
                  else           android.R.drawable.ic_media_play
        )
        val label = if (isPlaying) "Pauza" else "Redare"
        val action = RemoteAction(
            icon, label, label,
            PendingIntent.getBroadcast(
                this, PIP_REQUEST_CODE,
                Intent(ACTION_PIP_CONTROL).putExtra(EXTRA_PIP_ACTION, PIP_ACTION_PLAY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(action))
            .build()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            setPictureInPictureParams(buildPipParams())
        }
    }

    private fun registerPipReceiver() {
        if (pipReceiver != null) return
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_PIP_CONTROL) {
                    if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
                    else mediaPlayer?.play()
                    updatePipParams()
                }
            }
        }
        registerReceiver(pipReceiver, IntentFilter(ACTION_PIP_CONTROL), RECEIVER_NOT_EXPORTED)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        binding.topBar.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        if (!isInPictureInPictureMode) {
            runCatching { pipReceiver?.let { unregisterReceiver(it) } }
            pipReceiver = null
            setupFullscreen()
            showControls()
        }
    }

    // ── Immersive fullscreen ───────────────────────────────────────────────

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
