package com.p2pfiletransfer

import android.media.MediaScannerConnection
import android.util.Log
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import com.p2pfiletransfer.P2pFileTransferModule.Companion.NAME
import com.p2pfiletransfer.P2pFileTransferModule.Companion.PORT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.UUID

internal object FileTransferServer {

  suspend fun start(
    destination: String,
    scanToGallery: Boolean,
    context: ReactContext,
    promise: Promise,
    sendEvent: (String, WritableMap) -> Unit,
  ) = withContext(Dispatchers.IO) {
    val start = System.currentTimeMillis()
    try {
      ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(PORT))
      }.use { server ->
        Log.i(NAME, "Server: Socket opened")

        server.accept().use { client ->
          Log.i(NAME, "Server: connection done")

          DataInputStream(BufferedInputStream(client.getInputStream())).use { inputStream ->

            val directory = File(destination)
            if (!directory.exists()) {
              directory.mkdirs()
            }

            val size = inputStream.readLong()
            val name = inputStream.readUTF()
            val type = inputStream.readUTF()

            val file = File(directory, name ?: "${UUID.randomUUID()}.$type")

            Log.i(NAME, "Server: copying file to ${file.absolutePath}")

            FileOutputStream(file).use { outputStream ->
              val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
              var bytesRead: Int
              var totalReceived = 0L

              while (totalReceived < size) {
                bytesRead = inputStream.read(buffer)
                outputStream.write(buffer, 0, bytesRead)
                // Update progress
                totalReceived += bytesRead
                sendEvent(
                  "PROGRESS_FILE_RECEIVE",
                  WiFiP2PDeviceMapper.mapSendFileBundleToReactEntity(
                    System.currentTimeMillis() - start,
                    null,
                    (totalReceived / size.toFloat()) * 100,
                  )
                )
              }
            }

            withContext(Dispatchers.Main) {
              Log.i(NAME, "File copied - ${file.absolutePath}")
              promise.resolve(
                WiFiP2PDeviceMapper.mapSendFileBundleToReactEntity(
                  System.currentTimeMillis() - start,
                  file.absolutePath,
                  100f,
                )
              )
              if (scanToGallery) {
                MediaScannerConnection.scanFile(
                  context,
                  arrayOf(file.toString()),
                  null,
                  null
                )
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(NAME, e.message, e)
      promise.reject(e)
    }
  }
}
