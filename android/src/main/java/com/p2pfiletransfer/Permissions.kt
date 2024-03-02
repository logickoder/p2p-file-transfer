package com.p2pfiletransfer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.facebook.react.modules.core.PermissionAwareActivity
import com.p2pfiletransfer.P2pFileTransferModule.Companion.NAME
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


/**
 * Request a permission
 */
internal suspend fun Activity?.request(
  permission: String,
) = suspendCancellableCoroutine { cont ->
  try {
    val activity = getPermissionAwareActivity()

    val result = activity.checkSelfPermission(permission)
    if (result == PackageManager.PERMISSION_GRANTED) {
      cont.resume(Permissions.GRANTED)
    }

    activity.requestPermissions(
      arrayOf(permission),
      0
    ) { _, _, results ->
      if (results.isNotEmpty() && results.first() == PackageManager.PERMISSION_GRANTED) {
        cont.resume(Permissions.GRANTED)
        return@requestPermissions true
      }

      if (activity.shouldShowRequestPermissionRationale(permission)) {
        cont.resume(Permissions.DENIED)
      } else {
        cont.resume(Permissions.BLOCKED)
      }

      true
    }

  } catch (e: IllegalStateException) {
    Log.e(NAME, e.message, e)
    cont.resume(Permissions.DENIED)
  }
}

/**
 * Launch the settings screen
 */
fun Activity?.openSettings(screen: String = Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
  try {
    if (this == null) {
      throw NullPointerException("Tried to open settings screen while not attached to an Activity.")
    }
    val intent = Intent(screen).apply {
      if (screen == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
        data = Uri.fromParts("package", packageName, null)
      }
    }
    startActivity(intent)
  } catch (e: Exception) {
    Log.e(NAME, e.message, e)
  }
}

/**
 * Get the [PermissionAwareActivity] from the context
 */
private fun Activity?.getPermissionAwareActivity(): PermissionAwareActivity {
  checkNotNull(this) {
    "Tried to use permissions API while not attached to an " + "Activity."
  }

  check(this is PermissionAwareActivity) {
    "Tried to use permissions API but the host Activity doesn't implement PermissionAwareActivity."
  }

  return this
}

internal enum class Permissions {
  GRANTED,
  DENIED,
  BLOCKED
}
