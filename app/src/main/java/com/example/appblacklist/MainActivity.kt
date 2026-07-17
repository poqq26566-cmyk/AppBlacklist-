package com.example.appblacklist

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppListAdapter
    private var fullList: List<AppEntity> = emptyList()

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

        // 观察数据库变化，保存完整列表并应用当前搜索关键词
        AppDatabase.getInstance(this).appDao().getAll().observe(this) { list ->
            fullList = list
            applyFilter(findViewById<EditText>(R.id.etSearch).text.toString())
        }

        // 搜索框监听输入，实时过滤
        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lifecycleScope.launch {
            AppScanner.syncInstalledApps(this@MainActivity)
        }
    }

    private fun applyFilter(keyword: String) {
        val trimmed = keyword.trim()
        val filtered = if (trimmed.isEmpty()) {
            fullList
        } else {
            fullList.filter {
                it.appName.contains(trimmed, ignoreCase = true) ||
                it.packageName.contains(trimmed, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            AppScanner.syncInstalledApps(this@MainActivity)
        }
    }
}
