package com.example.appblacklist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object AppScanner {

    suspend fun syncInstalledApps(context: Context) = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val dao = AppDatabase.getInstance(context).appDao()

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)

        val installedPackageNames = mutableSetOf<String>()
        val existingRecords = dao.getAllSync().associateBy { it.packageName }

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (installedPackageNames.contains(pkg)) continue
            installedPackageNames.add(pkg)

            val existing = existingRecords[pkg]

            if (existing == null) {
                // 新应用才需要处理图标（耗时操作），已有记录的应用跳过，加快速度
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                val iconBase64 = drawableToBase64(icon)

                dao.insert(
                    AppEntity(
                        packageName = pkg,
                        appName = label,
                        iconBase64 = iconBase64,
                        isBlacklisted = false,
                        isInstalled = true
                    )
                )
            } else if (!existing.isInstalled) {
                // 之前卸载过、现在又重新安装了，恢复状态即可，不用重新处理图标
                dao.setInstalled(pkg, true)
            }
            // 已存在且状态正常的应用什么都不做，节省时间
        }

        // 标记不在本次结果里的应用为已卸载
        for (record in existingRecords.values) {
            val stillInstalled = installedPackageNames.contains(record.packageName)
            if (record.isInstalled && !stillInstalled) {
                dao.setInstalled(record.packageName, false)
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
