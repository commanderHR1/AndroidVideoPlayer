package com.commanderhr1.videoplayer

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.commanderhr1.videoplayer.databinding.ActivityMainBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util

// Logging
private const val LOG_TAG : String = "VideoPlayerApp"

class MainActivity : AppCompatActivity() {
    // ExoPlayer
    private var player: ExoPlayer? = null
    private var playWhenReady = true
    private var currentWindow = 0
    private var playbackPosition = 0L
    private val playbackStateListener: Player.Listener = playbackStateListener()
    private var analyticsListener : AnalyticsListener = analyticsListener()

    // AnalyticsListener
    private var playTime = 0L // in ms
    private var pauseTime = 0L // in ms
    private var totalTime = 0L // in ms
    private var pressedPaused = 0

    // onCreate
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
    }

    // Binds PlayerView
    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }

    // Initializes ExoPlayer
    private fun initializePlayer() {
        val uri = Uri.parse(getString(R.string.media_url_mp3))
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters())
        }
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer
                exoPlayer.addMediaSource(buildMediaSource(uri))
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(currentWindow, playbackPosition)
                exoPlayer.addListener(playbackStateListener)
                exoPlayer.addAnalyticsListener(analyticsListener)
                exoPlayer.prepare()
            }
    }

    // onStart
    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24) {
            initializePlayer()
        }
    }

    // Hides the system UI
    @SuppressLint("InlinedApi")
    private fun hideSystemUi() {
        viewBinding.videoView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    // onResume
    public override fun onResume() {
        super.onResume()
        hideSystemUi()
        if ((Util.SDK_INT < 24 || player == null)) {
            initializePlayer()
        }
    }

    // onPause
    public override fun onPause() {
        super.onPause()
        if (Util.SDK_INT < 24) {
            releasePlayer()
        }
    }

    // Releases ExoPlayer
    private fun releasePlayer() {
        player?.run {
            playbackPosition = this.currentPosition
            currentWindow = this.currentWindowIndex
            playWhenReady = this.playWhenReady
            removeListener(playbackStateListener)
            release()
        }
        player = null
    }

    // onStop
    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= 24) {
            releasePlayer()
        }
    }

    // Listener for onPlaybackStateChanged
    private fun playbackStateListener() = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateString: String = when (playbackState) {
                ExoPlayer.STATE_IDLE -> "ExoPlayer.STATE_IDLE      -"
                ExoPlayer.STATE_BUFFERING -> "ExoPlayer.STATE_BUFFERING -"
                ExoPlayer.STATE_READY -> "ExoPlayer.STATE_READY     -"
                ExoPlayer.STATE_ENDED -> "ExoPlayer.STATE_ENDED     -"
                else -> "UNKNOWN_STATE             -"
            }
            Log.d(LOG_TAG, "changed state to $stateString")
        }
    }

    // analyticsListener
    private fun analyticsListener() = object : AnalyticsListener {
        private var initTime = 0L

        override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
            if(isPlaying) {
                if(initTime != 0L) pauseTime += System.currentTimeMillis() - initTime
                initTime = System.currentTimeMillis()
            } else {
                if(initTime != 0L) playTime += System.currentTimeMillis() - initTime
                initTime = System.currentTimeMillis()
                pressedPaused++
            }
            totalTime = playTime+pauseTime
            Log.e("onIsPlaying", "PLAYTIME: $playTime")
            Log.e("onIsPlaying", "PRESSEDPAUSE: $pressedPaused")
            Log.e("onIsPlaying", "PAUSETIME: $pauseTime")
            Log.e("onIsPlaying", "TOTALTIME: $totalTime")
            super.onIsPlayingChanged(eventTime, isPlaying)
        }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long
        ) {
            super.onAudioUnderrun(eventTime, bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)
            Log.e("onAudioUnderrun", "Audio Underrun at $eventTime")
        }
    }

    private fun buildMediaSource(uri: Uri) : ProgressiveMediaSource {
        val mediaItem : MediaItem = MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_MPD).build()
        return ProgressiveMediaSource.Factory(
            DefaultDataSourceFactory(applicationContext, LOG_TAG)
        ).createMediaSource(mediaItem)
    }
}
