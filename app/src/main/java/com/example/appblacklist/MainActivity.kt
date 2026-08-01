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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppListAdapter
    private var fullList: List<AppEntity> = emptyList()
    private var filterMode = 0

    // 系统不允许第三方 App 静默批量卸载，只能一个一个弹系统确认框；
    // 这个队列记录还剩哪些包名没处理，每次用户确认/取消一个卸载弹窗后，
    // 自动继续弹下一个，实现"连续卸载"的体验。
    private val uninstallQueue = ArrayDeque<String>()

    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // resultCode: RESULT_OK(-1) 表示用户确认卸载了，RESULT_CANCELED(0) 表示取消了。
        // 加这个提示主要是为了排查"系统卸载确认框到底有没有弹出来"这种问题。
        when (result.resultCode) {
            RESULT_OK -> Unit // 用户确认卸载，不用额外提示，能在列表里看到状态变化
            RESULT_CANCELED -> Toast.makeText(this, "已取消这一个的卸载", Toast.LENGTH_SHORT).show()
        }
        triggerNextUninstall()
    }

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

        findViewById<Button>(R.id.btnUninstallBlacklisted).setOnClickListener {
            startUninstallBlacklisted()
        }

        lifecycleScope.launch {
            AppScanner.syncInstalledApps(this@MainActivity)
        }
    }

    private fun startUninstallBlacklisted() {
        // 只卸载"已拉黑 且 当前确实还装着"的应用，已经卸载过的记录不需要再处理
        val targets = fullList.filter { it.isBlacklisted && it.isInstalled }
        if (targets.isEmpty()) {
            Toast.makeText(this, "没有已安装的拉黑应用", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("卸载已拉黑的应用")
            .setMessage(
                "共 ${targets.size} 个已拉黑的应用当前已安装。\n" +
                    "系统不允许一次性静默卸载，需要对每个应用逐一确认，" +
                    "接下来会连续弹出 ${targets.size} 次系统卸载确认框，确定要开始吗？"
            )
            .setPositiveButton("开始卸载") { _, _ ->
                uninstallQueue.clear()
                uninstallQueue.addAll(targets.map { it.packageName })
                triggerNextUninstall()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun triggerNextUninstall() {
        val packageName = uninstallQueue.removeFirstOrNull()
        if (packageName == null) {
            Toast.makeText(this, "已拉黑应用的卸载流程结束", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        try {
            uninstallLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法卸载 $packageName：${e.message}", Toast.LENGTH_LONG).show()
            triggerNextUninstall()
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
