package com.p2pfiletransfer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper.getMainLooper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.location.LocationManagerCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch


class P2pFileTransferModule(
  reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext), WifiP2pManager.ConnectionInfoListener {

  private var broadcastReceiver: WiFiP2PBroadcastReceiver? = null
  private var wifiP2pInfo: WifiP2pInfo? = null
  private var manager: WifiP2pManager? = null
  private var channel: WifiP2pManager.Channel? = null
  private val mapper = WiFiP2PDeviceMapper

  private val scope = MainScope()

  override fun getName() = NAME

  override fun onConnectionInfoAvailable(p0: WifiP2pInfo?) {
    wifiP2pInfo = p0
  }

  /**
   * Check if the app has either location or nearby devices permission
   */
  @ReactMethod
  fun checkPermissions(promise: Promise) = promise.resolve(isPermissionGranted())

  @ReactMethod
  fun shouldEnableLocation(promise: Promise) = promise.resolve(shouldEnableLocation())

  @ReactMethod
  fun isLocationEnabled(promise: Promise) = promise.resolve(isLocationEnabled())

  @ReactMethod
  fun isWifiEnabled(promise: Promise) = promise.resolve(isWifiEnabled())

  @ReactMethod
  fun isWifiApEnabled(promise: Promise) = promise.resolve(isWifiApEnabled())

  @ReactMethod
  fun requestPermissions(promise: Promise) = scope.launch {
    requestPermissions(
      onSuccess = { promise.resolve(true) },
      onFailure = { code, message -> promise.reject(code, message) }
    )
  }

  @ReactMethod
  fun openAppSettings() = currentActivity.openSettings()

  @ReactMethod
  fun openWifiSettings() = currentActivity.openSettings(
    screen = Settings.ACTION_WIFI_SETTINGS
  )

  @ReactMethod
  fun openLocationSettings() = currentActivity.openSettings(
    screen = Settings.ACTION_LOCATION_SOURCE_SETTINGS
  )

  @ReactMethod
  fun openWifiApSettings() = currentActivity.openSettings(
    screen = Settings.ACTION_WIRELESS_SETTINGS
  )

  @ReactMethod
  fun init(promise: Promise) {
    scope.launch {

      if (manager != null) { // prevent reinitialization
        promise.reject("0x2", "$NAME module should only be initialized once.")
        return@launch
      }

      // check for permissions
      requestPermissions(
        onSuccess = {
        },
        onFailure = { code, message ->
          promise.reject(code, message)
        }
      )

      // check if location is should be enabled and is enabled
      if (shouldEnableLocation() && !isLocationEnabled()) {
        openLocationSettings()
        promise.reject("0x5", "Location service is required to use this module.")
        return@launch
      }

      // check if wifi is enabled
      if (!isWifiEnabled()) {
        openWifiSettings()
        promise.reject("0x6", "Turn on your wifi to continue")
        return@launch
      }

      // check if wifi ap is enabled
      if (isWifiApEnabled()) {
        openWifiApSettings()
        promise.reject("0x7", "Turn off your wifi hotspot to continue")
        return@launch
      }

      try {
        promise.resolve(registerBroadcastReceiver())
      } catch (e: java.lang.Exception) {
        promise.reject("0x0", "$name can't listen to WifiP2p changes", e)
      }
    }
  }


  @ReactMethod
  fun getConnectionInfo(promise: Promise) {
    manager?.requestConnectionInfo(
      channel
    ) { info ->
      wifiP2pInfo = info
      promise.resolve(mapper.mapWiFiP2PInfoToReactEntity(info))
    }
  }

  @SuppressLint("MissingPermission")
  @ReactMethod
  fun getGroupInfo(promise: Promise) {
    manager?.requestGroupInfo(
      channel
    ) { group ->
      if (group != null) {
        promise.resolve(mapper.mapWiFiP2PGroupInfoToReactEntity(group))
      } else {
        promise.resolve(null)
      }
    }
  }

  @SuppressLint("MissingPermission")
  @ReactMethod
  fun createGroup(callback: Callback) {
    manager?.createGroup(
      channel,
      object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
          callback.invoke() // Group creation successful
        }

        override fun onFailure(reason: Int) {
          callback.invoke(reason) // Group creation failed
        }
      })
  }

  @ReactMethod
  fun removeGroup(callback: Callback) {
    manager?.removeGroup(
      channel,
      object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
          callback.invoke()
        }

        override fun onFailure(reason: Int) {
          callback.invoke(reason)
        }
      })
  }

  @SuppressLint("MissingPermission")
  @ReactMethod
  fun getAvailablePeersList(promise: Promise) {
    manager?.requestPeers(
      channel
    ) { deviceList ->
      promise.resolve(mapper.mapDevicesInfoToReactEntity(deviceList))
    }
  }

  @SuppressLint("MissingPermission")
  @ReactMethod
  fun discoverPeers(callback: Callback) {
    val wifiManager = reactApplicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    if (!wifiManager.isWifiEnabled) {
      callback.invoke(4, "Turn on your wifi to continue")
      return
    }

    manager?.discoverPeers(
      channel,
      object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
          callback.invoke()
        }

        override fun onFailure(reasonCode: Int) {
          callback.invoke(reasonCode)
        }
      })
  }

  @ReactMethod
  fun stopPeerDiscovery(callback: Callback) {
    manager?.stopPeerDiscovery(
      channel,
      object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
          callback.invoke()
        }

        override fun onFailure(reasonCode: Int) {
          callback.invoke(reasonCode)
        }
      })
  }

  @ReactMethod
  fun cancelConnect(callback: Callback) {
    manager?.cancelConnect(
      channel,
      object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
          callback.invoke()
        }

        override fun onFailure(reasonCode: Int) {
          callback.invoke(reasonCode)
        }
      })
  }

  @SuppressLint("MissingPermission")
  @ReactMethod
  fun connectWithConfig(readableMap: ReadableMap?, callback: Callback) {
    val bundle = Arguments.toBundle(readableMap)
    val config = WifiP2pConfig()

    val deviceAddress = bundle!!.getString("deviceAddress")
    config.deviceAddress = deviceAddress
    config.wps.setup = WpsInfo.PBC

    if (bundle.containsKey("groupOwnerIntent")) {
      config.groupOwnerIntent = bundle.getDouble("groupOwnerIntent").toInt()
    }

    manager?.connect(
      channel,
      config,
      object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
          callback.invoke() // WiFiP2PBroadcastReceiver notifies us. Ignore for now.
        }

        override fun onFailure(reasonCode: Int) {
          callback.invoke(reasonCode)
        }
      })
  }

  @ReactMethod
  fun sendFile(uri: String, promise: Promise) {
    val address = wifiP2pInfo?.groupOwnerAddress?.hostAddress
    if (address != null) {
      sendFileTo(uri, address, promise)
    } else {
      promise.reject(Throwable("CONNECTION_CLOSED"))
    }
  }

  @ReactMethod
  fun sendFileTo(uri: String, address: String, promise: Promise) {
    // User has picked a file. Transfer it to group owner i.e peer using FileTransferService
    Log.i(NAME, "Sending: $uri")
    scope.launch {
      val (resultCode, resultData) = FileTransferWorker.start(
        Uri.parse(uri),
        address,
        port = PORT.toString(),
        context = reactApplicationContext
      )
      if (resultCode == 0) { // successful transfer
        promise.resolve(mapper.mapSendFileBundleToReactEntity(resultData))
      } else { // error
        promise.reject(resultCode.toString(), resultData.getString("error"))
      }
    }
  }

  @ReactMethod
  fun receiveFile(
    destination: String,
    name: String?,
    forceToScanGallery: Boolean,
    callback: Callback?
  ) {
    if (callback == null) {
      return
    }

    manager?.requestConnectionInfo(
      channel
    ) { info ->
      if (info.groupFormed) {
        scope.launch {
          FileTransferServer.start(
            destination,
            name,
            forceToScanGallery,
            reactApplicationContext,
            callback,
          )
        }
      } else {
        Log.i(NAME, "You must be in a group to receive a file")
      }
    }
  }

  /**
   * Setup the broadcast receiver that listens to WiFiP2P changes and sends as events to JS
   */
  private fun registerBroadcastReceiver(): Boolean {
    val intentFilter = IntentFilter().apply {
      // Indicates a change in the Wi-Fi Direct status.
      addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
      // Indicates a change in the list of available peers.
      addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
      // Indicates the state of Wi-Fi Direct connectivity has changed.
      addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
      // Indicates this device's details have changed.
      addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    val activity = currentActivity ?: throw IllegalStateException("Activity is `null`")

    manager = activity.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
      ?: throw IllegalStateException("WiFiP2PManager is `null`")
    channel = manager!!.initialize(activity, getMainLooper(), null)
      ?: throw IllegalStateException("Channel is `null`")


    broadcastReceiver = WiFiP2PBroadcastReceiver(
      manager!!,
      channel!!,
      getContext = { reactApplicationContext },
      getScope = { scope }
    )

    activity.registerReceiver(broadcastReceiver, intentFilter)

    return true
  }

  /**
   * Request permissions
   */
  private suspend fun requestPermissions(
    onSuccess: () -> Unit,
    onFailure: (String, String) -> Unit
  ) {
    when {
      // check for location permission
      Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 && ActivityCompat.checkSelfPermission(
        reactApplicationContext,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
      -> {
        val result = currentActivity.request(Manifest.permission.ACCESS_FINE_LOCATION)

        if (result == Permissions.BLOCKED) {
          currentActivity.openSettings()
        }
        when (result) {
          Permissions.GRANTED -> onSuccess()

          Permissions.DENIED,
          Permissions.BLOCKED -> {
            onFailure("0x3", "Location permission is required to use this module.")
          }
        }
      }

      // check for nearby devices permission
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(
        reactApplicationContext,
        Manifest.permission.NEARBY_WIFI_DEVICES
      ) != PackageManager.PERMISSION_GRANTED
      -> {
        val result = currentActivity.request(Manifest.permission.NEARBY_WIFI_DEVICES)

        if (result == Permissions.BLOCKED) {
          currentActivity.openSettings()
        }
        when (result) {
          Permissions.GRANTED -> onSuccess()

          Permissions.DENIED,
          Permissions.BLOCKED -> {
            onFailure("0x4", "Nearby devices permission is required to use this module.")
          }
        }
      }
    }
  }

  /**
   * Determine if the permission is granted
   */
  private fun isPermissionGranted() = when {
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 -> ActivityCompat.checkSelfPermission(
      reactApplicationContext,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    else -> ActivityCompat.checkSelfPermission(
      reactApplicationContext,
      Manifest.permission.NEARBY_WIFI_DEVICES
    ) == PackageManager.PERMISSION_GRANTED
  }

  /**
   * Determine if the location should be enabled, typically for Android 12 and below
   */
  private fun shouldEnableLocation() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S

  /**
   * Check if the location is enabled
   */
  private fun isLocationEnabled() = LocationManagerCompat.isLocationEnabled(
    reactApplicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
  )

  /**
   * Check if the wifi is enabled
   */
  private fun isWifiEnabled() = (
    reactApplicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    ).isWifiEnabled

  /**
   * Check if the wifi hotspot is enabled
   */
  private fun isWifiApEnabled(): Boolean {
    val wifiManager = reactApplicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled").apply {
      isAccessible = true
    }

    return try {
      method.invoke(wifiManager) as Boolean
    } catch (e: Exception) {
      false
    }
  }

  companion object {
    const val NAME = "P2pFileTransfer"
    const val PORT = 8988
    const val TIMEOUT = 5000
  }
}
