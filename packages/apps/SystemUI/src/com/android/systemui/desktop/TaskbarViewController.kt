/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.desktop

import android.app.ActivityManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.IWindowManager
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import com.android.systemui.desktop.DesktopTaskbar.AppInfo
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.SystemUI

/**
 * Manages the desktop taskbar visibility and state.
 *
 * This controller handles:
 * - Taskbar show/hide based on display
 * - Running apps list management
 * - Display switching (show on external, hide on phone)
 * - Communication with WindowManager for window events
 */
class TaskbarViewController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val commandQueue: CommandQueue?
) : DesktopTaskbar.TaskbarListener {

    companion object {
        private const val TAG = "TaskbarViewController"
        private const val TASKBAR_ADD_FLAGS = (
            LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_NOT_TOUCH_MODAL or
                LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        private const val TASKBAR_DIMENSIONS = 56 // dp
    }

    private var desktopTaskbar: DesktopTaskbar? = null
    private var taskbarParams: LayoutParams? = null
    private var currentDisplayId: Int = Display.DEFAULT_DISPLAY

    private val mainHandler = Handler(Looper.getMainLooper())

    // Track running apps
    private val runningApps = mutableMapOf<String, AppInfo>()
    private var focusedApp: String? = null

    // Display listener for display changes
    private val displayListener = object : Display.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == currentDisplayId) {
                hideTaskbar()
            }
        }
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == currentDisplayId) {
                updateTaskbarPosition()
            }
        }
    }

    init {
        setupTaskbar()
    }

    private fun setupTaskbar() {
        desktopTaskbar = DesktopTaskbar(context).apply {
            setTaskbarListener(this@TaskbarViewController)
        }

        val display = windowManager.defaultDisplay
        currentDisplayId = display.displayId

        // Calculate taskbar position at bottom of screen
        val displayMetrics = context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val taskbarHeightPx = (TASKBAR_DIMENSIONS * displayMetrics.density).toInt()

        taskbarParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            taskbarHeightPx,
            LayoutParams.TYPE_NAVIGATION_BAR,
            TASKBAR_ADD_FLAGS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            y = 0
            displayId = currentDisplayId
        }
    }

    /**
     * Show the taskbar on the specified display
     */
    fun showTaskbar(displayId: Int) {
        mainHandler.post {
            try {
                desktopTaskbar?.let { taskbar ->
                    taskbarParams?.displayId = displayId
                    currentDisplayId = displayId

                    if (taskbar.parent == null) {
                        windowManager.addView(taskbar, taskbarParams)
                    }
                    taskbar.setVisible(true)

                    // Update running apps
                    updateRunningAppsInternal()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to show taskbar", e)
            }
        }
    }

    /**
     * Hide the taskbar
     */
    fun hideTaskbar() {
        mainHandler.post {
            try {
                desktopTaskbar?.let { taskbar ->
                    taskbar.setVisible(false)
                    if (taskbar.parent != null) {
                        windowManager.removeView(taskbar)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to hide taskbar", e)
            }
        }
    }

    /**
     * Update the list of running apps in the taskbar
     */
    fun updateRunningApps(apps: List<AppInfo>) {
        mainHandler.post {
            runningApps.clear()
            apps.forEach { app ->
                runningApps[app.packageName] = app
            }
            updateRunningAppsInternal()
        }
    }

    private fun updateRunningAppsInternal() {
        desktopTaskbar?.updateRunningApps(runningApps.values.toList())
    }

    /**
     * Called when an app is launched
     */
    fun onAppLaunched(packageName: String, title: String, icon: Drawable?, taskId: Int) {
        mainHandler.post {
            val appInfo = AppInfo(
                packageName = packageName,
                title = title,
                icon = icon,
                taskId = taskId,
                isMinimized = false
            )
            runningApps[packageName] = appInfo
            focusedApp = packageName

            desktopTaskbar?.apply {
                updateRunningApps(runningApps.values.toList())
                setSelectedApp(packageName)
            }
        }
    }

    /**
     * Called when an app is closed
     */
    fun onAppClosed(packageName: String) {
        mainHandler.post {
            runningApps.remove(packageName)

            if (focusedApp == packageName) {
                focusedApp = runningApps.keys.firstOrNull()
            }

            desktopTaskbar?.apply {
                updateRunningApps(runningApps.values.toList())
                setSelectedApp(focusedApp)
            }
        }
    }

    /**
     * Called when a window's state changes (minimized, maximized, etc.)
     */
    fun onWindowStateChanged(packageName: String, isMinimized: Boolean) {
        mainHandler.post {
            runningApps[packageName]?.let { app ->
                runningApps[packageName] = app.copy(isMinimized = isMinimized)
                desktopTaskbar?.updateRunningApps(runningApps.values.toList())
            }
        }
    }

    /**
     * Update taskbar position when display changes
     */
    private fun updateTaskbarPosition() {
        desktopTaskbar?.let { taskbar ->
            if (taskbar.parent != null) {
                windowManager.updateViewLayout(taskbar, taskbarParams)
            }
        }
    }

    /**
     * Update system tray information
     */
    fun updateSystemTray(time: String, batteryLevel: Int, isCharging: Boolean, notificationCount: Int) {
        mainHandler.post {
            desktopTaskbar?.updateSystemTray(time, batteryLevel, isCharging, notificationCount)
        }
    }

    /**
     * Handle desktop mode enabled/disabled
     */
    fun onDesktopModeEnabled(displayId: Int) {
        showTaskbar(displayId)
    }

    fun onDesktopModeDisabled() {
        hideTaskbar()
    }

    // DesktopTaskbar.TaskbarListener implementation

    override fun onHomeButtonClicked() {
        // Return to launcher - send home intent
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(homeIntent)
    }

    override fun onAppClicked(appInfo: AppInfo) {
        // Bring window to foreground
        bringAppToForeground(appInfo)
    }

    override fun onAppLongClicked(appInfo: AppInfo) {
        // Show window options (minimize, close) - handled by WindowDecorationController
        // For now, just minimize the window
        minimizeWindow(appInfo.taskId)
    }

    private fun bringAppToForeground(appInfo: AppInfo) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = activityManager.getTasksForUser(appInfo.taskId, 0)

            if (tasks.isNotEmpty()) {
                focusedApp = appInfo.packageName
                desktopTaskbar?.setSelectedApp(appInfo.packageName)

                // Move task to front
                val moveTaskIntent = android.content.Intent().apply {
                    action = "android.intent.action.MAIN"
                    component = tasks[0].topActivity
                    addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(moveTaskIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to bring app to foreground", e)
        }
    }

    private fun minimizeWindow(taskId: Int) {
        // This would typically communicate with the window manager
        // to minimize the specific window
        try {
            // Request window to minimize via IWindowManager
            val windowManagerService = context.getSystemService(Context.WINDOW_SERVICE) as IWindowManager
            // The actual implementation would involve AIDL calls to minimize
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to minimize window", e)
        }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        hideTaskbar()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
