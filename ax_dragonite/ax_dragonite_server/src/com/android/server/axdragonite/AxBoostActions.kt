/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.axdragonite

import android.os.FileUtils
import android.os.Process
import java.io.File
import java.io.IOException

internal class PlatformResource(
    private val mHost: AxDragoniteImpl,
    private val mResourceSpec: String?,
) : Runnable {
    var boostHandle: Int = -1
        private set

    val release = Runnable {
        if (boostHandle <= 0) return@Runnable
        mHost.logDebug(AxDragoniteImpl.TAG, "release platform handler: $boostHandle")
        mHost.mDragoniteExt.releaseBoost(boostHandle)
    }

    init {
        mHost.logDebug(AxDragoniteImpl.TAG, "resources: $mResourceSpec")
    }

    override fun run() {
        val spec = mResourceSpec
        if (spec.isNullOrEmpty()) {
            mHost.logDebug(AxDragoniteImpl.TAG, "Boost spec is empty, return!")
            return
        }
        boostHandle = mHost.mDragoniteExt.acquireBoost(spec)
        mHost.logDebug(AxDragoniteImpl.TAG, "executeHandle = $boostHandle")
    }
}

internal class ProcessAffinity(
    private val mHost: AxDragoniteImpl,
    private val mPid: Int,
    private val mRenderTid: Int,
    private val mAffinity: IntArray?,
) : Runnable {
    override fun run() {
        mHost.logDebug(
            AxDragoniteImpl.TAG,
            "set $mPid/$mRenderTid to ${mAffinity?.contentToString()}",
        )
        mHost.trace("ProcessAffinity_pid:${mPid}_affinity:${mAffinity?.contentToString()}") {
            val cores = mAffinity ?: AxCpuSets.resolveCores(AxCpuSets.CPUSET_ALL) ?: return
            try {
                Process.setThreadAffinity(mPid, cores)
                if (mRenderTid != -1) Process.setThreadAffinity(mRenderTid, cores)
            } catch (e: Exception) {
                mHost.logDebug(AxDragoniteImpl.TAG, "set process affinity failed: ${e.message}")
            }
        }
    }
}

internal class FileWrite(
    private val mHost: AxDragoniteImpl,
    private val mTarget: File,
    private val mValue: String,
) : Runnable {
    override fun run() {
        mHost.logDebug(AxDragoniteImpl.TAG, "write $mValue to $mTarget")
        mHost.trace("FileWrite_value:${mValue}_path:$mTarget") {
            try {
                FileUtils.stringToFile(mTarget, mValue)
            } catch (unused: IOException) {
                mHost.logDebug(AxDragoniteImpl.TAG, "adjust $mTarget to $mValue failed!")
            }
        }
    }
}

internal class ThreadAffinity(
    private val mHost: AxDragoniteImpl,
    private val mTid: Int,
    private val mCpus: IntArray?,
) : Runnable {
    override fun run() {
        val cores = mCpus ?: AxCpuSets.resolveCores(AxCpuSets.CPUSET_ALL) ?: return
        mHost.logDebug(AxDragoniteImpl.TAG, "set $mTid to ${cores.contentToString()}")
        mHost.trace("ThreadAffinity_tid${mTid}_affinity:${cores.contentToString()}") {
            try {
                Process.setThreadAffinity(mTid, cores)
            } catch (e: Exception) {
                mHost.logDebug(
                    AxDragoniteImpl.TAG,
                    "set thread affinity failed for $mTid: ${e.message}",
                )
            }
        }
    }
}
