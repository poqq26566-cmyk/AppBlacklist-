package com.example.appblacklist

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object BlacklistExportImport {

    // 导出：把所有拉黑记录写成 JSON 数组存到用户选择的文件里
    suspend fun export(context: Context, uri: Uri): Int {
        val dao = AppDatabase.getInstance(context).appDao()
        val blacklisted = dao.getBlacklistedSync()

        val jsonArray = JSONArray()
        for (item in blacklisted) {
            val obj = JSONObject()
            obj.put("packageName", item.packageName)
            obj.put("appName", item.appName)
            obj.put("iconBase64", item.iconBase64)
            obj.put("remark", item.remark)   // 备注一并导出
            jsonArray.put(obj)
        }

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(jsonArray.toString().toByteArray(Charsets.UTF_8))
        }
        return blacklisted.size
    }

    // 导入：读取 JSON 文件，把里面的应用都标记为拉黑并写入/更新数据库
    suspend fun import(context: Context, uri: Uri): Int {
        val dao = AppDatabase.getInstance(context).appDao()
        val pm = context.packageManager

        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return 0

        val jsonArray = JSONArray(text)
        var count = 0

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val pkg = obj.getString("packageName")
            val appName = obj.getString("appName")
            val iconBase64 = obj.getString("iconBase64")
            val remark = if (obj.has("remark")) obj.getString("remark") else ""  // 兼容旧版导出文件

            val isInstalled = try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }

            val existing = dao.getByPackage(pkg)
            if (existing == null) {
                dao.insert(
                    AppEntity(
                        packageName = pkg,
                        appName = appName,
                        iconBase64 = iconBase64,
                        isBlacklisted = true,
                        isInstalled = isInstalled,
                        remark = remark
                    )
                )
            } else {
                dao.setBlacklisted(pkg, true)
                if (remark.isNotBlank()) {
                    dao.setRemark(pkg, remark)  // 导入时若有备注则更新
                }
            }
            count++
        }
        return count
    }
}
