package com.example.appblacklist

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import java.io.ByteArrayOutputStream

object AppScanner {

    /**
     * 扫描本机已安装的应用，写入/更新数据库。
     * 已经在数据库里但本次没扫描到的应用（说明被卸载了），
     * 只将 isInstalled 置为 false，不删除记录，这样黑名单里还能继续显示。
     */
    suspend fun syncInstalledApps(context: Context) {
        val pm = context.packageManager
        val dao = AppDatabase.getInstance(context).appDao()

        val installedApps: List<ApplicationInfo> = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val installedPackageNames = mutableSetOf<String>()

        for (appInfo in installedApps) {
            val pkg = appInfo.packageName
            installedPackageNames.add(pkg)

            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
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

        // 标记数据库里那些不在本次扫描结果中的应用为“已卸载”，但保留记录（尤其是拉黑的）
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
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
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
