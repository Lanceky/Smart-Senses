package com.lanceky.smartsenses.monitor

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.lanceky.smartsenses.rules.AudioSample
import com.lanceky.smartsenses.rules.Output
import kotlin.math.roundToInt

/** Asks the phone three questions. None of them needs a permission. */
class AudioReader(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun read(): AudioSample {
        val stream = AudioManager.STREAM_MUSIC
        val volume = audio.getStreamVolume(stream)
        val min = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(stream) else 0
        val max = audio.getStreamMaxVolume(stream)
        val percent = if (max > min) ((volume - min) * 100f / (max - min)).roundToInt() else 0
        return AudioSample(output = headphoneOutput(), volumePercent = percent, playing = audio.isMusicActive)
    }

    private fun headphoneOutput(): Output? {
        val types = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        return when {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES in types || AudioDeviceInfo.TYPE_WIRED_HEADSET in types -> Output.WIRED
            AudioDeviceInfo.TYPE_USB_HEADSET in types -> Output.USB
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in types -> Output.BLUETOOTH
            Build.VERSION.SDK_INT >= 31 && AudioDeviceInfo.TYPE_BLE_HEADSET in types -> Output.BLUETOOTH
            else -> null
        }
    }
}
