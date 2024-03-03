package com.p2pfiletransfer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.p2pfiletransfer.P2pFileTransferModule.Companion.NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.net.InetSocketAddress
import java.net.Socket


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
    Socket().apply {
      reuseAddress = true
    }.use { socket ->
      try {
        Log.i(NAME, "Opening client socket - ")
        socket.bind(null)
        socket.connect((InetSocketAddress(host, port)), SOCKET_TIMEOUT)

        Log.i(NAME, "Client socket connected - ${socket.isConnected}")
        DataOutputStream(BufferedOutputStream(socket.getOutputStream())).use { outputStream ->
          val file = Uri.parse(fileUri)
          val (name, size, type) = getFileInfo(file)
            ?: throw FileNotFoundException("$fileUri not found")
          Log.i(NAME, "Client: Got file Info - $name, $size, $type")

          // Write the file size
          outputStream.writeLong(size)
          // Write the file name
          outputStream.writeUTF(name)
          // Write the file type
          outputStream.writeUTF(type)
          // Write the file
          getFileStream(file)?.use { inputStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            var totalSent = 0L
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
              totalSent += bytesRead
              outputStream.write(buffer, 0, bytesRead)
              setProgress(
                workDataOf(
                  RESULT_PROGRESS to (totalSent / size.toFloat()) * 100,
                  RESULT_TIME to System.currentTimeMillis() - start
                )
              )
            }
          } ?: throw FileNotFoundException("$fileUri not found")

          Log.i(NAME, "Client: File sent")

          setProgress(
            workDataOf(
              RESULT_PROGRESS to 100f,
              RESULT_TIME to System.currentTimeMillis() - start,
              RESULT_FILE to fileUri
            )
          )
          Result.success()
        }
      } catch (e: Exception) {
        Log.e(NAME, e.message, e)

        setProgress(workDataOf(RESULT_ERROR to e.message))
        Result.failure()
      }
    }
  }

  private fun getFileInfo(file: Uri): Triple<String, Long, String>? {
    return when (file.scheme) {
      "content" -> applicationContext.contentResolver.query(
        file,
        null,
        null,
        null,
        null
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
          val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
          val typeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

          Triple(
            cursor.getString(nameIndex),
            cursor.getLong(sizeIndex),
            cursor.getString(typeIndex),
          )
        } else null
      }

      else -> File(file.path!!).run {
        Triple(nameWithoutExtension, length(), extension)
      }
    }
  }

  private fun getFileStream(file: Uri) = when (file.scheme) {
    "content" -> applicationContext.contentResolver.openInputStream(file)
    else -> FileInputStream(file.path)
  }

  companion object {
    private const val SOCKET_TIMEOUT = 5000
    private const val EXTRAS_FILE_PATH = "file_url"
    private const val EXTRAS_ADDRESS = "go_host"
    private const val EXTRAS_PORT = "go_port"

    internal const val RESULT_PROGRESS = "result_progress"
    internal const val RESULT_TIME = "result_time"
    internal const val RESULT_FILE = "result_file"
    internal const val RESULT_ERROR = "result_error"

    suspend fun start(
      uri: Uri,
      address: String,
      port: String,
      context: Context,
      onData: (Bundle) -> Unit,
    ) {
      val workRequest = OneTimeWorkRequestBuilder<FileTransferWorker>()
        .setInputData(
          workDataOf(
            EXTRAS_FILE_PATH to uri.toString(),
            EXTRAS_ADDRESS to address,
            EXTRAS_PORT to port,
          )
        )
        .build()

      WorkManager.getInstance(context).enqueue(workRequest)

      WorkManager.getInstance(context).getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
        val data = workInfo.progress

        val progress = data.getFloat(RESULT_PROGRESS, 0f)
        val time = data.getLong(RESULT_TIME, -1)
        val file = data.getString(RESULT_FILE)
        val error = data.getString(RESULT_ERROR)

        onData(
          Bundle().apply {
            putFloat(RESULT_PROGRESS, progress)
            putLong(RESULT_TIME, time)
            putString(RESULT_FILE, file)
            putString(RESULT_ERROR, error)
          }
        )
      }
    }
  }
}
