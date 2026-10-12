/*
 * Copyright (C) 2025-2026 The Android Open Source Project
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
package com.android.settings.homepage

import android.app.settings.SettingsEnums
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.UserHandle
import android.os.UserManager
import android.text.TextUtils
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import com.android.internal.util.UserIcons
import com.android.settings.R
import com.android.settings.SettingsActivity
import com.android.settings.activityembedding.ActivityEmbeddingRulesController
import com.android.settings.activityembedding.ActivityEmbeddingUtils
import com.android.settings.communal.CommunalPreferenceController
import com.android.settings.core.SubSettingLauncher
import com.android.settings.overlay.FeatureFactory
import com.android.settings.safetycenter.SafetyCenterManagerWrapper
import com.android.settings.ax.res.R as AxSettingsR

class ComposeHomepageController(private val activity: SettingsHomepageActivity) {

    companion object {
        private const val TAG = "ComposeHomepageCtrl"
        private const val WELLBEING_PKG = "com.google.android.apps.wellbeing"
        private const val WELLBEING_ACTIVITY = "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"
        private const val GOOGLE_PKG = "com.google.android.gms"
        private const val GOOGLE_ACTIVITY = "com.google.android.gms.app.settings.GoogleSettingsIALink"

        private const val ABOUT_FRAGMENT_CLASS = "com.android.settings.deviceinfo.axion.AxionAboutFragment"
        private const val PERSONALIZE_FRAGMENT_CLASS = "com.android.settings.personalize.PersonalizeFragment"

        @JvmStatic
        fun isComposeHomepageEnabled(): Boolean = true
    }

    private var userInfoReceiver: BroadcastReceiver? = null

    fun onCreate(highlightMenuKey: String): TopLevelSettings {
        setupEdgeToEdge()

        val root = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val composeView = ComposeView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(composeView)

        val fragmentContainer = FrameLayout(activity).apply {
            id = R.id.main_content
            layoutParams = ViewGroup.LayoutParams(0, 0)
            visibility = android.view.View.GONE
        }
        root.addView(fragmentContainer)

        activity.setContentView(root)

        val fragment = TopLevelSettings().apply {
            arguments?.putString(
                SettingsActivity.EXTRA_FRAGMENT_ARG_KEY,
                highlightMenuKey
            )
        }
        activity.supportFragmentManager
            .beginTransaction()
            .add(R.id.main_content, fragment)
            .commit()

        SettingsHomepageComposeInterop.setContent(
            composeView,
            onSearchClick = Runnable {
                val intent = FeatureFactory.featureFactory.searchFeatureProvider
                    .buildSearchIntent(activity, SettingsEnums.SETTINGS_HOMEPAGE)
                activity.startActivity(intent)
            },
            onAvatarClick = Runnable {
                val intent = Intent(android.provider.Settings.ACTION_USER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            },
            onPreferenceClick = java.util.function.Consumer { key ->
                handlePreferenceClick(key, fragment)
            },
            userName = getUserName(),
            avatarBitmap = getAvatarBitmap(),
            isCommunalAvailable = CommunalPreferenceController.isAvailable(activity),
            isSafetyCenterAvailable = SafetyCenterManagerWrapper.get().isEnabled(activity),
            isEmergencyAvailable = activity.resources.getBoolean(R.bool.config_show_emergency_settings),
            isSupportAvailable = FeatureFactory.featureFactory.supportFeatureProvider != null,
            isWellbeingAvailable = isActivityResolvable(WELLBEING_PKG, WELLBEING_ACTIVITY),
            isGoogleAvailable = isActivityResolvable(GOOGLE_PKG, GOOGLE_ACTIVITY)
        )

        return fragment
    }

    fun onStart() {
        registerUserInfoReceiver()
    }

    fun onResume() {
        SettingsHomepageComposeInterop.updateUserInfo(getUserName(), getAvatarBitmap())
    }

    fun onStop() {
        unregisterUserInfoReceiver()
    }

    private fun handlePreferenceClick(key: String, fragment: TopLevelSettings) {
        when (key) {
            "personalize", "axion_hub" -> { launchPersonalizeFragment(fragment); return }
            "axion_themepicker" -> { launchWallpaperAndStyles(); return }
            "top_level_wellbeing" -> { launchExternalSettings(WELLBEING_PKG, WELLBEING_ACTIVITY); return }
            "top_level_google" -> { launchExternalSettings(GOOGLE_PKG, GOOGLE_ACTIVITY); return }
        }
        val screen = fragment.preferenceScreen
        val pref = screen?.findPreference<Preference>(key)
        if (pref != null) {
            fragment.onPreferenceTreeClick(pref)
        } else if (key == "top_level_about_device") {
            launchAboutFragment(fragment)
        } else {
            Log.w(TAG, "Preference not found for key: $key")
        }
    }

    private fun launchPersonalizeFragment(fragment: TopLevelSettings) {
        ActivityEmbeddingRulesController.registerSubSettingsPairRule(activity, true /* clearTop */)
        fragment.setHighlightPreferenceKey("personalize")
        SubSettingLauncher(activity)
            .setDestination(PERSONALIZE_FRAGMENT_CLASS)
            .setSourceMetricsCategory(SettingsEnums.DASHBOARD_SUMMARY)
            .setTitleRes(AxSettingsR.string.axion_hub_title)
            .setIsSecondLayerPage(true)
            .launch()
    }

    private fun launchAboutFragment(fragment: TopLevelSettings) {
        ActivityEmbeddingRulesController.registerSubSettingsPairRule(activity, true /* clearTop */)
        fragment.setHighlightPreferenceKey("top_level_about_device")
        SubSettingLauncher(activity)
            .setDestination(ABOUT_FRAGMENT_CLASS)
            .setSourceMetricsCategory(SettingsEnums.DASHBOARD_SUMMARY)
            .setTitleRes(R.string.about_settings)
            .setIsSecondLayerPage(true)
            .launch()
    }

    private fun launchWallpaperAndStyles() {
        try {
            val intent = Intent()
                .setClassName("com.android.axion.themepicker", "com.android.axion.themepicker.ui.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("is_launch_extra", true)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch wallpaper and styles", e)
        }
    }

    private fun launchExternalSettings(pkg: String, cls: String) {
        try {
            activity.startActivity(
                Intent()
                    .setClassName(pkg, cls)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch $pkg/$cls", e)
        }
    }

    private fun isActivityResolvable(pkg: String, cls: String): Boolean {
        return try {
            activity.packageManager.getActivityInfo(
                ComponentName(pkg, cls),
                PackageManager.MATCH_DEFAULT_ONLY
            )
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        ViewCompat.setOnApplyWindowInsetsListener(
            activity.findViewById(android.R.id.content)
        ) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            windowInsets
        }
    }

    private fun getUserName(): String {
        return try {
            activity.getSystemService(UserManager::class.java)?.userName ?: "User"
        } catch (e: Exception) {
            "User"
        }
    }

    private fun getAvatarBitmap(): Bitmap {
        val userManager = activity.getSystemService(UserManager::class.java)
        var bitmap = userManager?.getUserIcon(UserHandle.myUserId())
        if (bitmap == null) {
            val defaultIcon = UserIcons.getDefaultUserIcon(
                activity.resources, UserHandle.myUserId(), false
            )
            bitmap = UserIcons.convertToBitmap(defaultIcon)
        }
        return bitmap
    }

    private fun registerUserInfoReceiver() {
        if (userInfoReceiver != null) return
        userInfoReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                SettingsHomepageComposeInterop.updateUserInfo(getUserName(), getAvatarBitmap())
            }
        }
        activity.registerReceiver(
            userInfoReceiver,
            IntentFilter(Intent.ACTION_USER_INFO_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterUserInfoReceiver() {
        userInfoReceiver?.let {
            activity.unregisterReceiver(it)
            userInfoReceiver = null
        }
    }
}
