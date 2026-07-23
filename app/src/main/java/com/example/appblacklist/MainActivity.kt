package com.example.appblacklist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppListAdapter
    private var fullList: List<AppEntity> = emptyList()
    private var filterMode = 0

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                val count = BlacklistExportImport.export(this@MainActivity, uri)
                Toast.makeText(this@MainActivity, "已导出 $count 条黑名单记录", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                val count = BlacklistExportImport.import(this@MainActivity, uri)
                Toast.makeText(this@MainActivity, "已导入 $count 条黑名单记录", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AppListAdapter(
            onCheckChanged = { app, checked ->
                lifecycleScope.launch {
                    AppDatabase.getInstance(this@MainActivity).appDao()
                        .setBlacklisted(app.packageName, checked)
                }
            },
            onRemarkChanged = { app, remark ->
                // 保存备注到数据库
                lifecycleScope.launch {
                    AppDatabase.getInstance(this@MainActivity).appDao()
                        .setRemark(app.packageName, remark)
                }
            }
        )
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

        findViewById<Button>(R.id.btnExport).setOnClickListener {
            exportLauncher.launch("blacklist_backup.json")
        }

        findViewById<Button>(R.id.btnImport).setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }

        lifecycleScope.launch {
            AppScanner.syncInstalledApps(this@MainActivity)
        }
    }

    private fun applyFilter() {
        val keyword = findViewById<EditText>(R.id.etSearch).text.toString().trim()

        var result = fullList

        result = when (filterMode) {
            1 -> result.filter { it.isBlacklisted }
            2 -> result.filter { !it.isBlacklisted }
            else -> result
        }

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
