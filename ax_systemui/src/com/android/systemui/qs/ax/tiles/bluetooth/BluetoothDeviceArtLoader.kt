/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.systemui.qs.ax.tiles.bluetooth

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

@SysUISingleton
class BluetoothDeviceArtLoader
@Inject
constructor(
    @param:Application private val context: Context,
    @param:Background private val backgroundDispatcher: CoroutineDispatcher,
) {
    private val cache =
        object : LinkedHashMap<String, ImageBitmap>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ImageBitmap>?
            ): Boolean = size > MAX_CACHE_ENTRIES
        }
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val failedUris = ConcurrentHashMap<String, Long>()

    fun getCached(uriString: String?): ImageBitmap? {
        if (uriString.isNullOrBlank()) return null
        synchronized(cache) {
            return cache.get(uriString)
        }
    }

    fun evict(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        synchronized(cache) {
            cache.remove(uriString)
        }
        failedUris.remove(uriString)
    }

    suspend fun load(uri: Uri): ImageBitmap? {
        val key = uri.toString()
        if (key.isBlank()) return null

        getCached(key)?.let { return it }

        val failedAt = failedUris[key]
        if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < FAILURE_RETRY_MS) {
            return null
        }

        return coroutineScope {
            val deferred =
                inFlight.computeIfAbsent(key) {
                    async(backgroundDispatcher) {
                        try {
                            decodeHardwareImageBitmap(uri)?.also { image ->
                                synchronized(cache) {
                                    cache.put(key, image)
                                }
                                failedUris.remove(key)
                            } ?: run {
                                val now = SystemClock.elapsedRealtime()
                                if (failedUris.size >= MAX_CACHE_ENTRIES * 2) {
                                    failedUris.entries.removeIf { now - it.value >= FAILURE_RETRY_MS }
                                    if (failedUris.size >= MAX_CACHE_ENTRIES * 2) {
                                        failedUris.clear()
                                    }
                                }
                                failedUris[key] = now
                                null
                            }
                        } finally {
                            inFlight.remove(key)
                        }
                    }
                }
            deferred.await()
        }
    }

    private suspend fun decodeHardwareImageBitmap(uri: Uri): ImageBitmap? =
        withContext(backgroundDispatcher) {
            val resolver = context.contentResolver
            runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            val decoded =
                runCatching {
                        val source = ImageDecoder.createSource(resolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
                            decoder.isMutableRequired = false
                            val w = info.size.width
                            val h = info.size.height
                            val maxDim = max(w, h)
                            if (maxDim > MAX_ICON_DIMENSION_PX && w > 0 && h > 0) {
                                val scale = MAX_ICON_DIMENSION_PX.toFloat() / maxDim.toFloat()
                                decoder.setTargetSize(
                                    (w * scale).roundToInt().coerceAtLeast(1),
                                    (h * scale).roundToInt().coerceAtLeast(1),
                                )
                            }
                        }
                    }
                    .getOrElse {
                        decodeFallbackStream(uri)
                    }

            if (decoded == null || decoded.width <= 0 || decoded.height <= 0) {
                return@withContext null
            }

            runCatching {
                    val hwBitmap =
                        if (decoded.config == Bitmap.Config.HARDWARE) {
                            decoded
                        } else {
                            decoded.copy(Bitmap.Config.HARDWARE, false)?.also {
                                if (it !== decoded) decoded.recycle()
                            } ?: decoded
                        }
                    hwBitmap.asImageBitmap()
                }
                .onFailure { Log.w(TAG, "Failed converting Bluetooth icon for $uri", it) }
                .getOrNull()
        }

    private fun decodeFallbackStream(uri: Uri): Bitmap? =
        runCatching {
                val resolver = context.contentResolver
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
                val w = boundsOpts.outWidth
                val h = boundsOpts.outHeight
                if (w <= 0 || h <= 0) return null

                var sampleSize = 1
                while (max(w / sampleSize, h / sampleSize) > MAX_ICON_DIMENSION_PX * 2) {
                    sampleSize *= 2
                }
                val decodeOpts =
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                val raw =
                    resolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOpts)
                    } ?: return null

                val maxDim = max(raw.width, raw.height)
                if (maxDim > MAX_ICON_DIMENSION_PX) {
                    val scale = MAX_ICON_DIMENSION_PX.toFloat() / maxDim.toFloat()
                    val targetW = (raw.width * scale).roundToInt().coerceAtLeast(1)
                    val targetH = (raw.height * scale).roundToInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
                    if (scaled !== raw) raw.recycle()
                    scaled
                } else {
                    raw
                }
            }
            .onFailure { Log.w(TAG, "Fallback stream decode failed for $uri", it) }
            .getOrNull()

    private companion object {
        const val TAG = "BtDeviceArtLoader"
        const val MAX_CACHE_ENTRIES = 16
        const val MAX_ICON_DIMENSION_PX = 256
        const val FAILURE_RETRY_MS = 60_000L
    }
}
