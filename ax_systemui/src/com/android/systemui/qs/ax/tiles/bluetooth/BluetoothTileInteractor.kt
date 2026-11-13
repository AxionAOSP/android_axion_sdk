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

package com.android.systemui.qs.ax.tiles.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.android.settingslib.Utils
import com.android.settingslib.bluetooth.BluetoothCallback
import com.android.settingslib.bluetooth.BluetoothUtils
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.internal.R as InternalR
import com.android.settingslib.bluetooth.HapClientProfile
import com.android.settingslib.bluetooth.HearingAidProfile
import com.android.settingslib.bluetooth.HidProfile
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.policy.BluetoothController
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

@Immutable
data class AxBluetoothDeviceModel(
    val address: String,
    val name: String,
    val secondaryLabel: String,
    val productImage: ImageBitmap?,
    val categoryIcon: Icon?,
    val isActiveAudio: Boolean,
)

@Immutable
data class AxBluetoothTileSnapshot(
    val enabled: Boolean = false,
    val connecting: Boolean = false,
    val connectedDevices: List<AxBluetoothDeviceModel> = emptyList(),
) {
    val hasConnectedDevices: Boolean
        get() = enabled && !connecting && connectedDevices.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class BluetoothTileInteractor
@Inject
constructor(
    private val bluetoothController: BluetoothController,
    private val localBluetoothManager: LocalBluetoothManager?,
    private val artLoader: BluetoothDeviceArtLoader,
    @param:Application private val applicationScope: CoroutineScope,
    @param:Background private val backgroundDispatcher: CoroutineDispatcher,
    @param:Background private val backgroundExecutor: Executor,
) {
    private val controllerSignals: Flow<Unit> =
        conflatedCallbackFlow {
                val controllerCallback =
                    object : BluetoothController.Callback {
                        override fun onBluetoothStateChange(enabled: Boolean) {
                            trySend(Unit)
                        }

                        override fun onBluetoothDevicesChanged() {
                            trySend(Unit)
                        }
                    }
                val btCallback =
                    object : BluetoothCallback {
                        override fun onActiveDeviceChanged(
                            activeDevice: CachedBluetoothDevice?,
                            bluetoothProfile: Int,
                        ) {
                            trySend(Unit)
                        }

                        override fun onProfileConnectionStateChanged(
                            cachedDevice: CachedBluetoothDevice,
                            state: Int,
                            bluetoothProfile: Int,
                        ) {
                            trySend(Unit)
                        }

                        override fun onAclConnectionStateChanged(
                            cachedDevice: CachedBluetoothDevice,
                            state: Int,
                        ) {
                            trySend(Unit)
                        }
                    }

                bluetoothController.addCallback(controllerCallback)
                localBluetoothManager?.eventManager?.registerCallback(btCallback)
                trySend(Unit)

                awaitClose {
                    bluetoothController.removeCallback(controllerCallback)
                    localBluetoothManager?.eventManager?.unregisterCallback(btCallback)
                }
            }

    val snapshot: StateFlow<AxBluetoothTileSnapshot> =
        controllerSignals
            .flatMapLatest {
                val enabled = bluetoothController.isBluetoothEnabled
                val connecting = bluetoothController.isBluetoothConnecting
                val devices =
                    if (enabled) {
                        bluetoothController.connectedDevices
                            ?.filterNotNull()
                            ?.distinctBy { it.address }
                            .orEmpty()
                    } else {
                        emptyList()
                    }
                if (devices.isEmpty()) {
                    flowOf(AxBluetoothTileSnapshot(enabled = enabled, connecting = connecting))
                } else {
                    observeConnectedDevices(devices).transformLatest {
                        val currentEnabled = bluetoothController.isBluetoothEnabled
                        val currentConnecting = bluetoothController.isBluetoothConnecting
                        if (!currentEnabled) {
                            emit(AxBluetoothTileSnapshot(enabled = false, connecting = false))
                            return@transformLatest
                        }

                        val resolved = buildDeviceEntries(devices)
                        emit(
                            AxBluetoothTileSnapshot(
                                enabled = currentEnabled,
                                connecting = currentConnecting,
                                connectedDevices = resolved.map { it.model },
                            )
                        )

                        val pendingUris = resolved.mapNotNull { entry ->
                            entry.pendingIconUri?.let { uri -> entry.model.address to uri }
                        }
                        if (pendingUris.isNotEmpty()) {
                            val loadedImages = buildMap {
                                for ((address, uri) in pendingUris) {
                                    artLoader.load(uri)?.let { put(address, it) }
                                }
                            }
                            if (loadedImages.isNotEmpty()) {
                                emit(
                                    AxBluetoothTileSnapshot(
                                        enabled = bluetoothController.isBluetoothEnabled,
                                        connecting = bluetoothController.isBluetoothConnecting,
                                        connectedDevices =
                                            resolved.map { entry ->
                                                val img = loadedImages[entry.model.address]
                                                if (img != null) {
                                                    entry.model.copy(productImage = img)
                                                } else {
                                                    entry.model
                                                }
                                            },
                                    )
                                )
                            }
                        }
                    }
                }
            }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
                initialValue = AxBluetoothTileSnapshot(),
            )

    private fun observeConnectedDevices(devices: List<CachedBluetoothDevice>): Flow<Unit> =
        conflatedCallbackFlow {
                val attrCallbacks = ArrayList<Pair<CachedBluetoothDevice, CachedBluetoothDevice.Callback>>(devices.size)
                val metadataListeners =
                    ArrayList<Pair<CachedBluetoothDevice, BluetoothAdapter.OnMetadataChangedListener>>(devices.size)

                for (device in devices) {
                    val attrCb = CachedBluetoothDevice.Callback { trySend(Unit) }
                    runCatching {
                        device.registerCallback(backgroundExecutor, attrCb)
                        attrCallbacks.add(device to attrCb)
                    }

                    val metaCb =
                        BluetoothAdapter.OnMetadataChangedListener { _, key, value ->
                            when (key) {
                                BluetoothDevice.METADATA_MAIN_BATTERY,
                                BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY,
                                BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY,
                                BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY -> {
                                    trySend(Unit)
                                }
                                BluetoothDevice.METADATA_MAIN_ICON -> {
                                    if (value != null) {
                                        artLoader.evict(String(value))
                                    }
                                    val currentUri =
                                        runCatching {
                                                BluetoothUtils.getUriMetaData(
                                                    device.device,
                                                    BluetoothDevice.METADATA_MAIN_ICON,
                                                )
                                            }
                                            .getOrNull()
                                    artLoader.evict(currentUri?.toString())
                                    trySend(Unit)
                                }
                            }
                        }
                    runCatching {
                        bluetoothController.addOnMetadataChangedListener(
                            device,
                            backgroundExecutor,
                            metaCb,
                        )
                        metadataListeners.add(device to metaCb)
                    }
                }
                trySend(Unit)

                awaitClose {
                    for ((device, attrCb) in attrCallbacks) {
                        runCatching { device.unregisterCallback(attrCb) }
                    }
                    for ((device, metaCb) in metadataListeners) {
                        runCatching {
                            bluetoothController.removeOnMetadataChangedListener(device, metaCb)
                        }
                    }
                }
            }

    private data class ResolvedDeviceEntry(
        val model: AxBluetoothDeviceModel,
        val pendingIconUri: Uri?,
    )

    private suspend fun buildDeviceEntries(
        devices: List<CachedBluetoothDevice>
    ): List<ResolvedDeviceEntry> =
        withContext(backgroundDispatcher) {
            val activeSorted =
                devices
                    .filter { runCatching { it.isConnected }.getOrDefault(false) }
                    .sortedByDescending { device ->
                        runCatching { BluetoothUtils.isActiveMediaDevice(device) }.getOrDefault(false)
                    }

            activeSorted.map { device ->
                val address = runCatching { device.address.orEmpty() }.getOrDefault("")
                val name =
                    runCatching { device.name?.takeIf { it.isNotBlank() } }.getOrNull()
                        ?: address
                val isActiveAudio =
                    runCatching { BluetoothUtils.isActiveMediaDevice(device) }.getOrDefault(false)
                val secondary = resolveSecondaryLabel(device)
                val iconUri =
                    runCatching {
                            BluetoothUtils.getUriMetaData(
                                device.device,
                                BluetoothDevice.METADATA_MAIN_ICON,
                            )
                        }
                        .getOrNull()
                val cachedBitmap = artLoader.getCached(iconUri?.toString())
                val categoryIcon =
                    runCatching { resolveBtClassIconRes(device) }
                        .getOrNull()
                        ?.let { resId -> Icon.Resource(resId, contentDescription = null) }

                ResolvedDeviceEntry(
                    model =
                        AxBluetoothDeviceModel(
                            address = address,
                            name = name,
                            secondaryLabel = secondary,
                            productImage = cachedBitmap,
                            categoryIcon = categoryIcon,
                            isActiveAudio = isActiveAudio,
                        ),
                    pendingIconUri = if (cachedBitmap == null) iconUri else null,
                )
            }
        }

    private fun resolveSecondaryLabel(device: CachedBluetoothDevice): String {
        val batteryInfo = runCatching { device.batteryLevelsInfo }.getOrNull()
        val btDevice = runCatching { device.device }.getOrNull()

        val left =
            validPercentage(batteryInfo?.leftBatteryLevel)
                ?: btDevice?.let {
                    validPercentage(
                        runCatching {
                                BluetoothUtils.getIntMetaData(
                                    it,
                                    BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY,
                                )
                            }
                            .getOrNull()
                    )
                }
        val right =
            validPercentage(batteryInfo?.rightBatteryLevel)
                ?: btDevice?.let {
                    validPercentage(
                        runCatching {
                                BluetoothUtils.getIntMetaData(
                                    it,
                                    BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY,
                                )
                            }
                            .getOrNull()
                    )
                }
        val caseLevel =
            validPercentage(batteryInfo?.caseBatteryLevel)
                ?: btDevice?.let {
                    validPercentage(
                        runCatching {
                                BluetoothUtils.getIntMetaData(
                                    it,
                                    BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY,
                                )
                            }
                            .getOrNull()
                    )
                }

        val multiBattery = formatMultiBattery(left, right, caseLevel)
        if (multiBattery != null) return multiBattery

        val overall =
            validPercentage(batteryInfo?.overallBatteryLevel)
                ?: btDevice?.let {
                    validPercentage(
                        runCatching {
                                BluetoothUtils.getIntMetaData(
                                    it,
                                    BluetoothDevice.METADATA_MAIN_BATTERY,
                                )
                            }
                            .getOrNull()
                    )
                }
                ?: validPercentage(runCatching { device.minBatteryLevelWithMemberDevices }.getOrNull())

        if (overall != null) {
            return Utils.formatPercentage(overall)
        }

        return runCatching { device.connectionSummary?.takeIf { it.isNotBlank() } }
            .getOrNull()
            .orEmpty()
    }

    @DrawableRes
    private fun resolveBtClassIconRes(cachedDevice: CachedBluetoothDevice): Int {
        val btClass = cachedDevice.btClass
        if (btClass != null) {
            when (btClass.majorDeviceClass) {
                BluetoothClass.Device.Major.COMPUTER -> return InternalR.drawable.ic_bt_laptop
                BluetoothClass.Device.Major.PHONE -> return InternalR.drawable.ic_phone
                BluetoothClass.Device.Major.PERIPHERAL -> {
                    val hidRes = HidProfile.getHidClassDrawable(btClass)
                    if (hidRes != 0) return hidRes
                }
                BluetoothClass.Device.Major.IMAGING -> return InternalR.drawable.ic_settings_print
            }
        }

        if (cachedDevice.isHearingDevice) {
            return InternalR.drawable.ic_bt_hearing_aid
        }

        var profileFallbackRes = 0
        for (profile in cachedDevice.profiles) {
            val resId = profile.getDrawableResource(btClass)
            if (resId != 0) {
                if (profile is HearingAidProfile || profile is HapClientProfile) {
                    return InternalR.drawable.ic_bt_hearing_aid
                }
                if (profileFallbackRes == 0) {
                    profileFallbackRes = resId
                }
            }
        }
        if (profileFallbackRes != 0) {
            return profileFallbackRes
        }

        if (btClass != null) {
            if (btClass.doesClassMatch(BluetoothClass.PROFILE_HEADSET)) {
                return InternalR.drawable.ic_bt_headset_hfp
            }
            if (btClass.doesClassMatch(BluetoothClass.PROFILE_A2DP)) {
                return InternalR.drawable.ic_bt_headphones_a2dp
            }
        }

        return InternalR.drawable.ic_settings_bluetooth
    }

    companion object {
        internal fun validPercentage(level: Int?): Int? =
            level?.takeIf { it in 0..100 }

        internal fun formatMultiBattery(left: Int?, right: Int?, caseLevel: Int?): String? {
            val l = validPercentage(left)
            val r = validPercentage(right)
            val c = validPercentage(caseLevel)
            if (l == null && r == null && c == null) return null

            return buildList {
                    if (l != null) add("L $l%")
                    if (r != null) add("R $r%")
                    if (c != null) add("Case $c%")
                }
                .joinToString(" \u00b7 ")
                .takeIf { it.isNotEmpty() }
        }
    }
}
