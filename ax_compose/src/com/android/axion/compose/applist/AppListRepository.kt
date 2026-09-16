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

package com.android.axion.compose.applist

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext
import com.android.axion.util.PackageManagerUtils
import com.android.internal.dualapps.AxDualAppsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppFilter {
    USER_ONLY,
    SYSTEM_ONLY,
    ALL,
    NO_OVERLAYS,
    LAUNCHABLE_ONLY,
    INCLUDE_DUAL_APPS,
}

data class AppEntry(
    val packageName: String,
    val className: String,
    val label: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val userId: Int = 0,
    val isClone: Boolean = false,
)

@Composable
fun rememberAppList(vararg filters: AppFilter): State<List<AppEntry>> {
    val context = LocalContext.current
    val pm = context.packageManager
    val filterSet = filters.toSet()
    val state = remember(filterSet) { mutableStateOf<List<AppEntry>>(emptyList()) }

    LaunchedEffect(filterSet) {
        withContext(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val entries = apps.mapNotNull { info ->
                val isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val launchIntent = PackageManagerUtils.getLaunchIntentForPackage(
                    context,
                    info.packageName,
                )

                if (AppFilter.USER_ONLY in filterSet && isSystem) return@mapNotNull null
                if (AppFilter.SYSTEM_ONLY in filterSet && !isSystem) return@mapNotNull null
                if (AppFilter.NO_OVERLAYS in filterSet && info.isResourceOverlay) return@mapNotNull null
                if (AppFilter.LAUNCHABLE_ONLY in filterSet && launchIntent == null) {
                    return@mapNotNull null
                }

                val icon = PackageManagerUtils.loadApplicationIcon(pm, info)
                    ?: return@mapNotNull null
                val className = launchIntent?.component?.className ?: ""
                AppEntry(
                    packageName = info.packageName,
                    className = className,
                    label = PackageManagerUtils.loadApplicationLabel(pm, info).toString(),
                    icon = icon,
                    isSystem = isSystem,
                    userId = 0,
                    isClone = false,
                )
            }

            val dualEntries = mutableListOf<AppEntry>()
            if (AppFilter.INCLUDE_DUAL_APPS in filterSet) {
                try {
                    val dualManager = AxDualAppsManager.getInstance()
                    if (dualManager != null && dualManager.isDualSpaceExists) {
                        val dualUserId = AxDualAppsManager.DUAL_APPS_USER_ID
                        val allowedPackages = dualManager.queryAllowPackages()
                        val hiddenPackages = dualManager.queryHiddenPackages().toSet()
                        for (pkg in allowedPackages) {
                            if (pkg in hiddenPackages) continue
                            val baseEntry = entries.find { it.packageName == pkg && it.userId == 0 }
                            if (baseEntry != null) {
                                val badgedIcon = pm.getUserBadgedIcon(baseEntry.icon, UserHandle.of(dualUserId))
                                dualEntries.add(
                                    AppEntry(
                                        packageName = pkg,
                                        className = baseEntry.className,
                                        label = baseEntry.label,
                                        icon = badgedIcon,
                                        isSystem = false,
                                        userId = dualUserId,
                                        isClone = true,
                                    )
                                )
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
            }

            state.value = (entries + dualEntries).sortedBy { it.label.lowercase() }
        }
    }

    return state
}

@Composable
fun rememberFilteredAppList(query: String, vararg filters: AppFilter): State<List<AppEntry>> {
    val allApps = rememberAppList(*filters)
    return remember(query, allApps) {
        derivedStateOf {
            if (query.isBlank()) {
                allApps.value
            } else {
                val lower = query.lowercase()
                allApps.value.filter { entry ->
                    entry.label.lowercase().contains(lower) ||
                        entry.packageName.lowercase().contains(lower)
                }
            }
        }
    }
}
