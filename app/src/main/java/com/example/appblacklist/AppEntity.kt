package com.example.appblacklist

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用记录：即使应用被卸载，这条记录（含图标）依然保留在数据库中，
 * 所以拉黑过的应用即使卸载了，列表里依旧能看到它的名称、包名和图标。
 */
@Entity(tableName = "app_records")
data class AppEntity(
    @PrimaryKey val packageName: String,
    var appName: String,
    var iconBase64: String,      // 图标以 Base64 字符串形式持久化保存
    var isBlacklisted: Boolean = false,
    var isInstalled: Boolean = true,
    var remark: String = ""      // 备注，默认为空字符串
)
