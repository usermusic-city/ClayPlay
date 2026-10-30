package com.clayplay.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fully synthetic sound: no audio assets ship with the app, everything is
 * generated into a small pool of pre-rendered PCM buffers at startup.
 *
 * Why pre-render instead of synthesise on the fly: AudioTrack wants a whole
 * buffer per write on the cheapest path, and a poke can fire several sounds in
 * the same frame. Pre-rendering ~1s of 22.05kHz mono per toy costs well under
 * 2MB and makes playback latency predictable.
 *
 * Each toy gets a distinct timbre so the room reads as eight different
 * materials rather than one sound with eight pitches:
 *
 *   blob    soft low thump (clay fist on clay)
 *   mushroom hollow woody knock
 *   cat     short chirp
 *   top     spinning whirr + tick
 *   pot     ceramic ring with a long tail
 *   blocks  hard clack
 *   planet  airy, wide shimmer
 *   duck    squeezed squeak
 */
class ClayAudio {

    private val sampleRate = 22050
    private val player: AudioTrack?
    private val samples = HashMap<String, ShortArray>()
    private var enabled = true

    init {
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = if (minBuf > 0) minBuf * 4 else 16384
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                track = null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "AudioTrack unavailable: " + t.message)
            track = null
        }
        player = track

        buildSounds()
    }

    fun setEnabled(v: Boolean) { enabled = v }
    fun isEnabled() = enabled

    // ------------------------------------------------------------ synthesis

    private fun buildSounds() {
        samples["blob"] = thump(0.30f, 150f, 62f, 0.85f)
        samples["mushroom"] = knock(0.24f, 420f, 0.75f)
        samples["cat"] = chirp(0.26f, 620f, 1180f, 0.55f)
        samples["top"] = whirr(0.55f, 300f, 0.6f)
        samples["pot"] = ring(0.75f, 780f, 2.6f, 0.5f)
        samples["blocks"] = clack(0.18f, 0.7f)
        samples["planet"] = shimmer(0.85f, 0.5f)
        samples["duck"] = squeak(0.22f, 900f, 1500f, 0.6f)
    }

    private fun alloc(seconds: Float): FloatArray =
        FloatArray((sampleRate * seconds).toInt())

    private fun toShort(buf: FloatArray): ShortArray {
        // normalise to a safe headroom, then clip
        var peak = 0f
        for (v in buf) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        val g = if (peak > 0.0001f) 0.72f / peak else 1f
        val out = ShortArray(buf.size)
        for (i in buf.indices) {
            var v = buf[i] * g
            if (v > 1f) v = 1f
            if (v < -1f) v = -1f
            out[i] = (v * 32767f).toInt().toShort()
        }
        return out
    }

    /** Exponential decay envelope. */
    private fun env(t: Float, attack: Float, decay: Float): Float {
        val a = if (t < attack) t / attack else 1f
        return a * exp(-t * decay)
    }

    /** Soft low body with a pitch drop: clay hit. */
    private fun thump(dur: Float, f0: Float, f1: Float, bright: Float): ShortArray {
        val buf = alloc(dur)
        var phase = 0f
        for (i in buf.indices) {
            val t = i.toFloat() / sampleRate
            val k = t / dur
            val f = f0 + (f1 - f0) * k
            phase += 2.0 * PI.toFloat() * f / sampleRate
            val e = env(t, 0.004f, 11f)
            // a touch of second harmonic gives the "solid mass" impression
            val v = sin(phase) * e + sin(phase * 2f) * e * 0.22f * bright
            buf[i] = v
        }
        return toShort(buf)
    }

    /** Hollow knock: filtered noise burst plus a short low tone. */
    private fun knock(dur: Float, tone: Float, amp: Float): ShortArray {
        val buf = alloc(dur)
        val rnd = Random(7)
        var phase = 0f
        var lp = 0f
        for (i in buf.indices) {
            val t = i.toFloat() / sampleRate
            phase += 2.0 * PI.toFloat() * tone / sampleRate
            val noise = rnd.nextFloat() * 2f - 1f
            lp += (noise - lp) * 0.25f          // lowpass the noise -> "hollow"
            val e = env(t, 0.002f, 26f)
            buf[i] = (lp * 0.5f + sin(phase) * 0.5f) * e * amp
        }
        return toShort(buf)
    }

    /** Rising chirp with vibrato. */
    private fun chirp(dur: Float, f0: Float, f1: Float, amp: Float): ShortArray {
        val buf = alloc(dur)
        var phase = 0f
        for (i in buf.indices) {
            val t = i.toFloat() / sampleRate
            val k = (t / dur).coerceIn(0f, 1f)
            val vib = 1f + 0.03f * sin(2.0 * PI.toFloat() * 26f * t)
            val f = (f0 + (f1 - f0) * k) * vib
            phase += 2.0 * PI.toFloat() * f / sampleRate
            val e = env(t, 0.008f, 8f)
            buf[i] = sin(phase) * e * amp
        }
        return toShort(buf)
    }

    /** Spinning whirr: amplitude-modulated mid tone sweeping down. */
    private fun whirr(dur: Float, rate: Float, amp: Float): ShortArray {
        val buf = alloc(dur)
        var phase = 0f
        for (i in buf.indices) {
            val t = i.toFloat() / sampleRate
            val k = (t / dur).coerceIn(0f, 1f)
            val f = 420f - 190f * k
            phase += 2.0 * PI.toFloat() * f / sampleRate
            val am = 0.55f + 0.45f * sin(2.0 * PI.toFloat() * rate * t)
            val e = env(t, 0.02f, 4.2f)
            buf[i] = sin(phase) * am * e * amp
        }
        return toShort(buf)
    }

    /** Ceramic ring: two detuned partials with a long decay. */
    private fun ring(dur: Float, f: Float, decay: Float, amp: Float): ShortArray {
        val buf = alloc(dur)
        var p1 = 0f
        var p2 = 0f
        var p3 = 0f
        for (i in buf.indices) {
            val t = i.toFloat() / sampleRate
            p1 += 2.0 * PI.toFloat() * f / sampleRate
            p2 += 2.0 * PI.toFloat() * (f * 2.76f) / sampleRate
            p3 += 2.0 * PI.toFloat() * (f * 5.40f) / sampleRate
            val e = env(t, 0.003f, decay)
            buf[i] = (sin(p1) + sin(p2) * 0.42f + sin(p3) * 0.18f) * e * amp
        }
        return toShort(buf)
    }

    /** Hard clack: very short noise transient. */
    private fun clack(dur: Float, amp: Float): ShortArray {
        val buf = alloc(dur)
        val rnd = Random(23)
        var lp = 0f
        for (i in buf.indices) {
            val t = i.toFloat() / sampleRate
            val noise = rnd.nextFloat() * 2f - 1f
            lp += (noise - lp) * 0.62f
            val e = env(t, 0.0015f, 52f)
            buf[i] = lp * e * amp
        }
        return toShort(buf)
    }

    /** Airy shimmer: several soft partials with slow beating. */
    private fun shimmer(dur: Float, amp: Float): ShortArray {
        val buf = alloc(dur)
        val freqs = floatArrayOf(523f, 661f, 784f, 1046f, 1318f)
        val phases = FloatArray(freqs.size)
        for (i in buf.indices) {
            val t = i.toFloat() / sampleRate
            var v = 0f
            for (k in freqs.indices) {
                phases[k] += 2.0 * PI.toFloat() * (freqs[k] + sin(t * 3.1f + k) * 2.5f) / sampleRate
                v += sin(phases[k]) / (k + 1.6f)
            }
            val e = env(t, 0.05f, 3.4f)
            buf[i] = v * e * amp * 0.5f
        }
        return toShort(buf)
    }

    /** Squeezed rubber squeak: fast pitch rise with a rough envelope. */
    private fun squeak(dur: Float, f0: Float, f1: Float, amp: Float): ShortArray {
        val buf = alloc(dur)
        var phase = 0f
        for (i in buf.indices) {
            val t = i.toFloat() / sampleRate
            val k = (t / dur).coerceIn(0f, 1f)
            val f = f0 + (f1 - f0) * (k * k)
            phase += 2.0 * PI.toFloat() * f / sampleRate
            val e = env(t, 0.006f, 12f)
            // slight waveshaping adds the "squeezed" edge
            val s = sin(phase)
            val shaped = s + 0.35f * s * s * s
            buf[i] = shaped * e * amp
        }
        return toShort(buf)
    }

    // ------------------------------------------------------------- playback

    fun play(toyId: String) {
        if (!enabled) return
        val track = player ?: return
        val data = samples[toyId] ?: samples["blob"] ?: return
        try {
            // MODE_STATIC rewinds with stop() before the next write.
            track.stop()
            track.write(data, 0, data.size)
            track.play()
        } catch (t: Throwable) {
            Log.w(TAG, "playback failed: " + t.message)
        }
    }

    fun release() {
        try {
            player?.stop()
            player?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "release failed: " + t.message)
        }
    }

    companion object {
        private const val TAG = "ClayAudio"
    }
}
