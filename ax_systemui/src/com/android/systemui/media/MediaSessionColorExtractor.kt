package com.android.systemui.media

import android.app.WallpaperColors
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object MediaSessionColorExtractor {

    fun extractColor(drawable: Drawable): Int {
        val colors = if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            WallpaperColors.fromBitmap(drawable.bitmap)
        } else {
            WallpaperColors.fromDrawable(drawable)
        }
        return colors?.primaryColor?.toArgb() ?: 0
    }
}
