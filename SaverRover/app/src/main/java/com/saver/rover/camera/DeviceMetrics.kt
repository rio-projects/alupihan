package com.saver.rover.camera

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock

/** Sample sparingly; battery temperature is explicitly not a SoC temperature measurement. */
class DeviceMetrics(private val context: Context) {
    private var lastWall = SystemClock.elapsedRealtime()
    private var lastCpu = Process.getElapsedCpuTime()
    private var lastSample = ""

    fun sample(): String {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWall < 2000) return lastSample
        val cpu = Process.getElapsedCpuTime()
        val percent = 100f * (cpu - lastCpu) / (now - lastWall)
        lastCpu = cpu
        lastWall = now
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus.toString()
        } else "n/a"
        lastSample = "CPU ${percent.toInt()}% (100%=1 core) | RAM ${Debug.getPss() / 1024} MB | " +
            "battery ${if (temperature >= 0) "${temperature / 10f}°C" else "n/a"} | thermal $thermal"
        return lastSample
    }
}
