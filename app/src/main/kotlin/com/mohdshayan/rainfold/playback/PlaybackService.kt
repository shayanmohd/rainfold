package com.mohdshayan.rainfold.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mohdshayan.rainfold.MainActivity
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.audio.AudioOut
import com.mohdshayan.rainfold.core.mix.FreeTier
import com.mohdshayan.rainfold.core.mix.Mixer
import com.mohdshayan.rainfold.data.LiveMix
import com.mohdshayan.rainfold.data.PlaybackStatus
import com.mohdshayan.rainfold.di.ServiceLocator
import com.mohdshayan.rainfold.ui.EngineLabels
import com.mohdshayan.rainfold.widget.RainfoldWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The one foreground service, alive only while sound plays (type mediaPlayback). Media3 posts the
 * notification and moves the service in and out of the foreground; this class owns the mixer, the
 * render thread, the wake lock, audio focus, the headphone receiver and the sleep timer.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val mixer = Mixer(seed = System.nanoTime())
    private lateinit var out: AudioOut
    private lateinit var player: NoisePlayer
    private lateinit var focus: FocusController
    private var session: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var duck = 1f
    private var noisyRegistered = false
    private var playStartedAt = 0L
    private var qualifiedThisSession = false
    private var wakeLockAt = 0L

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseByUser()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        running = true
        out = AudioOut(mixer) { main.post { releaseWakeLock() } }
        player = NoisePlayer(Looper.getMainLooper()) { play -> main.post { if (play) startSound() else stopSound() } }
        focus = FocusController(
            this,
            onPause = { transient -> pauseFromFocus(transient) },
            onResume = {
                player.setPlaying(true)
                startSound()
            },
            onDuck = { ducked ->
                duck = if (ducked) 0.3f else 1f
                out.setLevel(level())
            },
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_rainfold) },
        )
        val repo = ServiceLocator.session
        scope.launch {
            repo.load()
            combine(repo.mix.filterNotNull(), ServiceLocator.unlock.state) { live, unlock -> live to unlock.unlocked }
                .collect { (live, entitled) -> applyMix(live, entitled) }
        }
        scope.launch {
            while (true) {
                delay(1_000)
                tick()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlayingNow) stopSelf()
    }

    override fun onDestroy() {
        running = false
        out.stop()
        unregisterNoisy()
        focus.abandonForGood()
        releaseWakeLock()
        session?.release()
        session = null
        player.release()
        scope.cancel()
        ServiceLocator.session.setStatus(PlaybackStatus.STOPPED)
        super.onDestroy()
    }

    private fun applyMix(live: LiveMix, entitled: Boolean) {
        val spec = live.spec
        mixer.update(spec, FreeTier.playableLayers(entitled, spec.layers), FreeTier.evolvingAllowed(entitled))
        player.setTitle(EngineLabels.title(this, live), getString(R.string.app_name))
    }

    private fun level(): Float {
        val plan = ServiceLocator.session.plan.value
        return (plan?.gainAt(System.currentTimeMillis()) ?: 1f) * duck
    }

    private fun startSound() {
        if (out.isRunning) return
        if (!focus.request()) {
            player.setPlaying(false)
            return
        }
        registerNoisy()
        acquireWakeLock()
        out.start(level())
        playStartedAt = System.currentTimeMillis()
        qualifiedThisSession = false
        ServiceLocator.session.setStatus(PlaybackStatus.PLAYING)
        ServiceLocator.appScope.launch { ServiceLocator.appPrefs.setPlayingSince(playStartedAt) }
        RainfoldWidget.refresh(this)
    }

    /** A deliberate stop: the user, the timer, unplugged headphones or another app taking over. */
    private fun stopSound() {
        out.stop()
        unregisterNoisy()
        focus.abandonForGood()
        ServiceLocator.session.setStatus(PlaybackStatus.STOPPED)
        ServiceLocator.appScope.launch { ServiceLocator.appPrefs.setPlayingSince(null) }
        RainfoldWidget.refresh(this)
    }

    private fun pauseByUser() {
        player.setPlaying(false)
        stopSound()
    }

    private fun pauseFromFocus(transient: Boolean) {
        player.setPlaying(false)
        out.stop()
        unregisterNoisy()
        if (!transient) focus.abandonForGood()
        ServiceLocator.session.setStatus(if (transient) PlaybackStatus.PAUSED_FOR_CALL else PlaybackStatus.STOPPED)
        ServiceLocator.appScope.launch { ServiceLocator.appPrefs.setPlayingSince(null) }
        RainfoldWidget.refresh(this)
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val repo = ServiceLocator.session
        val plan = repo.plan.value
        if (plan != null) {
            if (plan.finishedAt(now)) {
                repo.setPlan(null)
                if (player.isPlayingNow) {
                    pauseByUser()
                    qualify()
                }
            } else if (out.isRunning) {
                out.setLevel(level())
            }
        }
        if (out.isRunning && !qualifiedThisSession && now - playStartedAt >= QUALIFYING_MS) qualify()
        // The wake lock carries a timeout as a safety net; renew it hourly so a night without a timer never outlasts it.
        if (out.isRunning && now - wakeLockAt >= WAKE_RENEW_MS) acquireWakeLock()
    }

    private suspend fun qualify() {
        qualifiedThisSession = true
        ServiceLocator.appPrefs.recordQualifyingDay(LocalDate.now().toEpochDay())
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        runCatching { unregisterReceiver(noisy) }
        noisyRegistered = false
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Rainfold:playback")
            .also { it.setReferenceCounted(false); wakeLock = it }
        lock.acquire(WAKE_LOCK_MS)
        wakeLockAt = System.currentTimeMillis()
    }

    private fun releaseWakeLock() {
        if (out.isRunning) return
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        private const val QUALIFYING_MS = 30 * 60_000L
        private const val WAKE_LOCK_MS = 11 * 60 * 60_000L
        private const val WAKE_RENEW_MS = 60 * 60_000L

        /** True while the service exists in this process; Listen uses it to spot a stop it did not ask for. */
        @Volatile
        var running = false
            private set
    }
}
