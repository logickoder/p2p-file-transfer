package com.p2pfiletransfer

import android.util.Log
import com.p2pfiletransfer.P2pFileTransferModule.Companion.NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket


/**
 * Send a test message to the server
 */
internal suspend fun sendTestMessage(
  groupOwnerAddress: String,
  port: Int,
  socketTimeout: Int
) = withContext(Dispatchers.IO) {
  // If the client is not listening when the server create the connection, the connection is not established => Try again
  var retry = 10

  Socket().apply {
    reuseAddress = true
    bind(null)
  }.use { socket ->
    try {
      do {
        socket.connect((InetSocketAddress(groupOwnerAddress, port)), socketTimeout)
        retry--
      } while (!socket.isConnected && retry > 0)

      if (socket.isConnected) {
        Log.i(NAME, "Client Test: Socket opened")
        socket.getOutputStream().use { os ->
          ObjectOutputStream(os).use { oos ->
            oos.writeObject(java.lang.String(NAME))
            Log.i(NAME, "Client Test: Sent message")
          }
        }
      }
      Unit
    } catch (e: Exception) {
      Log.e(NAME, "Exception from sendTestMessage: ${e.message}", e)
    }
  }
}

/**
 * Receive a test message from the client to get the client IP address
 */
internal suspend fun receiveTestMessage(
  port: Int,
): String? = withContext(Dispatchers.IO) {
  ServerSocket().apply {
    reuseAddress = true
    bind(InetSocketAddress(port))
  }.use { serverSocket ->
    try {
      val client = serverSocket.accept()
      Log.i(NAME, "Server Test: connection done")

      client.getInputStream().use { cis ->
        ObjectInputStream(cis).use { ois ->
          val message = ois.readObject()
          if (message as? String == NAME) {
            client.inetAddress.toString().removePrefix("/")
          } else null
        }
      }
    } catch (e: Exception) {
      Log.e(NAME, "Exception from receiveTestMessage: ${e.message}", e)
      null
    }
  }
}
