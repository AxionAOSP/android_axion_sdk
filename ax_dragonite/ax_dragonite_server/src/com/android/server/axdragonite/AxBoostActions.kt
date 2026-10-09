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

internal class AnimationBoost(
    private val mHost: AxDragoniteImpl,
    private val mPid: Int,
    private val mRenderTid: Int,
    private val mBoost: Boolean,
) : Runnable {
    override fun run() {
        mHost.animationBoostInternal(mPid, mRenderTid, mBoost)
    }
}

internal class AxNameThreadAffinity(
    private val mPid: Int,
    private val mPackageName: String,
    private val mEnabled: Boolean,
) : Runnable {
    override fun run() {
        if (mEnabled) {
            AxNamedThreadAffinityFeature.getInstance().applyNamedAffinityForPid(mPid)
        } else {
            AxNamedThreadAffinityFeature.getInstance().resetAffinity()
        }
    }
}

internal class PlatformHandler(
    private val mHost: AxDragoniteImpl,
    private val mResources: PlatformResource,
) : Runnable {
    override fun run() {
        if (mResources.boostHandle <= 0) return
        mHost.logDebug(AxDragoniteImpl.TAG, "release platform handler: ${mResources.boostHandle}")
        mHost.mDragoniteExt.releaseBoost(mResources.boostHandle)
    }
}

internal class BackgroundLoadLimitRunnable(
    private val mHost: AxDragoniteImpl,
    private val mLimit: Int,
) : Runnable {
    override fun run() {
        mHost.mBackgroundLoadLimit = mLimit
    }
}

internal class PlatformResource(
    private val mHost: AxDragoniteImpl,
    private val mResourceSpec: String?,
) : Runnable {
    var boostHandle: Int = -1
        private set

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
        mHost.traceBegin("ProcessAffinity_pid:${mPid}_affinity:$mAffinity")
        val cores =
            mAffinity ?: AxCpuSets.resolveCores(AxCpuSets.CPUSET_ALL) ?: return mHost.traceEnd()
        try {
            Process.setThreadAffinity(mPid, cores)
            if (mRenderTid != -1) Process.setThreadAffinity(mRenderTid, cores)
        } catch (e: Exception) {
            mHost.logDebug(AxDragoniteImpl.TAG, "set process affinity failed: ${e.message}")
        }
        mHost.traceEnd()
    }
}

internal class SfBindControl(private val mHost: AxDragoniteImpl, private val mEnabled: Boolean) :
    Runnable {
    override fun run() {
        mHost.sfBindControllInternal(mEnabled)
    }
}

internal abstract class FileWriteAction(
    protected val mHost: AxDragoniteImpl,
    protected val mTarget: File,
) : Runnable {
    protected abstract val mValue: String
    protected abstract val traceName: String

    override fun run() {
        mHost.logDebug(AxDragoniteImpl.TAG, "write $mValue to $mTarget")
        mHost.traceBegin(traceName)
        try {
            FileUtils.stringToFile(mTarget, mValue)
        } catch (unused: IOException) {
            mHost.logDebug(AxDragoniteImpl.TAG, "adjust $mTarget to $mValue failed!")
        }
        mHost.traceEnd()
    }
}

internal class CpuctlCpuShares(host: AxDragoniteImpl, target: File, shares: String) :
    FileWriteAction(host, target) {
    override val mValue: String = shares
    override val traceName: String = "CpuctlCpuShares_shares:${mValue}_path:$mTarget"
}

internal class CpuctlProcs(host: AxDragoniteImpl, target: File, pid: Int) :
    FileWriteAction(host, target) {
    override val mValue: String = pid.toString()
    override val traceName: String = "CpuctlProcs_pid:${mValue}_path:$mTarget"
}

internal class CpuctlUclamp(host: AxDragoniteImpl, target: File, value: String) :
    FileWriteAction(host, target) {
    override val mValue: String = value
    override val traceName: String = "CpuctlUclamp_value:${mValue}_path:$mTarget"
}

internal class CpusetCpus(host: AxDragoniteImpl, target: File, cpus: String) :
    FileWriteAction(host, target) {
    override val mValue: String = AxCpuSets.resolve(cpus)
    override val traceName: String = "CpusetCpus_cpus:${mValue}_path:$mTarget"
}

internal class CpusetProc(host: AxDragoniteImpl, target: File, pid: Int) :
    FileWriteAction(host, target) {
    override val mValue: String = pid.toString()
    override val traceName: String = "CpusetProc_pid:${mValue}_path:$mTarget"
}

internal class ThreadAffinity(
    private val mHost: AxDragoniteImpl,
    private val mTid: Int,
    private val mCpus: IntArray?,
) : Runnable {
    override fun run() {
        val cores = mCpus ?: return mHost.traceEnd()
        mHost.logDebug(AxDragoniteImpl.TAG, "set $mTid to ${cores.contentToString()}")
        mHost.traceBegin("ThreadAffinity_tid${mTid}_affinity:$cores")
        try {
            Process.setThreadAffinity(mTid, cores)
        } catch (e: Exception) {
            mHost.logDebug(
                AxDragoniteImpl.TAG,
                "set thread affinity failed for $mTid: ${e.message}",
            )
        }
        mHost.traceEnd()
    }
}

internal class ThreadBoost(
    private val mHost: AxDragoniteImpl,
    private val mTids: IntArray?,
    private val mBoost: Boolean,
) : Runnable {
    override fun run() {
        val tids = mTids ?: return
        for (tid in tids) {
            mHost.threadBoostInternal(tid, mBoost)
        }
    }
}

internal class KswapdAffinityControlRunnable(
    private val mHost: AxDragoniteImpl,
    private val mAffinity: IntArray?,
) : Runnable {
    override fun run() {
        val affinity = mAffinity?.contentToString()
        val pids = mHost.mKswapdPids?.contentToString()
        mHost.logDebug(AxDragoniteImpl.TAG, "KswapdAffinity: affinity = $affinity, pids = $pids")
        mHost.adjustKswapdAffinityInternal(mAffinity)
    }
}
