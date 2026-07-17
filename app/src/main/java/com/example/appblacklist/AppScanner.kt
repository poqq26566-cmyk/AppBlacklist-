package com.example.appblacklist

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import java.io.ByteArrayOutputStream

object AppScanner {

    suspend fun syncInstalledApps(context: Context) {
        val pm = context.packageManager
        val dao = AppDatabase.getInstance(context).appDao()

        // 用launcher intent查询，取到的是桌面上真正显示的名字和图标
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)

        val installedPackageNames = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (installedPackageNames.contains(pkg)) continue
            installedPackageNames.add(pkg)

            val label = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            val iconBase64 = drawableToBase64(icon)

            val existing = dao.getByPackage(pkg)
            if (existing == null) {
                dao.insert(
                    AppEntity(
                        packageName = pkg,
                        appName = label,
                        iconBase64 = iconBase64,
                        isBlacklisted = false,
                        isInstalled = true
                    )
                )
            } else {
                existing.appName = label
                existing.iconBase64 = iconBase64
                existing.isInstalled = true
                dao.update(existing)
            }
        }

        markUninstalledApps(context, installedPackageNames)
    }

    private suspend fun markUninstalledApps(context: Context, installedPackageNames: Set<String>) {
        val dao = AppDatabase.getInstance(context).appDao()
        val allRecords = dao.getAllSync()
        for (record in allRecords) {
            val stillInstalled = installedPackageNames.contains(record.packageName)
            if (record.isInstalled != stillInstalled) {
                dao.setInstalled(record.packageName, stillInstalled)
            }
        }
    }

    private fun drawableToBase64(drawable: Drawable): String {
        val bitmap = drawableToBitmap(drawable)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
