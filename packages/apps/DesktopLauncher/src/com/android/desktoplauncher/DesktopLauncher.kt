package com.android.desktoplauncher

import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display

/**
 * Application class for Desktop Launcher.
 * Handles global initialization and display connection monitoring.
 */
class DesktopLauncher : Application(), DisplayManager.DisplayListener {

    companion object {
        private const val TAG = "DesktopLauncher"
        
        @Volatile
        private var instance: DesktopLauncher? = null
        
        fun getInstance(): DesktopLauncher {
            return instance ?: throw IllegalStateException("DesktopLauncher not initialized")
        }
        
        /** External display ID used for desktop mode */
        const val EXTERNAL_DISPLAY_ID = Display.DEFAULT_DISPLAY + 1
    }

    private lateinit var displayManager: DisplayManager
    private var displayListener: DisplayManager.DisplayListener? = null
    private var mainHandler: Handler = Handler(Looper.getMainLooper())
    
    private var isExternalDisplayConnected = false
    private var onExternalDisplayChanged: ((Boolean) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.i(TAG, "DesktopLauncher application starting...")
        
        initializeDisplayManager()
    }

    /**
     * Initialize display manager and register listener for display changes
     */
    private fun initializeDisplayManager() {
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        
        displayListener = this
        displayManager.registerDisplayListener(displayListener!!, mainHandler)
        
        // Check initial display state
        checkExternalDisplayState()
        
        Log.i(TAG, "Display manager initialized, external display connected: $isExternalDisplayConnected")
    }

    /**
     * Check if external display is available for desktop mode
     */
    private fun checkExternalDisplayState() {
        val displays = displayManager.getDisplays()
        isExternalDisplayConnected = displays.any { display ->
            display.displayId != Display.DEFAULT_DISPLAY && 
            display.state == Display.STATE_ON
        }
    }

    /**
     * Set callback for external display connection changes
     */
    fun setOnExternalDisplayChangedListener(listener: (Boolean) -> Unit) {
        onExternalDisplayChanged = listener
    }

    /**
     * Get the external display if available
     */
    fun getExternalDisplay(): Display? {
        return displayManager.getDisplay(EXTERNAL_DISPLAY_ID)
            ?: displayManager.displays.firstOrNull { 
                it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON 
            }
    }

    /**
     * Check if running on external display
     */
    fun isOnExternalDisplay(): Boolean {
        return isExternalDisplayConnected
    }

    /**
     * Check if desktop mode is available
     */
    fun isDesktopModeAvailable(): Boolean {
        return isExternalDisplayConnected
    }

    // DisplayManager.DisplayListener implementation
    
    override fun onDisplayAdded(displayId: Int) {
        Log.d(TAG, "Display added: $displayId")
        
        if (displayId != Display.DEFAULT_DISPLAY) {
            val display = displayManager.getDisplay(displayId)
            if (display != null && display.state == Display.STATE_ON) {
                isExternalDisplayConnected = true
                mainHandler.post {
                    onExternalDisplayChanged?.invoke(true)
                }
                Log.i(TAG, "External display connected: $displayId")
            }
        }
    }

    override fun onDisplayRemoved(displayId: Int) {
        Log.d(TAG, "Display removed: $displayId")
        
        if (displayId != Display.DEFAULT_DISPLAY) {
            val remainingExternal = displayManager.displays.any { 
                it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON 
            }
            
            if (!remainingExternal && isExternalDisplayConnected) {
                isExternalDisplayConnected = false
                mainHandler.post {
                    onExternalDisplayChanged?.invoke(false)
                }
                Log.i(TAG, "External display disconnected: $displayId")
            }
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        Log.d(TAG, "Display changed: $displayId")
        
        if (displayId != Display.DEFAULT_DISPLAY) {
            val display = displayManager.getDisplay(displayId)
            val wasConnected = isExternalDisplayConnected
            
            isExternalDisplayConnected = display?.state == Display.STATE_ON
            
            if (wasConnected != isExternalDisplayConnected) {
                mainHandler.post {
                    onExternalDisplayChanged?.invoke(isExternalDisplayConnected)
                }
                Log.i(TAG, "External display state changed: connected=$isExternalDisplayConnected")
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        
        displayListener?.let {
            displayManager.unregisterDisplayListener(it)
        }
        
        Log.i(TAG, "DesktopLauncher application terminated")
    }
}
