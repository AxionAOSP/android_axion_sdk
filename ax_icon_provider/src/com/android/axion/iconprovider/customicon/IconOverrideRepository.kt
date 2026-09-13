package com.android.axion.iconprovider.customicon

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import org.json.JSONObject

object IconOverrideRepository {
    private const val PREF_ICON_OVERRIDES = "launcher_icon_overrides"
    private const val KEY_PACK_PACKAGE = "packPackage"
    private const val KEY_DRAWABLE_NAME = "drawableName"

    @JvmStatic
    fun getOverride(context: Context, componentName: ComponentName): IconOverride? {
        val root = readRoot(context)
        val key = componentName.flattenToString()
        if (!root.has(key)) return null
        val obj = root.optJSONObject(key) ?: return null
        val packPackage = obj.optString(KEY_PACK_PACKAGE, "")
        val drawableName = obj.optString(KEY_DRAWABLE_NAME, "")
        if (packPackage.isEmpty() || drawableName.isEmpty()) return null
        return IconOverride(packPackage, drawableName)
    }

    @JvmStatic
    fun hasOverride(context: Context, componentName: ComponentName): Boolean {
        return getOverride(context, componentName) != null
    }

    @JvmStatic
    fun setOverride(context: Context, componentName: ComponentName, override: IconOverride) {
        val root = readRoot(context)
        root.put(
            componentName.flattenToString(),
            JSONObject()
                .put(KEY_PACK_PACKAGE, override.packPackage)
                .put(KEY_DRAWABLE_NAME, override.drawableName),
        )
        persist(context, root)
    }

    @JvmStatic
    fun clearOverride(context: Context, componentName: ComponentName) {
        val root = readRoot(context)
        root.remove(componentName.flattenToString())
        persist(context, root)
    }

    private fun readRoot(context: Context): JSONObject {
        val raw = Settings.Secure.getString(context.contentResolver, PREF_ICON_OVERRIDES) ?: "{}"
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun persist(context: Context, root: JSONObject) {
        Settings.Secure.putString(context.contentResolver, PREF_ICON_OVERRIDES, root.toString())
    }
}
