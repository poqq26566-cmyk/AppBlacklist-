package com.example.appblacklist

import android.content.Context
import android.content.pm.ApplicationInfo
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

        // 改用 getInstalledApplications 而不是只查带桌面图标的 launcher 应用，
        // 这样才能扫到没有启动图标的系统组件/系统服务，
        // 是否为系统应用通过 ApplicationInfo.flags 判断，交给列表用一个开关来过滤显示。
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val installedPackageNames = mutableSetOf<String>()
        val existingRecords = dao.getAllSync().associateBy { it.packageName }

        for (appInfo in installedApps) {
            val pkg = appInfo.packageName
            if (installedPackageNames.contains(pkg)) continue
            installedPackageNames.add(pkg)

            val isSystemApp = isSystemApp(appInfo)
            val existing = existingRecords[pkg]

            if (existing == null) {
                // 新应用才需要处理图标（耗时操作），已有记录的应用跳过，加快速度
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg
                }
                val iconBase64 = try {
                    drawableToBase64(pm.getApplicationIcon(appInfo))
                } catch (e: Exception) {
                    ""
                }

                dao.insert(
                    AppEntity(
                        packageName = pkg,
                        appName = label,
                        iconBase64 = iconBase64,
                        isBlacklisted = false,
                        isInstalled = true,
                        isSystemApp = isSystemApp
                    )
                )
            } else {
                if (!existing.isInstalled) {
                    // 之前卸载过、现在又重新安装了，恢复状态即可，不用重新处理图标
                    dao.setInstalled(pkg, true)
                }
                if (existing.isSystemApp != isSystemApp) {
                    // 系统标记有变化时才更新（极少发生，例如系统应用被"更新"过）
                    dao.setSystemApp(pkg, isSystemApp)
                }
            }
            // 其余情况：已存在、状态正常、系统标记也没变，什么都不做，节省时间
        }

        // 标记不在本次结果里的应用为已卸载
        for (record in existingRecords.values) {
            val stillInstalled = installedPackageNames.contains(record.packageName)
            if (record.isInstalled && !stillInstalled) {
                dao.setInstalled(record.packageName, false)
            }
        }
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        val isBuiltIn = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return isBuiltIn || isUpdatedSystemApp
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
