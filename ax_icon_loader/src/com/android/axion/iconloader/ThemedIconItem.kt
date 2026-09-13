package com.android.axion.iconloader

import android.content.res.Resources
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable

data class ThemedIconItem(
    val resources: Resources,
    val resId: Int,
) {
    fun loadPaddedDrawable(): Drawable? {
        if (!"drawable".equals(resources.getResourceTypeName(resId))) {
            return null
        }
        val d = resources.getDrawable(resId, null).mutate()
        val inner = InsetDrawable(d, 0.2f)
        val extra = AdaptiveIconDrawable.getExtraInsetFraction()
        val inset = extra / (1f + 2f * extra)
        return InsetDrawable(inner, inset)
    }
}
