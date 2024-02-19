package com.p2pfiletransfer

import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactContext
import com.p2pfiletransfer.P2pFileTransferModule.Companion.NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.util.UUID

internal object FileTransferServer {

  suspend fun start(
    scanToGallery: Boolean,
    context: ReactContext,
    callback: Callback,
  ) = withContext(Dispatchers.IO) {
    try {
      val server = ServerSocket(8988)
      server.reuseAddress = true
      Log.i(NAME, "Server: Socket opened")

      val client = server.accept()
      Log.i(NAME, "Server: connection done")

      val inputStream = client.getInputStream()

      val directory = File(context.externalCacheDir, "videos")
      val file = File(directory, "${UUID.randomUUID()}.mp4")

      if (!directory.exists()) {
        directory.mkdirs()
      }

      Log.i(NAME, "Server: copying files ${file.absolutePath}")

      FileOutputStream(file).use { inputStream.copyTo(it) }

      server.close()
      withContext(Dispatchers.Main) {
        Log.i(NAME, "File copied - ${file.absolutePath}")
        callback.invoke(file.absolutePath)
        if (scanToGallery) {
          MediaScannerConnection.scanFile(
            context, arrayOf(File(file.absolutePath).toString()), null, null
          )
        }
      }
    } catch (e: IOException) {
      Log.e(NAME, e.message, e)
    }
  }
}
