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
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.IWindowManager
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import com.android.systemui.desktop.WindowDecorationView.WindowDecorationListener
import com.android.systemui.desktop.WindowDecorationView.WindowEdge
import android.view.WindowInfo as AndroidWindowInfo

/**
 * Manages window decorations lifecycle for desktop mode windows.
 *
 * This controller handles:
 * - Attaching decorations to windows
 * - Updating window titles
 * - Window state management (minimized, maximized, normal)
 * - Touch events for dragging/resizing
 */
class WindowDecorationController(
    private val context: Context,
    private val windowManager: WindowManager
) {

    companion object {
        private const val TAG = "WindowDecorationController"
        private const val MIN_WINDOW_WIDTH = 320 // dp
        private const val MIN_WINDOW_HEIGHT = 240 // dp
    }

    /**
     * Window state enum
     */
    enum class WindowState {
        NORMAL,
        MINIMIZED,
        MAXIMIZED,
        FULLSCREEN
    }

    /**
     * Data class to hold window decoration state
     */
    data class WindowDecoration(
        val taskId: Int,
        val token: IBinder,
        var decorationView: WindowDecorationView,
        var params: LayoutParams,
        var state: WindowState = WindowState.NORMAL,
        var title: String = "",
        var packageName: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val decorations = mutableMapOf<Int, WindowDecoration>()

    private var displayMetrics: DisplayMetrics
    private var windowManagerService: IWindowManager? = null

    init {
        displayMetrics = context.resources.displayMetrics

        // Get WindowManager service
        try {
            val windowManagerBinder = context.getSystemService(Context.WINDOW_SERVICE)
            // Note: In real SystemUI, this would be obtained differently
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get WindowManager", e)
        }
    }

    /**
     * Attach a window decoration to a window token
     */
    fun attachDecoration(
        taskId: Int,
        token: IBinder,
        packageName: String,
        initialTitle: String = ""
    ): WindowDecorationView? {
        return mainHandler.post@ {
            try {
                // Check if decoration already exists for this task
                if (decorations.containsKey(taskId)) {
                    android.util.Log.w(TAG, "Decoration already exists for task $taskId")
                    return@post decorations[taskId]?.decorationView
                }

                // Get app icon
                val icon = getAppIcon(packageName)

                // Create the decoration view
                val decorationView = WindowDecorationView(context).apply {
                    setAppIcon(icon)
                    setWindowTitle(initialTitle)
                    setWindowDecorationListener(createDecorationListener(taskId))

                    // Set content view placeholder (actual window content would be injected)
                }

                // Calculate initial window dimensions
                val initialWidth = (600 * displayMetrics.density).toInt()
                val initialHeight = (400 * displayMetrics.density).toInt()

                // Create window layout params
                val params = LayoutParams(
                    initialWidth,
                    initialHeight,
                    LayoutParams.TYPE_APPLICATION,
                    (
                        LayoutParams.FLAG_NOT_FOCUSABLE or
                            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    ),
                    android.graphics.PixelFormat.TRANSLUCENT
                ).apply {
                    token = token
                    title = initialTitle
                }

                // Add the decoration view to window manager
                windowManager.addView(decorationView, params)

                // Store decoration state
                val decoration = WindowDecoration(
                    taskId = taskId,
                    token = token,
                    decorationView = decorationView,
                    params = params,
                    packageName = packageName,
                    title = initialTitle
                )
                decorations[taskId] = decoration

                decorationView
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to attach decoration for task $taskId", e)
                null
            }
        } as? WindowDecorationView
    }

    /**
     * Update the window title
     */
    fun updateTitle(taskId: Int, title: String) {
        mainHandler.post {
            decorations[taskId]?.let { decoration ->
                decoration.title = title
                decoration.decorationView.setWindowTitle(title)
                decoration.params.title = title
                windowManager.updateViewLayout(decoration.decorationView, decoration.params)
            }
        }
    }

    /**
     * Set window state (MINIMIZED, MAXIMIZED, NORMAL)
     */
    fun setWindowState(taskId: Int, state: WindowState) {
        mainHandler.post {
            decorations[taskId]?.let { decoration ->
                decoration.state = state
                applyWindowState(decoration, state)
            }
        }
    }

    private fun applyWindowState(decoration: WindowDecoration, state: WindowState) {
        when (state) {
            WindowState.MINIMIZED -> {
                // Hide the window decoration
                decoration.decorationView.visibility = View.GONE
                // In a real implementation, we would communicate with WindowManager
                // to actually minimize the underlying window
            }
            WindowState.MAXIMIZED -> {
                // Maximize to fill the desktop area (excluding taskbar)
                val display = windowManager.defaultDisplay
                val metrics = android.util.DisplayMetrics()
                display.getRealMetrics(metrics)

                val taskbarHeight = (56 * displayMetrics.density).toInt() // 56dp taskbar

                decoration.params.apply {
                    width = metrics.widthPixels
                    height = metrics.heightPixels - taskbarHeight
                    x = 0
                    y = 0
                    gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
                }

                decoration.decorationView.apply {
                    setWindowState(true)
                    setResizeHandlesVisible(false)
                }

                windowManager.updateViewLayout(decoration.decorationView, decoration.params)
            }
            WindowState.NORMAL -> {
                // Restore to previous size
                decoration.decorationView.apply {
                    visibility = View.VISIBLE
                    setWindowState(false)
                    setResizeHandlesVisible(true)
                }
            }
            WindowState.FULLSCREEN -> {
                // Fullscreen (no decorations, covering everything)
                val display = windowManager.defaultDisplay
                val metrics = android.util.DisplayMetrics()
                display.getRealMetrics(metrics)

                decoration.params.apply {
                    width = metrics.widthPixels
                    height = metrics.heightPixels
                    x = 0
                    y = 0
                }

                decoration.decorationView.apply {
                    visibility = View.GONE // Hide decorations in fullscreen
                }

                windowManager.updateViewLayout(decoration.decorationView, decoration.params)
            }
        }
    }

    /**
     * Detach and remove window decoration
     */
    fun detachDecoration(taskId: Int) {
        mainHandler.post {
            decorations[taskId]?.let { decoration ->
                try {
                    windowManager.removeView(decoration.decorationView)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to remove decoration view", e)
                }
                decorations.remove(taskId)
            }
        }
    }

    /**
     * Update window position (called from drag)
     */
    fun updateWindowPosition(taskId: Int, deltaX: Float, deltaY: Float) {
        mainHandler.post {
            decorations[taskId]?.let { decoration ->
                decoration.params.apply {
                    x += deltaX.toInt()
                    y += deltaY.toInt()
                }
                windowManager.updateViewLayout(decoration.decorationView, decoration.params)
            }
        }
    }

    /**
     * Update window size (called from resize)
     */
    fun updateWindowSize(taskId: Int, edge: Int, delta: Float) {
        mainHandler.post {
            decorations[taskId]?.let { decoration ->
                val density = displayMetrics.density
                val deltaPx = (delta * density).toInt()

                decoration.params.apply {
                    when (edge and WindowEdge.LEFT) {
                        WindowEdge.LEFT -> {
                            width -= deltaPx
                            x += deltaPx
                        }
                    }
                    when (edge and WindowEdge.RIGHT) {
                        WindowEdge.RIGHT -> width += deltaPx
                    }
                    when (edge and WindowEdge.TOP) {
                        WindowEdge.TOP -> {
                            height -= deltaPx
                            y += deltaPx
                        }
                    }
                    when (edge and WindowEdge.BOTTOM) {
                        WindowEdge.BOTTOM -> height += deltaPx
                    }

                    // Enforce minimum size
                    val minWidth = (MIN_WINDOW_WIDTH * density).toInt()
                    val minHeight = (MIN_WINDOW_HEIGHT * density).toInt()
                    if (width < minWidth) {
                        width = minWidth
                    }
                    if (height < minHeight) {
                        height = minHeight
                    }
                }

                windowManager.updateViewLayout(decoration.decorationView, decoration.params)
            }
        }
    }

    /**
     * Get app icon for package
     */
    private fun getAppIcon(packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Create decoration listener for a task
     */
    private fun createDecorationListener(taskId: Int): WindowDecorationListener {
        return object : WindowDecorationListener {
            override fun onWindowMove(deltaX: Float, deltaY: Float) {
                updateWindowPosition(taskId, deltaX, deltaY)
            }

            override fun onWindowResize(edge: Int, delta: Float) {
                updateWindowSize(taskId, edge, delta)
            }

            override fun onMinimizeClicked() {
                setWindowState(taskId, WindowState.MINIMIZED)
            }

            override fun onMaximizeClicked() {
                val currentState = decorations[taskId]?.state
                if (currentState == WindowState.MAXIMIZED) {
                    setWindowState(taskId, WindowState.NORMAL)
                } else {
                    setWindowState(taskId, WindowState.MAXIMIZED)
                }
            }

            override fun onCloseClicked() {
                closeWindow(taskId)
            }

            override fun onMoveStart() {
                // Could notify SystemUI of drag start for visual feedback
            }

            override fun onMoveEnd() {
                // Could notify SystemUI of drag end
            }
        }
    }

    /**
     * Close a window
     */
    private fun closeWindow(taskId: Int) {
        // This would communicate with ActivityManager to finish the task
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // activityManager.removeTask(taskId) - in real implementation
            detachDecoration(taskId)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to close window", e)
        }
    }

    /**
     * Get all active decorations
     */
    fun getDecorations(): Map<Int, WindowDecoration> = decorations.toMap()

    /**
     * Get decoration for specific task
     */
    fun getDecoration(taskId: Int): WindowDecoration? = decorations[taskId]

    /**
     * Clean up all decorations
     */
    fun destroy() {
        mainHandler.post {
            val taskIds = decorations.keys.toList()
            taskIds.forEach { taskId ->
                detachDecoration(taskId)
            }
            mainHandler.removeCallbacksAndMessages(null)
        }
    }
}
