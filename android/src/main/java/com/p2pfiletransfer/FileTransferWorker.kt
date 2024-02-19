package com.p2pfiletransfer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.os.bundleOf
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.p2pfiletransfer.P2pFileTransferModule.Companion.NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume

/**
 *
 * A service that process each file transfer request by opening a socket connection
 * with the WiFi Direct Group Owner and writing the file
 */
internal class FileTransferWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val start = System.currentTimeMillis()

    val fileUri = inputData.getString(EXTRAS_FILE_PATH)!!
    val host = inputData.getString(EXTRAS_ADDRESS)!!
    val port = inputData.getString(EXTRAS_PORT)!!.toInt()
    val socket = Socket()

    try {
      Log.i(NAME, "Opening client socket - ")
      socket.bind(null)
      socket.connect((InetSocketAddress(host, port)), SOCKET_TIMEOUT)

      Log.i(NAME, "Client socket connected - " + socket.isConnected)
      val outputStream = socket.getOutputStream()

      val inputStream: InputStream? = try {
        applicationContext.contentResolver.openInputStream(Uri.parse(fileUri))
      } catch (e: FileNotFoundException) {
        Log.e(NAME, e.message!!)
        null
      }

      inputStream?.copyTo(outputStream)
      Log.i(NAME, "Client: Data written")

      val time = System.currentTimeMillis() - start
      setProgress(
        workDataOf(
          RESULT_TIME to time,
          RESULT_FILE to fileUri
        )
      )
      Result.success()
    } catch (e: Exception) {
      Log.e(NAME, e.message, e)
      e.printStackTrace()

      setProgress(workDataOf(RESULT_ERROR to e.message))
      Result.failure()
    } finally {
      if (socket.isConnected) {
        try {
          socket.close()
        } catch (e: IOException) {
          // Give up
          e.printStackTrace()
        }
      }
    }
  }

  companion object {
    private const val SOCKET_TIMEOUT = 5000
    private const val EXTRAS_FILE_PATH = "file_url"
    private const val EXTRAS_ADDRESS = "go_host"
    private const val EXTRAS_PORT = "go_port"

    private const val RESULT_TIME = "result_time"
    private const val RESULT_FILE = "result_file"
    private const val RESULT_ERROR = "result_error"

    suspend fun start(
      uri: Uri,
      address: String,
      port: String,
      context: Context,
    ) = suspendCancellableCoroutine { cont ->
      val fileTransferWorkRequest = OneTimeWorkRequestBuilder<FileTransferWorker>()
        .setInputData(
          workDataOf(
            EXTRAS_FILE_PATH to uri.toString(),
            EXTRAS_ADDRESS to address,
            EXTRAS_PORT to port,
          )
        )
        .build()
      WorkManager.getInstance(context).enqueue(fileTransferWorkRequest)

      suspend {
        WorkManager.getInstance(context).getWorkInfoByIdFlow(fileTransferWorkRequest.id)
          .collect { workInfo ->
            val progress = workInfo.progress
            val time = progress.getLong(RESULT_TIME, -1)
            val file = progress.getString(RESULT_FILE)
            val error = progress.getString(RESULT_ERROR)

            if (error != null) {
              cont.resume(1 to bundleOf(RESULT_ERROR to error))
            } else if (file != null) {
              cont.resume(
                0 to bundleOf(
                  RESULT_TIME to time,
                  RESULT_FILE to file
                )
              )
            }
          }
      }
    }
  }
}
