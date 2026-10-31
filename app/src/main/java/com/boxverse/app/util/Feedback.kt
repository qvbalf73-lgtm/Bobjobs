package com.boxverse.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.boxverse.app.domain.Rarity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** 触觉反馈 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    var enabled: Boolean = true

    private fun vibrate(effect: VibrationEffect) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(effect) }
    }

    fun tick() = vibrate(VibrationEffect.createOneShot(18, 120))
    fun success() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 40), intArrayOf(0, 160, 0, 220), -1))
    fun shake() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 50, 40, 50, 40), intArrayOf(0, 90, 0, 120, 0, 150), -1))

    fun reveal(rarity: Rarity) = when (rarity) {
        Rarity.R -> vibrate(VibrationEffect.createOneShot(30, 150))
        Rarity.SR -> vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 60), intArrayOf(0, 180, 0, 230), -1))
        Rarity.SSR -> vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 50, 60, 50, 160), intArrayOf(0, 200, 0, 230, 0, 255), -1))
        Rarity.UR -> vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 50, 40, 50, 40, 50, 40, 260), intArrayOf(0, 180, 0, 210, 0, 240, 0, 255), -1)
        )
    }
}

/**
 * 程序合成的开盒音效（不使用任何第三方音频素材）。
 * 普通款：清脆"啵"；稀有：双音；隐藏款：琶音；超级隐藏：长琶音 + 闪烁泛音。
 */
class SoundFx {
    var enabled: Boolean = true
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sampleRate = 44100

    fun shake() = play(listOf(Note(330.0, 0.05, 0.25), Note(392.0, 0.05, 0.25)))
    fun tap() = play(listOf(Note(880.0, 0.04, 0.2)))

    fun reveal(rarity: Rarity) = when (rarity) {
        Rarity.R -> play(listOf(Note(660.0, 0.12, 0.45)))
        Rarity.SR -> play(listOf(Note(587.33, 0.1, 0.4), Note(880.0, 0.22, 0.45)))
        Rarity.SSR -> play(
            listOf(Note(523.25, 0.1, 0.4), Note(659.25, 0.1, 0.4), Note(783.99, 0.1, 0.4), Note(1046.5, 0.45, 0.5)),
            shimmer = true,
        )
        Rarity.UR -> play(
            listOf(
                Note(523.25, 0.09, 0.4), Note(659.25, 0.09, 0.4), Note(783.99, 0.09, 0.4), Note(1046.5, 0.09, 0.45),
                Note(1318.5, 0.09, 0.45), Note(1568.0, 0.7, 0.5),
            ),
            shimmer = true,
        )
    }

    private data class Note(val freq: Double, val seconds: Double, val volume: Double)

    private fun play(notes: List<Note>, shimmer: Boolean = false) {
        if (!enabled) return
        scope.launch {
            runCatching {
                val total = notes.sumOf { (it.seconds * sampleRate).toInt() }
                val pcm = ShortArray(total)
                var offset = 0
                for (n in notes) {
                    val len = (n.seconds * sampleRate).toInt()
                    for (i in 0 until len) {
                        val t = i.toDouble() / sampleRate
                        val env = minOf(1.0, i / (0.004 * sampleRate)) * exp(-3.2 * t / n.seconds)
                        var s = sin(2 * PI * n.freq * t) + 0.35 * sin(2 * PI * n.freq * 2 * t)
                        if (shimmer) s += 0.18 * sin(2 * PI * n.freq * 3.01 * t) * sin(2 * PI * 7 * t)
                        pcm[offset + i] = (s * env * n.volume * 0.6 * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    offset += len
                }
                val track = AudioTrack.Builder()
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
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                delay((total * 1000L / sampleRate) + 150)
                track.release()
            }
        }
    }
}

object Dates {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val cnFmt = DateTimeFormatter.ofPattern("yyyy年M月d日")

    fun today(): Long = LocalDate.now().toEpochDay()
    fun format(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(fmt)
    fun formatCn(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(cnFmt)
    fun formatMillis(millis: Long): String {
        val dt = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
    fun epochDayOfMillis(millis: Long): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

    fun greeting(): String {
        val h = java.time.LocalTime.now().hour
        return when (h) {
            in 5..10 -> "早上好"
            in 11..12 -> "中午好"
            in 13..17 -> "下午好"
            in 18..22 -> "晚上好"
            else -> "夜深了"
        }
    }
}
