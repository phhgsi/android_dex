package com.android.desktoplauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.app.desktop.WindowInfo
import android.app.desktop.IDesktopModeService
import com.android.internal.os.BackgroundThread

/**
 * Manager class for interfacing with DesktopModeService.
 * Provides methods for launching and managing windows in desktop mode.
 */
class DesktopWindowManager(private val context: Context) {

    companion object {
        private const val TAG = "DesktopWindowManager"
        
        @Volatile
        private var instance: DesktopWindowManager? = null
        
        fun getInstance(context: Context): DesktopWindowManager {
            return instance ?: synchronized(this) {
                instance ?: DesktopWindowManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private var desktopModeService: IDesktopModeService? = null
    private var serviceConnection: android.content.ServiceConnection? = null
    
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Initialize connection to DesktopModeService
     */
    fun initialize() {
        bindToDesktopModeService()
    }

    /**
     * Bind to DesktopModeService
     */
    private fun bindToDesktopModeService() {
        val intent = Intent().apply {
            setPackage("com.android.server.desktop")
            action = "android.app.desktop.IDesktopModeService"
        }

        serviceConnection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
                try {
                    desktopModeService = IDesktopModeService.Stub.asInterface(service)
                    Log.i(TAG, "Connected to DesktopModeService")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to DesktopModeService", e)
                }
            }

            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                desktopModeService = null
                Log.i(TAG, "Disconnected from DesktopModeService")
                
                // Try to reconnect
                mainHandler.postDelayed({
                    bindToDesktopModeService()
                }, 2000)
            }
        }

        try {
            context.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to bind to DesktopModeService", e)
        }
    }

    /**
     * Launch an app in a freeform window on the external display.
     * @param packageName The package name of the app to launch
     * @param displayId Optional display ID (defaults to external display)
     */
    fun launchAppInWindow(packageName: String, displayId: Int = -1) {
        val service = desktopModeService
        if (service == null) {
            Log.w(TAG, "DesktopModeService not available")
            // Fallback: launch normally
            launchAppNormally(packageName)
            return
        }

        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                if (displayId > 0) {
                    service.startActivityInDesktopMode(
                        intent,
                        packageName,
                        displayId,
                        WindowInfo.LAUNCH_MODE_FREE_FORM
                    )
                } else {
                    service.startActivityInDesktopMode(
                        intent,
                        packageName,
                        -1, // Use default external display
                        WindowInfo.LAUNCH_MODE_FREE_FORM
                    )
                }
                Log.i(TAG, "Launching app in window: $packageName")
            } else {
                Log.w(TAG, "No launch intent found for: $packageName")
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to launch app in window", e)
            // Fallback
            launchAppNormally(packageName)
        }
    }

    /**
     * Fallback method to launch app normally
     */
    private fun launchAppNormally(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Launching app normally: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app: $packageName", e)
        }
    }

    /**
     * Get list of currently running apps in desktop mode.
     * @return List of WindowInfo for running apps
     */
    fun getRunningApps(): List<WindowInfo> {
        val service = desktopModeService
        if (service == null) {
            Log.w(TAG, "DesktopModeService not available")
            return emptyList()
        }

        return try {
            val windowInfoList = service.windowInfos
            windowInfoList?.toList() ?: emptyList()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get running apps", e)
            emptyList()
        }
    }

    /**
     * Minimize a window by task ID.
     * @param taskId The task ID of the window to minimize
     */
    fun minimizeWindow(taskId: Int) {
        val service = desktopModeService
        if (service == null) {
            Log.w(TAG, "DesktopModeService not available")
            return
        }

        try {
            service.minimizeWindow(taskId)
            Log.i(TAG, "Minimized window: $taskId")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to minimize window", e)
        }
    }

    /**
     * Maximize a window by task ID.
     * @param taskId The task ID of the window to maximize
     */
    fun maximizeWindow(taskId: Int) {
        val service = desktopModeService
        if (service == null) {
            Log.w(TAG, "DesktopModeService not available")
            return
        }

        try {
            service.maximizeWindow(taskId)
            Log.i(TAG, "Maximized window: $taskId")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to maximize window", e)
        }
    }

    /**
     * Close a window by task ID.
     * @param taskId The task ID of the window to close
     */
    fun closeWindow(taskId: Int) {
        val service = desktopModeService
        if (service == null) {
            Log.w(TAG, "DesktopModeService not available")
            return
        }

        try {
            service.closeWindow(taskId)
            Log.i(TAG, "Closed window: $taskId")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to close window", e)
        }
    }

    /**
     * Resize a window.
     * @param taskId The task ID of the window to resize
     * @param width New width in pixels
     * @param height New height in pixels
     * @param x New x position
     * @param y New y position
     */
    fun resizeWindow(taskId: Int, width: Int, height: Int, x: Int, y: Int) {
        val service = desktopModeService
        if (service == null) {
            Log.w(TAG, "DesktopModeService not available")
            return
        }

        try {
            service.resizeWindow(taskId, width, height, x, y)
            Log.i(TAG, "Resized window: $taskId to ${width}x${height} at ($x, $y)")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to resize window", e)
        }
    }

    /**
     * Bring a window to front.
     * @param taskId The task ID of the window to bring to front
     */
    fun focusWindow(taskId: Int) {
        val service = desktopModeService
        if (service == null) {
            Log.w(TAG, "DesktopModeService not available")
            return
        }

        try {
            service.focusWindow(taskId)
            Log.i(TAG, "Focused window: $taskId")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to focus window", e)
        }
    }

    /**
     * Check if desktop mode is active
     */
    fun isDesktopModeActive(): Boolean {
        val service = desktopModeService
        if (service == null) {
            return false
        }

        return try {
            service.isDesktopModeEnabled()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to check desktop mode status", e)
            false
        }
    }

    /**
     * Get the current desktop mode state
     */
    fun getDesktopModeState(): Int {
        val service = desktopModeService
        if (service == null) {
            return 0
        }

        return try {
            service.desktopModeState
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get desktop mode state", e)
            0
        }
    }

    /**
     * Cleanup and unbind from service
     */
    fun cleanup() {
        serviceConnection?.let {
            try {
                context.unbindService(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unbind service", e)
            }
        }
        serviceConnection = null
        desktopModeService = null
    }
}
