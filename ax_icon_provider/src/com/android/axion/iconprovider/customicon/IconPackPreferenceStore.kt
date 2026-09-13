package com.android.axion.iconprovider.customicon

import android.content.Context
import android.provider.Settings
import com.android.axion.iconprovider.ThemedIconPackLoader
import org.json.JSONObject

object IconPackPreferenceStore {
    private const val KEY_THEME_ENGINE_DATA = "theme_engine_data"
    private const val THEME_ROOT = "themes"
    private const val CATEGORY_ICON_PACK = "icon_pack"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PACKAGE_NAME = "packageName"
    private const val PREF_ICON_PACK_PACKAGE = "pref_icon_pack_package"
    private const val PREF_THEMED_ICON_PACK = "themed_icon_pack"
    private const val PREF_ICON_OVERRIDES = "launcher_icon_overrides"

    @Volatile
    private var cachedThemedIconPack: String? = null

    @Volatile
    private var cachedIconPack: String? = null

    @JvmStatic
    fun getIconPackPackage(context: Context): String {
        cachedIconPack?.let { return it }
        val pack = readThemeEngineIconPack(context)
            ?: Settings.Secure.getString(context.contentResolver, PREF_ICON_PACK_PACKAGE)
            ?: ""
        cachedIconPack = pack
        return pack
    }

    @JvmStatic
    fun setIconPackPackage(context: Context, packageName: String) {
        cachedIconPack = packageName
        Settings.Secure.putString(context.contentResolver, PREF_ICON_PACK_PACKAGE, packageName)
        writeThemeEngineIconPack(context, packageName)
        IconPackDrawableResolver.clearCache(packageName.takeIf { it.isNotEmpty() })
    }

    @JvmStatic
    fun getThemedIconPackPackage(context: Context): String {
        cachedThemedIconPack?.let { return it }
        val pack = Settings.Secure.getString(context.contentResolver, PREF_THEMED_ICON_PACK) ?: ""
        cachedThemedIconPack = pack
        return pack
    }

    @JvmStatic
    fun setThemedIconPackPackage(context: Context, packageName: String) {
        cachedThemedIconPack = packageName
        Settings.Secure.putString(context.contentResolver, PREF_THEMED_ICON_PACK, packageName)
        ThemedIconPackLoader.clearCache()
    }

    @JvmStatic
    fun clearCache() {
        cachedIconPack = null
        cachedThemedIconPack = null
    }

    @JvmStatic
    fun hasActiveIconPack(context: Context): Boolean {
        return getIconPackPackage(context).isNotEmpty()
    }

    @JvmStatic
    fun hasAnyIconCustomization(context: Context): Boolean {
        return hasActiveIconPack(context) || hasIconOverrides(context)
    }

    @JvmStatic
    fun hasIconOverrides(context: Context): Boolean {
        val overrides = Settings.Secure.getString(context.contentResolver, PREF_ICON_OVERRIDES) ?: "{}"
        return overrides.length > 2
    }

    private fun readThemeEngineIconPack(context: Context): String? {
        val json = Settings.Secure.getString(context.contentResolver, KEY_THEME_ENGINE_DATA)
            ?: return null
        return runCatching {
            val config = JSONObject(json)
            val themes = config.optJSONObject(THEME_ROOT) ?: return null
            val iconPack = themes.optJSONObject(CATEGORY_ICON_PACK) ?: return null
            val enabled = iconPack.optBoolean(KEY_ENABLED, false)
            val packageName = iconPack.optString(KEY_PACKAGE_NAME, "")
            if (enabled && packageName.isNotEmpty()) packageName else ""
        }.getOrNull()
    }

    private fun writeThemeEngineIconPack(context: Context, packageName: String) {
        val resolver = context.contentResolver
        val json = Settings.Secure.getString(resolver, KEY_THEME_ENGINE_DATA)
        val config = runCatching {
            if (json.isNullOrEmpty()) JSONObject() else JSONObject(json)
        }.getOrDefault(JSONObject())

        val themes = config.optJSONObject(THEME_ROOT) ?: JSONObject().also {
            config.put(THEME_ROOT, it)
        }
        val iconPack = themes.optJSONObject(CATEGORY_ICON_PACK) ?: JSONObject().also {
            themes.put(CATEGORY_ICON_PACK, it)
        }

        iconPack.put(KEY_PACKAGE_NAME, packageName)
        iconPack.put(KEY_ENABLED, packageName.isNotEmpty())
        Settings.Secure.putString(resolver, KEY_THEME_ENGINE_DATA, config.toString())
    }
}
