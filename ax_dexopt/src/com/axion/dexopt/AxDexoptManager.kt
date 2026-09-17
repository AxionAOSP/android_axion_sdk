package com.axion.dexopt

import android.app.ActivityManager
import android.app.IActivityManager
import android.util.Log
import com.android.internal.dexopt.IAxUserStartDexoptStatusHandler

class AxDexoptManager private constructor() {

    abstract class Callback {
        open fun onConnected(pkgs: List<String>, state: Int, curOptPkg: String?) {}
        open fun onProgress(current: Int, total: Int, pkgName: String?) {}
        open fun onCompleted() {}
        open fun onError(error: String?) {}
    }

    companion object {
        private const val TAG = "AxDexoptManager"

        val instance: AxDexoptManager by lazy { AxDexoptManager() }
        val amService: IActivityManager by lazy { ActivityManager.getService() }

        private var activeCallback: Callback? = null

        private val statusHandler = object : IAxUserStartDexoptStatusHandler.Stub() {
            override fun notifyConnected(pkgs: List<String>?, status: Int, message: String?) {
                activeCallback?.onConnected(pkgs ?: emptyList(), status, message)
            }

            override fun notifyProgress(current: Int, total: Int, pkgName: String?) {
                activeCallback?.onProgress(current, total, pkgName)
            }

            override fun notifyCompleted() {
                activeCallback?.onCompleted()
            }

            override fun notifyError(error: String?) {
                activeCallback?.onError(error)
            }
        }

        fun connect(callback: Callback) {
            activeCallback = callback
            try {
                amService.connectUserDexopt(statusHandler)
            } catch (t: Throwable) {
                logError("connectUserDexopt", t)
                callback.onError(t.message)
                return
            }
        }

        fun disconnect() {
            activeCallback = null
            try {
                amService.disconnectUserDexopt()
            } catch (t: Throwable) {
                logError("disconnectUserDexopt", t)
                return
            }
        }

        fun getPackagesToBeOptimized(): List<String> =
            try {
                amService.packagesToBeOptimized ?: emptyList()
            } catch (t: Throwable) {
                logError("getPackagesToBeOptimized", t)
                emptyList()
            }

        fun performDexOptimization() {
            try {
                amService.performUserDexopt()
            } catch (t: Throwable) {
                logError("performUserDexopt", t)
                return
            }
        }

        private fun logError(method: String, t: Throwable) {
            Log.e(TAG, "Failed to $method", t)
        }
    }
}
