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

package com.android.systemui.qs.ax.ui.carrier

import android.annotation.ColorInt
import android.annotation.StyleRes
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.buildSpec
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.lifecycle.repeatWhenWindowIsVisible
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarIconView.STATE_ICON
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.pipeline.mobile.ui.MobileViewLogger
import com.android.systemui.statusbar.pipeline.mobile.ui.binder.MobileIconBinder
import com.android.systemui.statusbar.pipeline.mobile.ui.binder.MobileIconBinderKairos
import com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.ShadeCarrierGroupMobileIconViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.ShadeCarrierGroupMobileIconViewModelKairos
import com.android.systemui.util.AutoMarqueeTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

interface AxModernShadeCarrierGroupMobileViewBinding {
    fun setStyleAndTint(@StyleRes style: Int, fgColor: Int, bgColor: Int)
}

interface AxShadeCarrierBinding {
    fun setTextAppearance(@StyleRes resId: Int)
    fun setTextColor(@ColorInt color: Int)
}

object AxShadeCarrierBinder {
    fun bind(
        carrierTextView: AutoMarqueeTextView,
        viewModel: ShadeCarrierGroupMobileIconViewModel,
    ): AxShadeCarrierBinding {
        carrierTextView.isVisible = true
        carrierTextView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.carrierName.collect {
                        carrierTextView.text = it
                    }
                }
            }
        }

        return object : AxShadeCarrierBinding {
            override fun setTextAppearance(resId: Int) {
                carrierTextView.setTextAppearance(resId)
            }

            override fun setTextColor(color: Int) {
                carrierTextView.setTextColor(color)
            }
        }
    }

    @ExperimentalKairosApi
    fun bindKairos(
        subId: Int,
        carrierTextView: AutoMarqueeTextView,
        viewModel: com.android.systemui.kairos.BuildSpec<ShadeCarrierGroupMobileIconViewModelKairos>,
        kairosNetwork: KairosNetwork,
        scope: CoroutineScope,
    ): Pair<AxShadeCarrierBinding, Job> {
        carrierTextView.isVisible = true
        val job =
            scope.launch {
                carrierTextView.repeatWhenWindowIsVisible {
                    kairosNetwork.activateSpec(
                        nameTag { "AxShadeCarrierBinderKairos(subId=$subId).bind" }
                    ) {
                        viewModel.applySpec().carrierName.observe(
                            name = nameTag { "AxShadeCarrierBinderKairos(subId=$subId).carrierName" }
                        ) {
                            carrierTextView.text = it
                        }
                    }
                }
            }

        val binding =
            object : AxShadeCarrierBinding {
                override fun setTextAppearance(resId: Int) {
                    carrierTextView.setTextAppearance(resId)
                }

                override fun setTextColor(color: Int) {
                    carrierTextView.setTextColor(color)
                }
            }
        return binding to job
    }
}

/**
 * Axion variant of ViewGroup containing a mobile carrier name and icon in the Shade Header.
 */
