package com.p2pfiletransfer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.p2pfiletransfer.P2pFileTransferModule.Companion.NAME
import com.p2pfiletransfer.P2pFileTransferModule.Companion.PORT
import com.p2pfiletransfer.P2pFileTransferModule.Companion.TIMEOUT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


internal class WiFiP2PBroadcastReceiver(
  private val manager: WifiP2pManager,
  private val channel: WifiP2pManager.Channel,
  private val getContext: () -> ReactContext,
  private val getScope: () -> CoroutineScope,
) : BroadcastReceiver() {
  private val clients = mutableSetOf<String>()

  @SuppressLint("MissingPermission")
  override fun onReceive(context: Context?, intent: Intent?) {
    when (intent?.action) {
      WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
        val data = WiFiP2PDeviceMapper.mapWifiP2pStateToReactEntity(
          state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
        )
        sendEvent("$NAME:P2P_STATE_CHANGED", data)
      }

      WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
        manager.requestPeers(channel, peerListListener)
      }

      WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
        manager.requestConnectionInfo(channel, connectionListener)
      }

      WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
        manager.requestGroupInfo(channel, groupInfoListener)
      }
    }
  }

  private val groupInfoListener = WifiP2pManager.GroupInfoListener { group ->
    if (group != null) {
      val params = WiFiP2PDeviceMapper.mapWiFiP2PGroupInfoToReactEntity(group)
      sendEvent("$NAME:THIS_DEVICE_CHANGED_ACTION", params)
    }
  }

  private val peerListListener = WifiP2pManager.PeerListListener { deviceList ->
    val params = WiFiP2PDeviceMapper.mapDevicesInfoToReactEntity(deviceList)
    sendEvent("$NAME:PEERS_UPDATED", params)
  }

  private val connectionListener = WifiP2pManager.ConnectionInfoListener { info ->
    getScope().launch {
      when {
        info.groupFormed && info.isGroupOwner -> {
          Log.i(NAME, "Server Test: preparing to receive message")
          val ipAddress = receiveTestMessage(PORT)
          if (ipAddress != null) {
            Log.d(NAME, "Server Test: Got client address - $ipAddress")
            clients += ipAddress

            val params = WiFiP2PDeviceMapper.mapClientsToReactEntity(clients.toList())
            sendEvent("$NAME:CLIENTS_UPDATED", params)
          }
        }

        info.groupFormed && !info.isGroupOwner -> {
          Log.i(NAME, "Server Test: preparing to send message")
          sendTestMessage(info.groupOwnerAddress.hostAddress!!, PORT, TIMEOUT)
        }
      }
    }
    val params = WiFiP2PDeviceMapper.mapWiFiP2PInfoToReactEntity(info)
    sendEvent("$NAME:CONNECTION_INFO_UPDATED", params)
  }

  private fun sendEvent(eventName: String, params: WritableMap?) {
    Log.i(NAME, "Sending $params to $eventName")
    getContext()
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }
}
