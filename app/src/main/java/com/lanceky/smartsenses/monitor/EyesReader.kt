package com.lanceky.smartsenses.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings.System as SystemSettings
import com.lanceky.smartsenses.rules.EyesSample

/**
 * Reads the screen brightness setting and the room light from the phone's light sensor. Neither needs a permission.
 * Brightness % is the stored 0–255 value; on the Galaxy A03 the slider moves linearly with it (128 ≈ the middle).
 */
class EyesReader(private val context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val light: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var listening = false

    @Volatile
    private var lux: Float? = null

    val hasLightSensor: Boolean get() = light != null

    /** Listens only while Eyes is on, so the sensor isn't kept busy for nothing. */
    fun setListening(on: Boolean) {
        if (on == listening || light == null) return
        listening = on
        if (on) {
            sensors.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            sensors.unregisterListener(this)
            lux = null
        }
    }

    fun read(): EyesSample {
        val resolver = context.contentResolver
        val raw = SystemSettings.getInt(resolver, SystemSettings.SCREEN_BRIGHTNESS, 0).coerceIn(0, 255)
        val auto = SystemSettings.getInt(resolver, SystemSettings.SCREEN_BRIGHTNESS_MODE, 0) ==
            SystemSettings.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        return EyesSample(brightnessPercent = (raw * 100 + 127) / 255, autoBrightness = auto, lux = lux)
    }

    override fun onSensorChanged(event: SensorEvent) {
        lux = event.values[0]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
