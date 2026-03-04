package com.android.desktoplauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main launcher activity that runs on the external display.
 * Displays an app grid with all installable apps and supports
 * launching apps in freeform windows.
 */
class DesktopLauncherActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DesktopLauncherActivity"
        
        // Intent action for desktop launcher category
        const val ACTION_DESKTOP_LAUNCHER = "android.intent.category.DESKTOP_LAUNCHER"
        
        // Request codes
        const val REQUEST_SETTINGS = 1001
        const val REQUEST_APP_INFO = 1002
    }

    // Views
    private lateinit var searchEditText: EditText
    private lateinit var settingsButton: ImageButton
    private lateinit var appGridRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var loadingProgress: ProgressBar

    // Managers
    private lateinit var appGridAdapter: AppGridAdapter
    private lateinit var settingsManager: SettingsManager
    private lateinit var windowManager: DesktopWindowManager

    // Data
    private val allApps = mutableListOf<AppInfo>()
    private val folders = mutableListOf<FolderInfo>()
    private var loadAppsJob: Job? = null
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up display for external monitor
        setupDisplay()
        
        setContentView(R.layout.activity_main)
        
        // Initialize managers
        settingsManager = SettingsManager.getInstance(this)
        windowManager = DesktopWindowManager.getInstance(this)
        
        // Initialize views
        initializeViews()
        
        // Set up RecyclerView
        setupRecyclerView()
        
        // Set up search
        setupSearch()
        
        // Set up settings button
        setupSettingsButton()
        
        // Load apps
        loadApps()
    }

    /**
     * Configure the activity for external display
     */
    private fun setupDisplay() {
        // Get the display we should run on
        val displayId = intent.getIntExtra(Intent.EXTRA_DISPLAY_ID, Display.DEFAULT_DISPLAY)
        
        if (displayId != Display.DEFAULT_DISPLAY) {
            val display = displayManager.getDisplay(displayId)
            if (display != null) {
                // Create window context for the external display
                val windowContext = createDisplayContext(display)
                // Use the display context for resources
                setTheme(R.style.Theme_DesktopLauncher)
            }
        }
        
        // Fullscreen settings
        window.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    /**
     * Initialize view references
     */
    private fun initializeViews() {
        searchEditText = findViewById(R.id.searchEditText)
        settingsButton = findViewById(R.id.settingsButton)
        appGridRecyclerView = findViewById(R.id.appGridRecyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        loadingProgress = findViewById(R.id.loadingProgress)
    }

    /**
     * Set up the RecyclerView with grid layout
     */
    private fun setupRecyclerView() {
        appGridAdapter = AppGridAdapter(
            onAppClick = { appInfo -> onAppClicked(appInfo) },
            onAppLongClick = { appInfo, view -> onAppLongClicked(appInfo, view) },
            onFolderClick = { folderInfo -> onFolderClicked(folderInfo) },
            onFolderLongClick = { folderInfo, view -> onFolderLongClicked(folderInfo, view) }
        )

        val columns = settingsManager.getGridColumns()
        
        appGridRecyclerView.apply {
            layoutManager = GridLayoutManager(this@DesktopLauncherActivity, columns)
            adapter = appGridAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * Set up search functionality
     */
    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                filterApps(query)
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * Set up settings button click handler
     */
    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            openSettings()
        }
    }

    /**
     * Load all apps from PackageManager
     */
    private fun loadApps() {
        loadAppsJob?.cancel()
        
        loadingProgress.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        
        loadAppsJob = coroutineScope.launch {
            val apps = withContext(Dispatchers.IO) {
                queryInstallableApps()
            }
            
            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(apps)
                
                // Sort apps
                sortApps()
                
                // Update adapter
                appGridAdapter.submitAppList(allApps)
                
                loadingProgress.visibility = View.GONE
                
                if (allApps.isEmpty()) {
                    emptyStateLayout.visibility = View.VISIBLE
                }
                
                Log.i(TAG, "Loaded ${allApps.size} apps")
            }
        }
    }

    /**
     * Query all apps that can be launched
     */
    private fun queryInstallableApps(): List<AppInfo> {
        val pm = packageManager
        val apps = mutableListOf<AppInfo>()
        
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        
        for (resolveInfo in resolveInfos) {
            // Skip our own app
            if (resolveInfo.activityInfo.packageName == packageName) {
                continue
            }
            
            // Check if should show system apps
            val isSystemApp = (resolveInfo.activityInfo.applicationInfo?.flags 
                ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
            
            if (!settingsManager.getShowSystemApps() && isSystemApp) {
                continue
            }
            
            val appInfo = AppInfo(
                packageName = resolveInfo.activityInfo.packageName,
                appName = resolveInfo.loadLabel(pm).toString(),
                icon = resolveInfo.loadIcon(pm),
                launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        component = resolveInfo.activityInfo.componentName
                    },
                isSystemApp = isSystemApp,
                isEnabled = resolveInfo.activityInfo.enabled,
                versionName = resolveInfo.activityInfo.applicationInfo?.let {
                    pm.getPackageInfo(it.packageName, 0)?.versionName
                },
                versionCode = resolveInfo.activityInfo.applicationInfo?.let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        it.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        it.versionCode.toLong()
                    }
                } ?: 0
            )
            
            apps.add(appInfo)
        }
        
        return apps
    }

    /**
     * Sort apps based on settings
     */
    private fun sortApps() {
        when (settingsManager.getSortOrder()) {
            SettingsManager.SortOrder.NAME -> {
                allApps.sortBy { it.appName.lowercase() }
            }
            SettingsManager.SortOrder.INSTALL_DATE -> {
                allApps.sortByDescending { 
                    it.launchIntent.`package`?.let { pkg ->
                        try {
                            packageManager.getPackageInfo(pkg, 0).firstInstallTime
                        } catch (e: Exception) {
                            0L
                        }
                    } ?: 0L
                }
            }
            SettingsManager.SortOrder.LAST_UPDATE -> {
                allApps.sortByDescending {
                    it.launchIntent.`package`?.let { pkg ->
                        try {
                            packageManager.getPackageInfo(pkg, 0).lastUpdateTime
                        } catch (e: Exception) {
                            0L
                        }
                    } ?: 0L
                }
            }
            SettingsManager.SortOrder.SIZE -> {
                allApps.sortBy {
                    it.launchIntent.`package`?.let { pkg ->
                        try {
                            packageManager.getPackageInfo(pkg, 0).applicationInfo?.let {
                                pm.getApplicationInfo(it.packageName, 0).enabled
                            }
                            0L
                        } catch (e: Exception) {
                            0L
                        }
                    } ?: 0L
                }
            }
        }
    }

    /**
     * Filter apps based on search query
     */
    private fun filterApps(query: String) {
        if (query.isEmpty()) {
            appGridAdapter.submitItems(getAllItems())
            return
        }
        
        val lowerQuery = query.lowercase()
        
        val filteredApps = allApps.filter { 
            it.appName.lowercase().contains(lowerQuery)
        }
        
        appGridAdapter.submitAppList(filteredApps)
    }

    /**
     * Get all items (apps + folders) for display
     */
    private fun getAllItems(): List<LauncherItem> {
        val items = mutableListOf<LauncherItem>()
        
        // Add folders first
        folders.forEach { items.add(LauncherItem.FolderItem(it)) }
        
        // Then add apps
        allApps.forEach { items.add(LauncherItem.AppItem(it)) }
        
        return items
    }

    /**
     * Handle app item click
     */
    private fun onAppClicked(appInfo: AppInfo) {
        Log.i(TAG, "App clicked: ${appInfo.appName}")
        
        // Try to launch in freeform window
        if (settingsManager.getAutoLaunch()) {
            windowManager.launchAppInWindow(appInfo.packageName)
        } else {
            // Launch normally
            try {
                appInfo.launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(appInfo.launchIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch app: ${appInfo.packageName}", e)
                Toast.makeText(this, "Failed to launch ${appInfo.appName}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Handle app item long click - show app info
     */
    private fun onAppLongClicked(appInfo: AppInfo, view: View) {
        Log.i(TAG, "App long clicked: ${appInfo.appName}")
        showAppInfoDialog(appInfo)
    }

    /**
     * Handle folder item click
     */
    private fun onFolderClicked(folderInfo: FolderInfo) {
        Log.i(TAG, "Folder clicked: ${folderInfo.name}")
        // Show folder contents
        showFolderContents(folderInfo)
    }

    /**
     * Handle folder item long click
     */
    private fun onFolderLongClicked(folderInfo: FolderInfo, view: View) {
        Log.i(TAG, "Folder long clicked: ${folderInfo.name}")
        // Show folder options
        showFolderOptionsDialog(folderInfo)
    }

    /**
     * Show app info dialog
     */
    private fun showAppInfoDialog(appInfo: AppInfo) {
        val items = arrayOf(
            getString(R.string.launch_app),
            getString(R.string.app_info),
            getString(R.string.uninstall)
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.DialogStyle)
            .setTitle(appInfo.appName)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> onAppClicked(appInfo)
                    1 -> showAppDetails(appInfo)
                    2 -> uninstallApp(appInfo)
                }
            }
            .show()
    }

    /**
     * Show app details in settings
     */
    private fun showAppDetails(appInfo: AppInfo) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${appInfo.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show app details", e)
        }
    }

    /**
     * Uninstall the app
     */
    private fun uninstallApp(appInfo: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = android.net.Uri.parse("package:${appInfo.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall app", e)
        }
    }

    /**
     * Show folder contents
     */
    private fun showFolderContents(folderInfo: FolderInfo) {
        // For now, just launch the first app in the folder
        if (folderInfo.apps.isNotEmpty()) {
            onAppClicked(folderInfo.apps.first())
        }
    }

    /**
     * Show folder options dialog
     */
    private fun showFolderOptionsDialog(folderInfo: FolderInfo) {
        val items = arrayOf(
            getString(R.string.open_folder),
            getString(R.string.rename_folder),
            getString(R.string.delete_folder)
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.DialogStyle)
            .setTitle(folderInfo.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showFolderContents(folderInfo)
                    1 -> renameFolder(folderInfo)
                    2 -> deleteFolder(folderInfo)
                }
            }
            .show()
    }

    /**
     * Rename a folder
     */
    private fun renameFolder(folderInfo: FolderInfo) {
        // TODO: Implement folder rename
        Toast.makeText(this, "Rename folder: ${folderInfo.name}", Toast.LENGTH_SHORT).show()
    }

    /**
     * Delete a folder
     */
    private fun deleteFolder(folderInfo: FolderInfo) {
        folders.remove(folderInfo)
        appGridAdapter.submitItems(getAllItems())
        Toast.makeText(this, "Folder deleted: ${folderInfo.name}", Toast.LENGTH_SHORT).show()
    }

    /**
     * Open settings activity
     */
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivityForResult(intent, REQUEST_SETTINGS)
    }

    override fun onResume() {
        super.onResume()
        
        // Re-initialize window manager
        windowManager.initialize()
        
        // Reload apps in case of changes
        if (allApps.isEmpty()) {
            loadApps()
        }
    }

    override fun onPause() {
        super.onPause()
        loadAppsJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.cleanup()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_SETTINGS -> {
                // Reload apps with new settings
                loadApps()
            }
        }
    }

    /**
     * Handle back press - minimize launcher instead of finishing
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Move task to background instead of finishing
        moveTaskToBack(true)
    }
}
