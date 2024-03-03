package com.p2pfiletransfer

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Bundle
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap

internal object WiFiP2PDeviceMapper {
  fun mapWifiP2pStateToReactEntity(enabled: Boolean): WritableMap =
    Arguments.createMap().apply {
      putBoolean("enabled", enabled)
    }

  fun mapDevicesInfoToReactEntity(deviceList: WifiP2pDeviceList): WritableMap =
    Arguments.createMap().apply {
      putArray("devices", mapDeviceListToReactEntityArray(deviceList))
    }

  private fun mapDeviceListToReactEntityArray(deviceList: WifiP2pDeviceList): WritableArray =
    Arguments.createArray().apply {
      for (device in deviceList.deviceList) {
        pushMap(mapDeviceInfoToReactEntity(device))
      }
    }

  private fun mapDeviceInfoToReactEntity(device: WifiP2pDevice): WritableMap =
    Arguments.createMap().apply {
      putString("deviceName", device.deviceName)
      putString("deviceAddress", device.deviceAddress)
      putString("primaryDeviceType", device.primaryDeviceType)
      putString("secondaryDeviceType", device.secondaryDeviceType)
      putBoolean("isGroupOwner", device.isGroupOwner)
      putInt("status", device.status)
    }

  fun mapClientsToReactEntity(clients: List<String>): WritableMap = Arguments.createMap().apply {
    putArray("clients", Arguments.createArray().apply {
      clients.forEach { client ->
        pushString(client)
      }
    })
  }

  fun mapWiFiP2PInfoToReactEntity(wifiP2pInformation: WifiP2pInfo): WritableMap =
    Arguments.createMap().apply {

      if (wifiP2pInformation.groupOwnerAddress != null) {
        val groupOwnerAddress = Arguments.createMap()
        groupOwnerAddress.putString(
          "hostAddress", wifiP2pInformation.groupOwnerAddress.hostAddress
        )
        groupOwnerAddress.putBoolean(
          "isLoopbackAddress", wifiP2pInformation.groupOwnerAddress.isLoopbackAddress
        )

        putMap("groupOwnerAddress", groupOwnerAddress)
      } else {
        putNull("groupOwnerAddress")
      }

      putBoolean("groupFormed", wifiP2pInformation.groupFormed)
      putBoolean("isGroupOwner", wifiP2pInformation.isGroupOwner)
    }

  fun mapWiFiP2PGroupInfoToReactEntity(group: WifiP2pGroup): WritableMap =
    Arguments.createMap().apply {
      putString("interface", group.getInterface())
      putString("networkName", group.networkName)
      putString("passphrase", group.passphrase)

      val groupOwner = group.owner
      if (groupOwner != null) {
        putMap("owner", Arguments.createMap().apply {
          putString("deviceAddress", groupOwner.deviceAddress)
          putString("deviceName", groupOwner.deviceName)
          putInt("status", groupOwner.status)
          putString("primaryDeviceType", groupOwner.primaryDeviceType)
          putString("secondaryDeviceType", groupOwner.secondaryDeviceType)
        })
      } else {
        putNull("owner")
      }

      putArray("clients", Arguments.createArray().apply {
        group.clientList?.forEach { device ->
          pushMap(mapDeviceInfoToReactEntity(device))
        }
      })
    }

  fun mapSendFileBundleToReactEntity(
    time: Long,
    file: String?,
    progress: Float,
  ): WritableMap = Arguments.createMap().apply {
    putDouble("time", time.toDouble())
    if (file == null) {
      putNull("file")
    } else {
      putString("file", file)
    }
    putDouble("progress", progress.toDouble())
  }
}
