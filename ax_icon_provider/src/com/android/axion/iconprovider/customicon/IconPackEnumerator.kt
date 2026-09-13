package com.android.axion.iconprovider.customicon

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.Log
import com.android.axion.util.PackageManagerUtils
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

object IconPackEnumerator {
    private const val TAG = "IconPackEnumerator"
    private const val XML_APPFILTER = "appfilter"
    private const val TAG_ITEM = "item"
    private const val ATTR_COMPONENT = "component"
    private const val ATTR_DRAWABLE = "drawable"

    private val ICON_PACK_ACTIONS = listOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.dlto.atom.launcher.THEME",
    )

    suspend fun listInstalledIconPacks(context: Context): List<IconPackInfo> =
        withContext(Dispatchers.IO) { listInstalledIconPacksBlocking(context) }

    fun listInstalledIconPacksBlocking(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val packages = LinkedHashSet<String>()
        ICON_PACK_ACTIONS.forEach { action ->
            pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
                .forEach { packages.add(it.activityInfo.packageName) }
        }
        pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { app ->
            if (hasAppFilter(context, app.packageName)) {
                packages.add(app.packageName)
            }
        }
        return packages.mapNotNull { packageName ->
            val appInfo = PackageManagerUtils.getApplicationInfo(context, packageName)
                ?: return@mapNotNull null
            IconPackInfo(
                packageName = packageName,
                label = PackageManagerUtils.loadApplicationLabel(pm, appInfo).toString(),
                icon = PackageManagerUtils.loadApplicationIcon(pm, appInfo),
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun parseComponentMap(context: Context, packPackage: String): Map<ComponentName, String> {
        val res = getResources(context, packPackage) ?: return emptyMap()
        val xmlId = res.getIdentifier(XML_APPFILTER, "xml", packPackage)
        if (xmlId == 0) return emptyMap()

        val map = HashMap<ComponentName, String>()
        try {
            val parser = res.getXml(xmlId)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == TAG_ITEM) {
                    val component = parser.getAttributeValue(null, ATTR_COMPONENT)
                    val drawable = parser.getAttributeValue(null, ATTR_DRAWABLE)
                    if (!component.isNullOrEmpty() && !drawable.isNullOrEmpty()) {
                        parseComponent(component)?.let { cn ->
                            map[cn] = drawable
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            Log.w(TAG, "Failed parsing $XML_APPFILTER for $packPackage", e)
        } catch (e: IOException) {
            Log.w(TAG, "Failed reading $XML_APPFILTER for $packPackage", e)
        }
        return map
    }

    suspend fun listDrawables(context: Context, packPackage: String): List<IconPackDrawableInfo> =
        withContext(Dispatchers.IO) { getDrawablesForPackage(context, packPackage) }

    fun getDrawablesForPackage(context: Context, packPackage: String): List<IconPackDrawableInfo> {
        val res = getResources(context, packPackage) ?: return emptyList()
        val xmlId = res.getIdentifier(XML_APPFILTER, "xml", packPackage)
        if (xmlId == 0) return emptyList()

        val seen = HashSet<String>()
        val result = mutableListOf<IconPackDrawableInfo>()
        try {
            val parser = res.getXml(xmlId)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == TAG_ITEM) {
                    val drawable = parser.getAttributeValue(null, ATTR_DRAWABLE)
                    if (!drawable.isNullOrEmpty() && seen.add(drawable)) {
                        val label = drawable.replace('_', ' ')
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        result.add(IconPackDrawableInfo(packPackage, drawable, label))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            Log.w(TAG, "Failed to parse appfilter for $packPackage", e)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read appfilter for $packPackage", e)
        }
        return result.sortedBy { it.label.lowercase() }
    }

    fun getResources(context: Context, packPackage: String): Resources? =
        PackageManagerUtils.getResources(context, packPackage)

    private fun hasAppFilter(context: Context, packageName: String): Boolean {
        val res = PackageManagerUtils.getResources(context, packageName) ?: return false
        return res.getIdentifier(XML_APPFILTER, "xml", packageName) != 0
    }

    private fun parseComponent(raw: String): ComponentName? {
        val trimmed = raw.removePrefix("ComponentInfo{").removeSuffix("}")
        val parts = trimmed.split("/")
        if (parts.size != 2) return null
        val pkg = parts[0]
        val cls = if (parts[1].startsWith(".")) "$pkg${parts[1]}" else parts[1]
        return ComponentName(pkg, cls)
    }
}
