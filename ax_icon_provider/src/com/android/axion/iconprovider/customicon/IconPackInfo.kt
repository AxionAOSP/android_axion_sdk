package com.android.axion.iconprovider.customicon

import android.graphics.drawable.Drawable

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

data class IconPackDrawableInfo(
    val packPackage: String,
    val drawableName: String,
    val label: String,
)
