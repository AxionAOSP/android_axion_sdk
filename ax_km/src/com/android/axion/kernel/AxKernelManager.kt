package com.android.axion.kernel

import android.app.ActivityManager
import android.app.IActivityManager
import com.android.internal.kernel.AxKernelControl
import com.android.internal.kernel.AxKernelMetrics

object AxKernelManager {
    val amService: IActivityManager by lazy { ActivityManager.getService() }

    fun getControls(): List<AxKernelControl> =
        runCatching { amService.axKernelControls ?: emptyList() }.getOrDefault(emptyList())

    fun setControlValue(id: String, value: Int): Boolean =
        runCatching { amService.setAxKernelControlValue(id, value) }.getOrDefault(false)

    fun getMetrics(): AxKernelMetrics? =
        getMetrics(AxKernelMetrics.CPU_TIME_UNAVAILABLE_TICKS, AxKernelMetrics.CPU_TIME_UNAVAILABLE_TICKS)

    fun getMetrics(prevActiveTicks: Long, prevTotalTicks: Long): AxKernelMetrics? =
        runCatching { amService.getAxKernelMetrics(prevActiveTicks, prevTotalTicks) }.getOrNull()
}
