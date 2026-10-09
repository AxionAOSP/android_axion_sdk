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

package com.android.launcher3

import android.os.Bundle
import android.os.Process
import android.util.Log
import com.android.internal.dragonite.AxDragoniteConstants
import com.android.internal.dragonite.AxDragoniteInternal
import com.android.launcher3.util.Executors
import java.util.HashSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AxLauncherSceneBooster private constructor() {

    companion object {
        private const val TAG = "AxLauncherSceneBooster"
        private const val PACKAGE_LAUNCHER = "com.android.launcher3"
        private const val DEFAULT_TIMEOUT_MS = 60000

        const val SCENE_APP_LAUNCH_SPEED_UP = AxDragoniteConstants.SCENE_APP_START
        const val SCENE_NORMAL_ANIMATION = AxDragoniteConstants.SCENE_LAUNCHER_ANIMATION
        const val SCENE_DATA_LOADING = AxDragoniteConstants.SCENE_LAUNCHER_ITEM_LOADING
        const val SCENE_FOLDER_ANIMATION = AxDragoniteConstants.SCENE_LAUNCHER_OVERLOAD
        const val SCENE_DRAG_AND_DROP = AxDragoniteConstants.SCENE_LAUNCHER_SCREEN_ON
        const val SCENE_FLING = AxDragoniteConstants.SCENE_FLING_LEVEL_1

        @JvmStatic fun get(): AxLauncherSceneBooster = InstanceHolder.INSTANCE

        @JvmStatic
        fun beginScene(sceneId: Int) {
            val host = get()
            synchronized(host.tokenLock) { host.addTokenLocked(host.obtainToken(sceneId)) }
        }

        @JvmStatic
        fun endScene(sceneId: Int) {
            val host = get()
            synchronized(host.tokenLock) {
                host.tokensByScene[sceneId]?.firstOrNull()?.let { host.removeTokenLocked(it) }
            }
        }

        @JvmStatic
        fun beginAppTransition(tid: Int) {
            val host = get()
            synchronized(host.tokenLock) {
                host.addTokenLocked(host.obtainToken(SCENE_APP_LAUNCH_SPEED_UP), tid)
            }
        }

        @JvmStatic fun endAppTransition() = endScene(SCENE_APP_LAUNCH_SPEED_UP)

        @JvmStatic
        fun startHomeGesture(): SceneToken {
            val host = get()
            synchronized(host.tokenLock) {
                host.homeToken
                    ?.takeIf { it.state.get() != 2 }
                    ?.let {
                        return it
                    }
                val token = host.obtainToken(SCENE_NORMAL_ANIMATION)
                host.addTokenLocked(token)
                host.homeToken = token
                return token
            }
        }

        @JvmStatic
        fun finishHomeGesture() {
            val host = get()
            val token =
                synchronized(host.tokenLock) { host.homeToken.also { host.homeToken = null } }
                    ?: return
            host.releaseToken(token)
        }

        @JvmStatic
        fun releaseAll() {
            val host = get()
            synchronized(host.tokenLock) {
                for (sceneId in HashSet(host.tokensByScene.keys)) {
                    host.releaseScene(sceneId)
                }
                host.tokensByScene.clear()
                host.activeHandlesByScene.clear()
            }
        }
    }

    private object InstanceHolder {
        val INSTANCE = AxLauncherSceneBooster()
    }

    class SceneToken(val id: Long, val sceneId: Int) {
        val state = AtomicInteger(0)

        override fun toString(): String =
            "SceneToken(id=$id, sceneId=$sceneId, state=${state.get()})"
    }

    private val activeHandlesByScene = ConcurrentHashMap<Int, Int>()
    private val tokensByScene = HashMap<Int, HashSet<SceneToken>>()
    private val tokenLock = Any()
    private val tokenSeq = AtomicLong(1)
    private var homeToken: SceneToken? = null

    private val releaseNormalAnimationRunnable = Runnable { releaseScene(SCENE_NORMAL_ANIMATION) }
    private val releaseFolderAnimationRunnable = Runnable { releaseScene(SCENE_FOLDER_ANIMATION) }
    private val releaseAppLaunchRunnable = Runnable { releaseScene(SCENE_APP_LAUNCH_SPEED_UP) }

    private fun obtainToken(sceneId: Int): SceneToken =
        SceneToken(tokenSeq.getAndIncrement(), sceneId)

    private fun addTokenLocked(token: SceneToken, tid: Int = -1) {
        val tokens = tokensByScene.getOrPut(token.sceneId) { HashSet() }
        tokens.add(token)
        token.state.set(1)
        if (tokens.size == 1) acquireSceneIpc(token.sceneId, tid)
    }

    fun acquireToken(sceneId: Int): SceneToken {
        val token = obtainToken(sceneId)
        startToken(token)
        return token
    }

    fun startToken(token: SceneToken?) {
        if (token == null || token.state.get() == 1) return
        synchronized(tokenLock) { addTokenLocked(token) }
    }

    fun releaseToken(token: SceneToken?) {
        if (token == null || token.state.get() == 2) return
        synchronized(tokenLock) { removeTokenLocked(token) }
    }

    private fun removeTokenLocked(token: SceneToken) {
        val tokens = tokensByScene[token.sceneId] ?: return
        if (!tokens.remove(token)) return
        token.state.set(2)
        if (tokens.isEmpty()) {
            tokensByScene.remove(token.sceneId)
            scheduleReleaseScene(token.sceneId)
        }
    }

    private fun scheduleReleaseScene(sceneId: Int) {
        when (sceneId) {
            SCENE_NORMAL_ANIMATION ->
                Executors.MAIN_EXECUTOR.execute(releaseNormalAnimationRunnable)
            SCENE_FOLDER_ANIMATION ->
                Executors.MAIN_EXECUTOR.execute(releaseFolderAnimationRunnable)
            SCENE_APP_LAUNCH_SPEED_UP -> Executors.MAIN_EXECUTOR.execute(releaseAppLaunchRunnable)
            else -> releaseScene(sceneId)
        }
    }

    private fun acquireSceneIpc(sceneId: Int, tid: Int) {
        Executors.UI_HELPER_EXECUTOR.execute {
            try {
                val data = Bundle()
                val myPid = Process.myPid()
                val mainThreadId = Process.myTid()
                data.putInt(AxDragoniteConstants.KEY_PID, myPid)
                if (tid != -1) {
                    data.putString(
                        AxDragoniteConstants.KEY_PARAMS,
                        "501:$tid,$mainThreadId;600:$tid",
                    )
                } else {
                    data.putString(AxDragoniteConstants.KEY_PARAMS, "501:$mainThreadId")
                }
                data.putString(AxDragoniteConstants.KEY_PACKAGE, PACKAGE_LAUNCHER)
                data.putString(AxDragoniteConstants.KEY_PACKAGE_NAME, PACKAGE_LAUNCHER)
                if (sceneId != SCENE_APP_LAUNCH_SPEED_UP) {
                    data.putInt(AxDragoniteConstants.KEY_DURATION, DEFAULT_TIMEOUT_MS)
                }

                val handle = AxDragoniteInternal.sceneBoostAcquire(sceneId, data)
                if (handle > 0) {
                    activeHandlesByScene[sceneId] = handle
                }
            } catch (e: Exception) {
                Log.e(TAG, "sceneBoostAcquire failed for scene $sceneId", e)
            }
        }
    }

    private fun releaseScene(sceneId: Int) {
        val handle = activeHandlesByScene.remove(sceneId) ?: return
        if (handle <= 0) return
        Executors.UI_HELPER_EXECUTOR.execute {
            try {
                AxDragoniteInternal.sceneBoostRelease(handle)
            } catch (e: Exception) {
                Log.e(TAG, "sceneBoostRelease failed for handle $handle", e)
            }
        }
    }

    fun onAppTransition(tid: Int): SceneToken {
        val token = obtainToken(SCENE_APP_LAUNCH_SPEED_UP)
        synchronized(tokenLock) { addTokenLocked(token, tid) }
        return token
    }

    fun onAnimationStart(): SceneToken = acquireToken(SCENE_NORMAL_ANIMATION)

    fun onFolderAnimation(): SceneToken = acquireToken(SCENE_FOLDER_ANIMATION)

    fun onDragAndDrop(): SceneToken = acquireToken(SCENE_DRAG_AND_DROP)

    fun onDataLoading(): SceneToken = acquireToken(SCENE_DATA_LOADING)
}
