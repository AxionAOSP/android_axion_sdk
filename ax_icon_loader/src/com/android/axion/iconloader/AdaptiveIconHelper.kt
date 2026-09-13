package com.android.axion.iconloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.provider.Settings

object AdaptiveIconHelper {

    private const val PREF_DISABLE_ADAPTIVE_ICONS = "pref_disable_adaptive_icons"
    private const val PREF_ICON_PACK_PACKAGE = "pref_icon_pack_package"

    @JvmStatic
    fun isAdaptiveDisabled(context: Context): Boolean {
        val packPkg = Settings.Secure.getString(context.contentResolver, PREF_ICON_PACK_PACKAGE)
        if (packPkg.isNullOrEmpty()) {
            return false
        }
        return Settings.Secure.getInt(context.contentResolver, PREF_DISABLE_ADAPTIVE_ICONS, 0) == 1
    }

    @JvmStatic
    fun wrapToDirectIcon(drawable: Drawable): Drawable {
        if (drawable is AdaptiveIconDrawable && canUnwrapAdaptiveIcon(drawable)) {
            val fg = drawable.foreground ?: return drawable
            return InsetDrawable(fg, -AdaptiveIconDrawable.getExtraInsetFraction())
        }
        return drawable
    }

    @JvmStatic
    fun canUnwrapAdaptiveIcon(icon: AdaptiveIconDrawable?): Boolean {
        if (icon == null || isExtenderIcon(icon)) return false
        val fg = icon.foreground ?: return false
        val bg = icon.background ?: return false
        return isUniformBackground(bg) && hasTransparentCorners(fg)
    }

    private fun isExtenderIcon(icon: AdaptiveIconDrawable): Boolean {
        val name = icon.javaClass.name
        return name.contains("Extender") || name.contains("ClockDrawableWrapper")
    }

    private fun isUniformBackground(bg: Drawable): Boolean {
        if (bg is ColorDrawable) return true
        return try {
            val bm = Bitmap.createBitmap(4, 4, ARGB_8888)
            val canvas = Canvas(bm)
            val oldBounds = Rect(bg.bounds)
            bg.setBounds(0, 0, 4, 4)
            bg.draw(canvas)
            bg.bounds = oldBounds
            val c0 = bm.getPixel(0, 0)
            val c1 = bm.getPixel(3, 0)
            val c2 = bm.getPixel(0, 3)
            val c3 = bm.getPixel(3, 3)
            val cMid = bm.getPixel(2, 2)
            bm.recycle()
            c0 == c1 && c1 == c2 && c2 == c3 && c3 == cMid
        } catch (_: Exception) {
            false
        }
    }

    private fun hasTransparentCorners(fg: Drawable): Boolean {
        return try {
            val bm = Bitmap.createBitmap(4, 4, ARGB_8888)
            val canvas = Canvas(bm)
            val oldBounds = Rect(fg.bounds)
            fg.setBounds(0, 0, 4, 4)
            fg.draw(canvas)
            fg.bounds = oldBounds
            val a0 = Color.alpha(bm.getPixel(0, 0))
            val a1 = Color.alpha(bm.getPixel(3, 0))
            val a2 = Color.alpha(bm.getPixel(0, 3))
            val a3 = Color.alpha(bm.getPixel(3, 3))
            bm.recycle()
            a0 == 0 && a1 == 0 && a2 == 0 && a3 == 0
        } catch (_: Exception) {
            false
        }
    }
}
