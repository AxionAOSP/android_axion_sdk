package com.android.axion.iconprovider

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.provider.Settings
import com.android.axion.iconloader.AdaptiveIconHelper
import com.android.axion.iconloader.ThemedIconItem
import com.android.axion.iconprovider.customicon.IconOverrideRepository
import com.android.axion.iconprovider.customicon.IconPackDrawableResolver
import com.android.axion.iconprovider.customicon.IconPackPreferenceStore

object AxIconEngine {

    @JvmStatic
    fun resolveCustomOrPackIcon(
        context: Context,
        componentName: ComponentName,
        iconDpi: Int,
    ): Drawable? {
        val override = IconOverrideRepository.getOverride(context, componentName)
        if (override != null) {
            val custom = IconPackDrawableResolver.loadDrawable(
                context,
                override.packPackage,
                override.drawableName,
                iconDpi,
            )
            if (custom != null) {
                return custom
            }
        }
        val packPackage = IconPackPreferenceStore.getIconPackPackage(context)
        return IconPackDrawableResolver.loadForComponent(context, packPackage, componentName, iconDpi)
    }

    @JvmStatic
    fun processIcon(
        context: Context,
        drawable: Drawable?,
    ): Drawable? {
        if (drawable == null) return null
        if (AdaptiveIconHelper.isAdaptiveDisabled(context)) {
            return AdaptiveIconHelper.wrapToDirectIcon(drawable)
        }
        return drawable
    }

    @JvmStatic
    fun getThemedIconItem(context: Context, packageName: String): ThemedIconItem? {
        return ThemedIconPackLoader.getExternalThemedIconItem(context, packageName)
    }

    @JvmStatic
    fun getSystemState(context: Context, iconStateUniqueId: String): String {
        val overrides = Settings.Secure.getString(context.contentResolver, "launcher_icon_overrides") ?: "{}"
        val shape = Settings.Secure.getString(context.contentResolver, "icon_shape_model") ?: ""
        return "," + iconStateUniqueId +
                "," + IconPackPreferenceStore.getIconPackPackage(context) +
                "," + IconPackPreferenceStore.getThemedIconPackPackage(context) +
                "," + overrides.hashCode() +
                "," + AdaptiveIconHelper.isAdaptiveDisabled(context) +
                "," + shape
    }
}
