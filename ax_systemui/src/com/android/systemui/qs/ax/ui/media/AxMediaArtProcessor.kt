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

package com.android.systemui.qs.ax.ui.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object AxMediaArtProcessor {
    const val CARD_BACKGROUND_WIDTH = 640
    const val CARD_BACKGROUND_HEIGHT = 320
    const val THUMBNAIL_DIMENSION = 256
    const val LOCKSCREEN_DIMENSION = 500

    private const val CACHE_CAPACITY = 16
    private val memoryCache = LruCache<String, ImageBitmap>(CACHE_CAPACITY)

    fun getCardBackground(drawable: Drawable?, cacheKey: String? = null): ImageBitmap? =
        processHardwareImageBitmap(drawable, CARD_BACKGROUND_WIDTH, CARD_BACKGROUND_HEIGHT, cacheKey)

    fun getThumbnail(drawable: Drawable?, cacheKey: String? = null): ImageBitmap? =
        processHardwareImageBitmap(drawable, THUMBNAIL_DIMENSION, THUMBNAIL_DIMENSION, cacheKey)

    fun getLockscreenArt(drawable: Drawable?, cacheKey: String? = null): ImageBitmap? =
        processHardwareImageBitmap(drawable, LOCKSCREEN_DIMENSION, LOCKSCREEN_DIMENSION, cacheKey)

    fun processHardwareImageBitmap(
        drawable: Drawable?,
        targetWidth: Int,
        targetHeight: Int,
        cacheKey: String? = null
    ): ImageBitmap? {
        if (drawable == null || targetWidth <= 0 || targetHeight <= 0) return null

        val fullCacheKey = if (!cacheKey.isNullOrEmpty()) {
            "${cacheKey}:${targetWidth}x${targetHeight}"
        } else null

        if (fullCacheKey != null) {
            val cached = memoryCache.get(fullCacheKey)
            if (cached != null) {
                return cached
            }
        }

        val hwBitmap = processHardwareBitmap(drawable, targetWidth, targetHeight) ?: return null
        val imageBitmap = hwBitmap.asImageBitmap()

        if (fullCacheKey != null) {
            memoryCache.put(fullCacheKey, imageBitmap)
        }

        return imageBitmap
    }

    fun processHardwareBitmap(
        drawable: Drawable?,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        if (drawable == null || targetWidth <= 0 || targetHeight <= 0) return null

        val srcBitmap = try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val w = drawable.intrinsicWidth.coerceIn(1, targetWidth)
                val h = drawable.intrinsicHeight.coerceIn(1, targetHeight)
                val sw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sw)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                sw
            }
        } catch (t: Throwable) {
            return null
        }

        if (srcBitmap.width <= 0 || srcBitmap.height <= 0) return null

        val scale = maxOf(
            targetWidth.toFloat() / srcBitmap.width.toFloat(),
            targetHeight.toFloat() / srcBitmap.height.toFloat()
        )

        val cropW = (targetWidth / scale).toInt().coerceIn(1, srcBitmap.width)
        val cropH = (targetHeight / scale).toInt().coerceIn(1, srcBitmap.height)
        val cropX = ((srcBitmap.width - cropW) / 2).coerceIn(0, srcBitmap.width - cropW)
        val cropY = ((srcBitmap.height - cropH) / 2).coerceIn(0, srcBitmap.height - cropH)

        val croppedBitmap = try {
            if (cropX > 0 || cropY > 0 || cropW < srcBitmap.width || cropH < srcBitmap.height) {
                Bitmap.createBitmap(srcBitmap, cropX, cropY, cropW, cropH)
            } else {
                srcBitmap
            }
        } catch (t: Throwable) {
            srcBitmap
        }

        val finalScaledBitmap = try {
            if (croppedBitmap.width != targetWidth || croppedBitmap.height != targetHeight) {
                Bitmap.createScaledBitmap(croppedBitmap, targetWidth, targetHeight, true)
            } else {
                croppedBitmap
            }
        } catch (t: Throwable) {
            croppedBitmap
        }

        return try {
            if (finalScaledBitmap.config == Bitmap.Config.HARDWARE) {
                finalScaledBitmap
            } else {
                finalScaledBitmap.copy(Bitmap.Config.HARDWARE, false) ?: finalScaledBitmap
            }
        } catch (t: Throwable) {
            finalScaledBitmap
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
