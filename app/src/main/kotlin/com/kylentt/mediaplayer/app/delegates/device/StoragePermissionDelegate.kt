package com.kylentt.mediaplayer.app.delegates.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.kylentt.mediaplayer.app.delegates.AppDelegate
import kotlin.reflect.KProperty

/**
 * Delegate to check Storage Permission Status, the required Permission for the App to function properly
 * gets the Context from [AppDelegate] Class
 * @return [Boolean] true if both [checkReadStoragePermission] and [checkWriteStoragePermission] is true
 * @see [DeviceWallpaperDelegate]
 * @author Kylentt
 * @since 2022/04/30
 */

object StoragePermissionDelegate {

  const val Read_External_Storage = Manifest.permission.READ_EXTERNAL_STORAGE
  const val Write_External_Storage = Manifest.permission.WRITE_EXTERNAL_STORAGE

  @JvmStatic fun checkReadStoragePermission(context: Context): Boolean {
    return ContextCompat
      .checkSelfPermission(context, Read_External_Storage) == PackageManager.PERMISSION_GRANTED
  }

  @JvmStatic fun checkWriteStoragePermission(context: Context): Boolean {
    return ContextCompat
      .checkSelfPermission(context, Write_External_Storage) == PackageManager.PERMISSION_GRANTED
  }

  operator fun getValue(appDelegate: AppDelegate, property: KProperty<*>): Boolean {
    return checkReadStoragePermission(appDelegate.base) and
      checkWriteStoragePermission(appDelegate.base)
  }

  operator fun getValue(any: Any?, property: KProperty<*>): Boolean {
    return AppDelegate.hasStoragePermission
  }
}
