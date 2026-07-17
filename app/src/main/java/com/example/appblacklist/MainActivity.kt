package com.example.appblacklist

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AppListAdapter { app, checked ->
            lifecycleScope.launch {
                AppDatabase.getInstance(this@MainActivity).appDao().setBlacklisted(app.packageName, checked)
            }
        }
        recyclerView.adapter = adapter

        // 观察数据库变化，自动刷新列表（包括拉黑状态、卸载状态的变化）
        AppDatabase.getInstance(this).appDao().getAll().observe(this) { list ->
            adapter.submitList(list)
        }

        // 首次进入扫描已安装应用，写入/更新数据库
        lifecycleScope.launch {
            AppScanner.syncInstalledApps(this@MainActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台重新扫描一次，保证安装/卸载状态是最新的
        lifecycleScope.launch {
            AppScanner.syncInstalledApps(this@MainActivity)
        }
    }
}
