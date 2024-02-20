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
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Looper.getMainLooper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.location.LocationManagerCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
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
  private val services = mutableMapOf<String, String>()

  override fun getName() = NAME

  override fun initialize() {
    super.initialize()
//    reactApplicationContext.registerComponentCallbacks()
  }

  override fun onConnectionInfoAvailable(p0: WifiP2pInfo?) {
    wifiP2pInfo = p0
  }

  @ReactMethod
  fun getConnectionInfo(promise: Promise) {
    manager?.requestConnectionInfo(
      channel
    ) { wifiP2pInformation ->
      wifiP2pInfo = wifiP2pInformation
      promise.resolve(mapper.mapWiFiP2PInfoToReactEntity(wifiP2pInformation))
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

  @ReactMethod
  fun init(promise: Promise) {
    scope.launch {

      if (manager != null) { // prevent reinitialization
        promise.reject("0x2", "$NAME module should only be initialized once.")
        return@launch
      }

      // check if location permission is granted
      if (ActivityCompat.checkSelfPermission(
          reactApplicationContext,
          Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S
      ) {
        val result = currentActivity.request(Manifest.permission.ACCESS_FINE_LOCATION)

        if (result == Permissions.BLOCKED) {
          reactApplicationContext.openSettings()
        }
        when (result) {
          Permissions.GRANTED -> {

          }

          Permissions.DENIED,
          Permissions.BLOCKED -> {
            promise.reject("0x3", "Location permission is required to use this module.")
            return@launch
          }
        }
      }

      // check if location is enabled
      if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
        val locationManager =
          reactApplicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
          promise.reject("0x5", "Location service is required to use this module.")
          return@launch
        }
      }

      if (ActivityCompat.checkSelfPermission(
          reactApplicationContext,
          Manifest.permission.NEARBY_WIFI_DEVICES
        ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
      ) {
        val result = currentActivity.request(Manifest.permission.NEARBY_WIFI_DEVICES)

        if (result == Permissions.BLOCKED) {
          reactApplicationContext.openSettings()
        }
        when (result) {
          Permissions.GRANTED -> {

          }

          Permissions.DENIED,
          Permissions.BLOCKED -> {
            promise.reject("0x4", "Nearby devices permission is required to use this module.")
            return@launch
          }
        }
      }

      val intentFilter = IntentFilter()

      // Indicates a change in the Wi-Fi Direct status.
      intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

      // Indicates a change in the list of available peers.
      intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)

      // Indicates the state of Wi-Fi Direct connectivity has changed.
      intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)

      // Indicates this device's details have changed.
      intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

      val activity = currentActivity
      if (activity != null) {
        try {
          manager = activity.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
          channel = manager?.initialize(activity, getMainLooper(), null)

          if (manager != null && channel != null) {
            broadcastReceiver = WiFiP2PBroadcastReceiver(
              manager!!,
              channel!!,
              reactApplicationContext,
              scope
            )
            activity.registerReceiver(broadcastReceiver, intentFilter)
          }

          promise.resolve(manager != null && channel != null)
        } catch (e: Exception) {
          promise.reject("0x1", "can not get WIFI_P2P_SERVICE", e)
        }
      }

      promise.reject("0x0", "$name module can not be initialized, since main activity is `null`")
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
    manager!!.removeGroup(
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

    manager!!.discoverPeers(
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

  companion object {
    const val NAME = "P2pFileTransfer"
    const val PORT = 8988
    const val TIMEOUT = 5000
  }
}
