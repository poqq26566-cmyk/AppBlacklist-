package com.example.appblacklist

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppListAdapter
    private var fullList: List<AppEntity> = emptyList()

    // 0=全部 1=已拉黑 2=未拉黑
    private var filterMode = 0

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

        AppDatabase.getInstance(this).appDao().getAll().observe(this) { list ->
            fullList = list
            applyFilter()
        }

        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val rgFilter = findViewById<RadioGroup>(R.id.rgFilter)
        rgFilter.setOnCheckedChangeListener { _, checkedId ->
            filterMode = when (checkedId) {
                R.id.rbBlacklisted -> 1
                R.id.rbNotBlacklisted -> 2
                else -> 0
            }
            applyFilter()
        }

        lifecycleScope.launch {
            AppScanner.syncInstalledApps(this@MainActivity)
        }
    }

    private fun applyFilter() {
        val keyword = findViewById<EditText>(R.id.etSearch).text.toString().trim()

        var result = fullList

        // 先按拉黑状态筛选
        result = when (filterMode) {
            1 -> result.filter { it.isBlacklisted }
            2 -> result.filter { !it.isBlacklisted }
            else -> result
        }

        // 再按关键词筛选
        if (keyword.isNotEmpty()) {
            result = result.filter {
                it.appName.contains(keyword, ignoreCase = true) ||
                it.packageName.contains(keyword, ignoreCase = true)
            }
        }

        adapter.submitList(result)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            AppScanner.syncInstalledApps(this@MainActivity)
        }
    }
}
