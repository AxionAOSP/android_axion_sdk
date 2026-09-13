package com.android.axion.iconprovider

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.ArrayMap
import android.util.Log
import com.android.axion.iconloader.ThemedIconItem
import com.android.axion.iconprovider.customicon.IconPackDrawableResolver
import com.android.axion.iconprovider.customicon.IconPackPreferenceStore
import org.xmlpull.v1.XmlPullParser

object ThemedIconPackLoader {
    private const val TAG = "ThemedIconPackLoader"
    private const val TAG_ICON = "icon"
    private const val ATTR_PACKAGE = "package"
    private const val ATTR_DRAWABLE = "drawable"

    private val drawableNameMap = ArrayMap<String, String>()
    private val themedItemCache = ArrayMap<String, ThemedIconItem>()
    private var packRes: Resources? = null
    private var loadedThemedIconPack: String? = null

    @Synchronized
    @JvmStatic
    fun getExternalThemedIconItem(context: Context, packageName: String): ThemedIconItem? {
        val currentPack = IconPackPreferenceStore.getThemedIconPackPackage(context)
        val packChanged = (currentPack == null && loadedThemedIconPack != null) ||
            (currentPack != null && currentPack != loadedThemedIconPack)
        if (packChanged || packRes == null) {
            drawableNameMap.clear()
            themedItemCache.clear()
            loadedThemedIconPack = currentPack
            packRes = loadExternalThemedIconPack(context, drawableNameMap, themedItemCache, currentPack)
        }
        val cached = themedItemCache[packageName]
        if (cached != null) {
            return cached
        }
        val res = packRes ?: return null
        val pack = loadedThemedIconPack.takeUnless { it.isNullOrEmpty() } ?: return null
        val drawableName = drawableNameMap[packageName] ?: return null
        val resId = resolveDrawableId(res, pack, drawableName)
        if (resId == 0) {
            return null
        }
        val item = ThemedIconItem(res, resId)
        themedItemCache[packageName] = item
        return item
    }

    @Synchronized
    @JvmStatic
    fun clearCache() {
        drawableNameMap.clear()
        themedItemCache.clear()
        packRes = null
        loadedThemedIconPack = null
    }

    private fun resolveDrawableId(res: Resources, packPackage: String, name: String): Int {
        val id = IconPackDrawableResolver.getDrawableId(res, packPackage, "${name}_foreground")
        if (id != 0) {
            return id
        }
        return IconPackDrawableResolver.getDrawableId(res, packPackage, name)
    }

    private fun loadExternalThemedIconPack(
        context: Context,
        nameMap: ArrayMap<String, String>,
        itemMap: ArrayMap<String, ThemedIconItem>,
        packPackage: String?,
    ): Resources? {
        if (packPackage.isNullOrEmpty()) {
            return null
        }
        val res = try {
            context.packageManager.getResourcesForApplication(packPackage)
        } catch (_: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Themed icon pack not found: $packPackage")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load themed icon pack: $packPackage", e)
            return null
        }
        val mapResId = res.getIdentifier("grayscale_icon_map", "xml", packPackage)
        if (mapResId != 0) {
            loadThemedIconMapFromResource(itemMap, res, mapResId)
            return res
        }
        val filterResId = res.getIdentifier("appfilter", "xml", packPackage)
        if (filterResId != 0) {
            loadThemedIconMapFromAppFilter(nameMap, res, filterResId)
            return res
        }
        return null
    }

    private fun loadThemedIconMapFromResource(
        map: ArrayMap<String, ThemedIconItem>,
        packRes: Resources,
        resId: Int,
    ) {
        try {
            packRes.getXml(resId).use { parser ->
                val depth = parser.depth
                var type: Int
                while (parser.next().also { type = it } != XmlPullParser.START_TAG &&
                    type != XmlPullParser.END_DOCUMENT) {
                }
                while ((parser.next().also { type = it } != XmlPullParser.END_TAG ||
                    parser.depth > depth) && type != XmlPullParser.END_DOCUMENT) {
                    if (type != XmlPullParser.START_TAG) {
                        continue
                    }
                    if (TAG_ICON == parser.name) {
                        val pkg = parser.getAttributeValue(null, ATTR_PACKAGE)
                        val iconId = parser.getAttributeResourceValue(null, ATTR_DRAWABLE, 0)
                        if (iconId != 0 && !pkg.isNullOrEmpty()) {
                            map[pkg] = ThemedIconItem(packRes, iconId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse themed icon map", e)
        }
    }

    private fun loadThemedIconMapFromAppFilter(
        map: ArrayMap<String, String>,
        packRes: Resources,
        resId: Int,
    ) {
        try {
            packRes.getXml(resId).use { parser ->
                var type: Int
                while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT) {
                    if (type != XmlPullParser.START_TAG || parser.name != "item") {
                        continue
                    }
                    val component = parser.getAttributeValue(null, "component")
                    val drawableName = parser.getAttributeValue(null, ATTR_DRAWABLE)
                    if (component.isNullOrEmpty() || drawableName.isNullOrEmpty()) {
                        continue
                    }
                    val pkg = extractPackageFromComponent(component)
                    if (pkg.isNullOrEmpty() || map.containsKey(pkg)) {
                        continue
                    }
                    map[pkg] = drawableName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse themed appfilter", e)
        }
    }

    private fun extractPackageFromComponent(component: String): String? {
        if (component.startsWith("ComponentInfo{") && component.endsWith("}")) {
            val inner = component.substring(14, component.length - 1)
            val slash = inner.indexOf('/')
            return if (slash > 0) inner.substring(0, slash) else null
        }
        return null
    }
}
