package com.example.appblacklist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        val dao = AppDatabase.getInstance(context).appDao()

        CoroutineScope(Dispatchers.IO).launch {
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> {
                    // 重新扫描一次即可把新安装的应用写入数据库
                    AppScanner.syncInstalledApps(context)
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    // 只标记为“已卸载”，不删除记录，这样拉黑的应用卸载后依旧能在列表看到
                    dao.setInstalled(pkg, false)
                }
            }
        }
    }
}
