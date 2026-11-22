package com.example.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors real Android radio bearer state (5G SA vs 5G NSA vs 4G LTE vs Wi-Fi)
 * and requests Android's 5G Low-Latency Network Slice (NET_CAPABILITY_PRIORITIZE_LATENCY).
 *
 * All telemetry is derived strictly from live Android ConnectivityManager and TelephonyManager APIs.
 */
class FiveGNetworkManager(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val _linkState = MutableStateFlow(RadioLinkSnapshot())
    val linkState: StateFlow<RadioLinkSnapshot> = _linkState.asStateFlow()

    private val _activeSliceNetwork = MutableStateFlow<Network?>(null)
    val activeSliceNetwork: StateFlow<Network?> = _activeSliceNetwork.asStateFlow()

    private var isStarted = false
    private var defaultCallbackRegistered = false
    private var sliceCallbackRegistered = false
    private var telephonyCallback: Any? = null

    @Volatile
    private var isCellularTransport = false
    @Volatile
    private var isWifiTransport = false
    @Volatile
    private var defaultNetworkHasLatencySlice = false
    @Volatile
    private var lastDisplayInfo: TelephonyDisplayInfo? = null

    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(network)
            updateFromDefaultCapabilities(caps)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateFromDefaultCapabilities(networkCapabilities)
        }

        override fun onLost(network: Network) {
            val active = connectivityManager.activeNetwork
            val caps = active?.let { connectivityManager.getNetworkCapabilities(it) }
            updateFromDefaultCapabilities(caps)
        }
    }

    private val latencySliceCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "5G low-latency slice network available")
            _activeSliceNetwork.value = network
            refreshSnapshot(note = "5G Low-Latency Slice active")
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _activeSliceNetwork.value = network
            refreshSnapshot(note = "5G Low-Latency Slice active")
        }

        override fun onLost(network: Network) {
            if (_activeSliceNetwork.value == network) {
                _activeSliceNetwork.value = null
            }
            refreshSnapshot(note = "5G slice lost — using default route")
        }

        override fun onUnavailable() {
            _activeSliceNetwork.value = null
            refreshSnapshot(note = "5G slice unavailable on current carrier/network")
        }
    }

    fun start() {
        if (isStarted) return
        isStarted = true

        // Initial synchronous read of active network
        val initialCaps = connectivityManager.activeNetwork?.let {
            connectivityManager.getNetworkCapabilities(it)
        }
        updateFromDefaultCapabilities(initialCaps)

        try {
            connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback)
            defaultCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register default network callback: ${e.message}")
        }

        registerTelephonyListenerIfSupported()
        syncSlicePreference(SettingsStore.current.prefer5gSlice)
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        releaseLowLatencySlice()
        unregisterTelephonyListener()
        if (defaultCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (_: Exception) {
            }
            defaultCallbackRegistered = false
        }
    }

    fun syncSlicePreference(preferSlice: Boolean) {
        if (!isStarted) return
        if (preferSlice) {
            requestLowLatencySlice()
        } else {
            releaseLowLatencySlice()
            refreshSnapshot(note = "5G slice request disabled in settings")
        }
    }

    fun requestLowLatencySlice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            refreshSnapshot(note = "Android 13+ required for 5G network slicing")
            return
        }
        if (sliceCallbackRegistered) return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                .build()
            connectivityManager.requestNetwork(request, latencySliceCallback)
            sliceCallbackRegistered = true
            refreshSnapshot(note = "Requesting NET_CAPABILITY_PRIORITIZE_LATENCY...")
        } catch (e: SecurityException) {
            Log.w(TAG, "Slice request denied by OS/manifest: ${e.message}")
            sliceCallbackRegistered = false
            refreshSnapshot(note = "Slice unavailable (${e.localizedMessage ?: "not permitted"})")
        } catch (e: Exception) {
            Log.w(TAG, "Slice request failed: ${e.message}")
            sliceCallbackRegistered = false
            refreshSnapshot(note = "Using default network")
        }
    }

    fun releaseLowLatencySlice() {
        if (sliceCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(latencySliceCallback)
            } catch (_: Exception) {
            }
            sliceCallbackRegistered = false
        }
        _activeSliceNetwork.value = null
    }

    /**
     * Android 14+ carrier upsell workflow for NET_CAPABILITY_PRIORITIZE_LATENCY when supported.
     */
    fun requestCarrierSliceBoost(onResult: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            onResult("Carrier 5G slice boost requires Android 14+.")
            return
        }
        val tm = telephonyManager
        if (tm == null) {
            onResult("Cellular modem unavailable on this device.")
            return
        }
        try {
            if (!tm.isPremiumCapabilityAvailableForPurchase(TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY)) {
                onResult("Carrier does not advertise on-demand 5G low-latency slice upsell.")
                return
            }
            tm.purchasePremiumCapability(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY,
                appContext.mainExecutor
            ) { resultCode ->
                val message = when (resultCode) {
                    TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_SUCCESS -> {
                        requestLowLatencySlice()
                        "5G low-latency slice activated!"
                    }
                    TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED -> {
                        requestLowLatencySlice()
                        "5G low-latency slice already active on this SIM."
                    }
                    TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_CANCELED ->
                        "Carrier slice request cancelled."
                    TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_DISABLED,
                    TelephonyManager.PURCHASE_PREMIUM_CAPABILITY_RESULT_FEATURE_NOT_SUPPORTED ->
                        "Carrier does not support 5G slicing on this plan."
                    else -> "Carrier slice boost unavailable (code $resultCode). Using default network."
                }
                refreshSnapshot(note = message)
                onResult(message)
            }
        } catch (e: SecurityException) {
            onResult("Basic phone state permission required for carrier slice check.")
        } catch (e: Exception) {
            onResult(e.localizedMessage ?: "Unable to query carrier slice boost.")
        }
    }

    private fun updateFromDefaultCapabilities(caps: NetworkCapabilities?) {
        if (caps == null) {
            isCellularTransport = false
            isWifiTransport = false
            defaultNetworkHasLatencySlice = false
            _linkState.value = _linkState.value.copy(
                bearer = RadioBearer.OFFLINE,
                sliceStatus = SliceStatus.UNAVAILABLE,
                downstreamKbps = 0,
                upstreamKbps = 0,
                isUnmetered = false,
                statusNote = "No active internet connection"
            )
            return
        }

        isCellularTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        isWifiTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED))
        defaultNetworkHasLatencySlice =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)

        refreshSnapshot(
            downstreamKbps = caps.linkDownstreamBandwidthKbps.coerceAtLeast(0),
            upstreamKbps = caps.linkUpstreamBandwidthKbps.coerceAtLeast(0),
            unmetered = unmetered
        )
    }

    private fun registerTelephonyListenerIfSupported() {
        val tm = telephonyManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!hasBasicPhoneReadAccess()) return
        try {
            val cb = createDisplayInfoCallback()
            tm.registerTelephonyCallback(appContext.mainExecutor, cb)
            telephonyCallback = cb
        } catch (e: SecurityException) {
            Log.w(TAG, "Telephony display info callback skipped: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register telephony callback: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createDisplayInfoCallback(): TelephonyCallback {
        return object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                lastDisplayInfo = telephonyDisplayInfo
                refreshSnapshot()
            }
        }
    }

    private fun unregisterTelephonyListener() {
        val cb = telephonyCallback ?: return
        val tm = telephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && cb is TelephonyCallback) {
            try {
                tm.unregisterTelephonyCallback(cb)
            } catch (_: Exception) {
            }
        }
        telephonyCallback = null
    }

    private fun hasBasicPhoneReadAccess(): Boolean {
        val basicGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_BASIC_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val fullGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        return basicGranted || fullGranted
    }

    @SuppressLint("MissingPermission")
    private fun detectBearer(): RadioBearer {
        if (!isCellularTransport && !isWifiTransport) {
            val activeCaps = connectivityManager.activeNetwork?.let {
                connectivityManager.getNetworkCapabilities(it)
            }
            if (activeCaps == null) return RadioBearer.OFFLINE
            if (activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return RadioBearer.WIFI
            if (!activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return RadioBearer.WIFI
        }
        if (isWifiTransport && !isCellularTransport) {
            return RadioBearer.WIFI
        }

        // Cellular transport: inspect TelephonyDisplayInfo first for accurate 5G SA vs 5G NSA detection.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val info = lastDisplayInfo
            if (info != null) {
                return classifyDisplayInfo(info)
            }
        }

        val tm = telephonyManager ?: return RadioBearer.CELLULAR_LEGACY
        val dataNetType = try {
            if (hasBasicPhoneReadAccess()) tm.dataNetworkType else TelephonyManager.NETWORK_TYPE_UNKNOWN
        } catch (_: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                dataNetType == TelephonyManager.NETWORK_TYPE_NR -> RadioBearer.FIVE_G_SA
            dataNetType == TelephonyManager.NETWORK_TYPE_LTE ||
                dataNetType == TelephonyManager.NETWORK_TYPE_IWLAN -> RadioBearer.LTE_4G
            dataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN -> RadioBearer.LTE_4G
            else -> RadioBearer.CELLULAR_LEGACY
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @Suppress("DEPRECATION")
    private fun classifyDisplayInfo(info: TelephonyDisplayInfo): RadioBearer {
        val overrideType = info.overrideNetworkType
        val baseType = info.networkType
        return when (overrideType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE -> RadioBearer.FIVE_G_NSA
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> {
                if (baseType == TelephonyManager.NETWORK_TYPE_NR) RadioBearer.FIVE_G_SA
                else RadioBearer.FIVE_G_NSA
            }
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> RadioBearer.LTE_4G
            else -> when (baseType) {
                TelephonyManager.NETWORK_TYPE_NR -> RadioBearer.FIVE_G_SA
                TelephonyManager.NETWORK_TYPE_LTE -> RadioBearer.LTE_4G
                else -> RadioBearer.CELLULAR_LEGACY
            }
        }
    }

    private fun checkUpsellAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        if (!hasBasicPhoneReadAccess()) return false
        return try {
            telephonyManager?.isPremiumCapabilityAvailableForPurchase(
                TelephonyManager.PREMIUM_CAPABILITY_PRIORITIZE_LATENCY
            ) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun refreshSnapshot(
        downstreamKbps: Int = _linkState.value.downstreamKbps,
        upstreamKbps: Int = _linkState.value.upstreamKbps,
        unmetered: Boolean = _linkState.value.isUnmetered,
        note: String? = null
    ) {
        val bearer = detectBearer()
        val upsell = checkUpsellAvailable()
        val preferSlice = SettingsStore.current.prefer5gSlice
        val hasSlice = _activeSliceNetwork.value != null || defaultNetworkHasLatencySlice

        val sliceStatus = when {
            !preferSlice -> SliceStatus.DISABLED
            hasSlice -> SliceStatus.ACTIVE
            upsell -> SliceStatus.PURCHASE_AVAILABLE
            sliceCallbackRegistered && bearer == RadioBearer.FIVE_G_SA -> SliceStatus.REQUESTING
            else -> SliceStatus.UNAVAILABLE
        }

        val carrier = try {
            telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }.orEmpty()
        } catch (_: Exception) {
            ""
        }

        _linkState.value = RadioLinkSnapshot(
            bearer = bearer,
            sliceStatus = sliceStatus,
            downstreamKbps = downstreamKbps,
            upstreamKbps = upstreamKbps,
            isUnmetered = unmetered,
            sliceUpsellAvailable = upsell,
            carrierName = carrier,
            statusNote = note ?: when {
                hasSlice && bearer == RadioBearer.FIVE_G_SA -> "5G SA + Low-Latency Slice active"
                hasSlice -> "Low-Latency Slice active"
                bearer == RadioBearer.FIVE_G_SA -> "5G SA connected (default bearer)"
                bearer == RadioBearer.FIVE_G_NSA -> "5G NSA connected"
                bearer == RadioBearer.WIFI -> "Connected via Wi-Fi"
                bearer == RadioBearer.LTE_4G -> "Connected via 4G LTE"
                bearer == RadioBearer.OFFLINE -> "No active network"
                else -> "Cellular network active"
            }
        )
    }

    companion object {
        private const val TAG = "FiveGNetworkManager"
    }
}