class AxModernShadeCarrierGroupMobileView(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {

    var subId: Int = -1

    private lateinit var binding: AxModernShadeCarrierGroupMobileViewBinding
    private var pendingStyleResId: Int? = null
    private var pendingFgColor = 0
    private var pendingBgColor = 0

    fun setStyleAndTint(@StyleRes styleResId: Int, fgColor: Int, bgColor: Int) {
        pendingStyleResId = styleResId
        pendingFgColor = fgColor
        pendingBgColor = bgColor
        if (!::binding.isInitialized) {
            return
        }
        applyStyleAndTint(styleResId, fgColor, bgColor)
    }

    private fun applyStyleAndTint(@StyleRes styleResId: Int, fgColor: Int, bgColor: Int) {
        binding.setStyleAndTint(style = styleResId, fgColor = fgColor, bgColor = bgColor)
    }

    private fun applyPendingStyleAndTint() {
        pendingStyleResId?.let { applyStyleAndTint(it, pendingFgColor, pendingBgColor) }
    }

    override fun toString(): String {
        return "AxModernShadeCarrierGroupMobileView(" +
            "subId=$subId, " +
            "viewString=${super.toString()}"
    }

    companion object {
        @JvmStatic
        fun constructAndBind(
            context: Context,
            logger: MobileViewLogger,
            slot: String,
            viewModel: ShadeCarrierGroupMobileIconViewModel,
        ): AxModernShadeCarrierGroupMobileView {
            return (LayoutInflater.from(context).inflate(R.layout.shade_carrier_new, null)
                    as AxModernShadeCarrierGroupMobileView)
                .apply {
                    subId = viewModel.subscriptionId

                    val iconView = requireViewById<ModernStatusBarMobileView>(R.id.mobile_combo)
                    if (NewStatusBarIcons.isEnabled) {
                        iconView.configureLayoutForNewStatusBarIcons()
                    }
                    iconView.initView(slot) {
                        MobileIconBinder.bind(iconView, viewModel, STATE_ICON, logger)
                    }
                    logger.logNewViewBinding(this, viewModel)

                    val textView = requireViewById<AutoMarqueeTextView>(R.id.mobile_carrier_text)
                    val shadeCarrierBinding = AxShadeCarrierBinder.bind(textView, viewModel)

                    binding =
                        object : AxModernShadeCarrierGroupMobileViewBinding {
                            override fun setStyleAndTint(
                                @StyleRes style: Int,
                                fgColor: Int,
                                bgColor: Int,
                            ) {
                                iconView.setStaticDrawableColor(fgColor, bgColor)
                                shadeCarrierBinding.setTextAppearance(style)
                                shadeCarrierBinding.setTextColor(fgColor)
                            }
                        }
                    applyPendingStyleAndTint()
                }
        }

        @ExperimentalKairosApi
        @JvmStatic
        fun constructAndBindKairos(
            context: Context,
            logger: MobileViewLogger,
            slot: String,
            viewModel: ShadeCarrierGroupMobileIconViewModelKairos,
            scope: CoroutineScope,
            subscriptionId: Int,
            location: StatusBarLocation,
            kairosNetwork: KairosNetwork,
        ): Pair<AxModernShadeCarrierGroupMobileView, Job> {
            val view =
                (LayoutInflater.from(context).inflate(R.layout.shade_carrier_new, null)
                        as AxModernShadeCarrierGroupMobileView)
                    .apply {
                        subId = subscriptionId

                        val iconView = requireViewById<ModernStatusBarMobileView>(R.id.mobile_combo)
                        if (NewStatusBarIcons.isEnabled) {
                            iconView.configureLayoutForNewStatusBarIcons()
                        }
                    }
            return view to
                scope.launch {
                    val iconView =
                        view.requireViewById<ModernStatusBarMobileView>(R.id.mobile_combo)
                    iconView.initView(slot) {
                        val (binding, _) =
                            MobileIconBinderKairos.bind(
                                view = iconView,
                                viewModel = buildSpec { viewModel },
                                initialVisibilityState = STATE_ICON,
                                logger = logger,
                                scope = this,
                                kairosNetwork = kairosNetwork,
                                subId = subscriptionId,
                            )
                        binding
                    }
                    logger.logNewViewBinding(view, viewModel, location.name)

                    val textView =
                        view.requireViewById<AutoMarqueeTextView>(R.id.mobile_carrier_text)
                    val (shadeCarrierBinding, _) =
                        AxShadeCarrierBinder.bindKairos(
                            subscriptionId,
                            textView,
                            buildSpec { viewModel },
                            kairosNetwork,
                            this,
                        )
                    view.binding =
                        object : AxModernShadeCarrierGroupMobileViewBinding {
                            override fun setStyleAndTint(style: Int, fgColor: Int, bgColor: Int) {
                                iconView.setStaticDrawableColor(fgColor, bgColor)
                                shadeCarrierBinding.setTextAppearance(style)
                                shadeCarrierBinding.setTextColor(fgColor)
                            }
                        }
                    view.applyPendingStyleAndTint()
                }
        }
    }
}
