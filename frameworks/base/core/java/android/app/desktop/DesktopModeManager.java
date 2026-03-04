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

package android.app.desktop;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * Public API for applications to interact with Desktop Mode.
 *
 * Desktop Mode allows Android devices to provide a desktop-like experience
 * when connected to an external display. This manager provides methods to
 * query and control desktop mode state, as well as manage windows.
 *
 * <p>Example usage:
 * <pre>{@code
 * DesktopModeManager dm = context.getSystemService(DesktopModeManager.class);
 *
 * // Check if desktop mode is enabled
 * if (dm.isDesktopModeEnabled()) {
 *     int displayId = dm.getDesktopDisplayId();
 *     // Use desktop display
 * }
 *
 * // Register for state changes
 * dm.addOnDesktopModeStateListener(executor, listener);
 * }</pre>
 *
 * @hide
 */
@SystemService(Context.DESKTOP_MODE_SERVICE)
public final class DesktopModeManager {

    private static final String TAG = "DesktopModeManager";

    private static final String SERVICE_ACTION = "android.app.desktop.IDesktopModeService";

    private final Context mContext;
    private final IDesktopModeService mService;

    private final ArrayMap<DesktopModeStateListener, ListenerWrapper> mListeners = new ArrayMap<>();

    /**
     * Listener interface for desktop mode state changes.
     *
     * @hide
     */
    public interface DesktopModeStateListener {

        /**
         * Called when desktop mode is enabled or disabled.
         *
         * @param enabled true if desktop mode is now enabled
         * @param displayId the desktop display ID, or -1 if disabled
         */
        void onDesktopModeStateChanged(boolean enabled, int displayId);

        /**
         * Called when desktop mode state transitions.
         *
         * @param state the new desktop mode state
         */
        default void onDesktopModeState(int state) {
            // Default implementation does nothing
        }

        /**
         * Called when desktop display configuration changes.
         *
         * @param config the new display configuration
         */
        default void onDesktopDisplayConfigChanged(DesktopDisplayConfig config) {
            // Default implementation does nothing
        }
    }

    private static class ListenerWrapper {
        final DesktopModeStateListener listener;
        final Executor executor;

        ListenerWrapper(DesktopModeStateListener listener, Executor executor) {
            this.listener = listener;
            this.executor = executor;
        }
    }

    private final IDesktopModeListener mBinderListener = new IDesktopModeListener.Stub() {
        @Override
        public void onDesktopModeEnabled(int displayId) {
            notifyStateChanged(true, displayId);
        }

        @Override
        public void onDesktopModeDisabled() {
            notifyStateChanged(false, -1);
        }

        @Override
        public void onDesktopModeStateChanged(int state) {
            // Forward to listeners if they implement the extended interface
        }

        @Override
        public void onWindowAdded(android.app.desktop.WindowInfo windowInfo) {
            // Not handled by DesktopModeStateListener
        }

        @Override
        public void onWindowRemoved(int taskId) {
            // Not handled by DesktopModeStateListener
        }

        @Override
        public void onWindowFocusChanged(int taskId, boolean hasFocus) {
            // Not handled by DesktopModeStateListener
        }

        @Override
        public void onWindowVisibilityChanged(int taskId, boolean visible) {
            // Not handled by DesktopModeStateListener
        }

        @Override
        public void onWindowBoundsChanged(int taskId, android.graphics.Rect bounds) {
            // Not handled by DesktopModeStateListener
        }

        @Override
        public void onDesktopDisplayConnected(int displayId, DesktopDisplayConfig config) {
            // Not handled by DesktopModeStateListener
        }

        @Override
        public void onDesktopDisplayDisconnected(int displayId) {
            // Not handled by DesktopModeStateListener
        }

        @Override
        public void onDesktopDisplayConfigChanged(int displayId, DesktopDisplayConfig config) {
            // Forward to listeners
            for (ListenerWrapper wrapper : mListeners.values()) {
                final DesktopDisplayConfig finalConfig = config;
                wrapper.executor.execute(() -> {
                    wrapper.listener.onDesktopDisplayConfigChanged(finalConfig);
                });
            }
        }

        @Override
        public void onTaskbarVisibilityChanged(boolean visible) {
            // Not handled by DesktopModeStateListener
        }

        private void notifyStateChanged(boolean enabled, int displayId) {
            for (ListenerWrapper wrapper : mListeners.values()) {
                final boolean finalEnabled = enabled;
                final int finalDisplayId = displayId;
                wrapper.executor.execute(() -> {
                    wrapper.listener.onDesktopModeStateChanged(finalEnabled, finalDisplayId);
                });
            }
        }
    };

    /** @hide */
    public DesktopModeManager(Context context, IDesktopModeService service) {
        mContext = context;
        mService = service;
    }

    /**
     * Checks if desktop mode is currently enabled.
     *
     * @return true if desktop mode is enabled, false otherwise
     */
    public boolean isDesktopModeEnabled() {
        try {
            return mService.isDesktopModeEnabled();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to check desktop mode state", e);
            return false;
        }
    }

    /**
     * Gets the display ID of the current desktop display.
     *
     * @return The desktop display ID, or -1 if desktop mode is not enabled
     */
    public int getDesktopDisplayId() {
        try {
            return mService.getDesktopDisplayId();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get desktop display ID", e);
            return DesktopModeConstants.INVALID_DISPLAY_ID;
        }
    }

    /**
     * Gets the current desktop mode state.
     *
     * @return One of: {@link DesktopModeConstants#STATE_PHONE_MODE},
     *         {@link DesktopModeConstants#STATE_DESKTOP_ACTIVATING},
     *         {@link DesktopModeConstants#STATE_DESKTOP_MODE},
     *         {@link DesktopModeConstants#STATE_DESKTOP_DEACTIVATING},
     *         {@link DesktopModeConstants#STATE_SUSPENDED}
     */
    public int getDesktopModeState() {
        try {
            return mService.getDesktopModeState();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get desktop mode state", e);
            return DesktopModeConstants.STATE_PHONE_MODE;
        }
    }

    /**
     * Gets the current desktop display configuration.
     *
     * @return The DesktopDisplayConfig for the current desktop display,
     *         or null if desktop mode is not enabled
     */
    @Nullable
    public DesktopDisplayConfig getDesktopDisplayConfig() {
        try {
            return mService.getDesktopDisplayConfig();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get desktop display config", e);
            return null;
        }
    }

    /**
     * Requests to enter desktop mode.
     *
     * <p>This will activate desktop mode on the available external display.
     * The result is asynchronous and listeners will be notified of state changes.
     *
     * @throws SecurityException if the caller doesn't have permission to control desktop mode
     */
    @RequiresPermission(DesktopModeConstants.PERMISSION_CONTROL_DESKTOP_MODE)
    public void requestEnterDesktopMode() {
        try {
            mService.enableDesktopMode();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to enable desktop mode", e);
        }
    }

    /**
     * Requests to exit desktop mode.
     *
     * <p>This will deactivate desktop mode and return to phone UI.
     * The result is asynchronous and listeners will be notified of state changes.
     *
     * @throws SecurityException if the caller doesn't have permission to control desktop mode
     */
    @RequiresPermission(DesktopModeConstants.PERMISSION_CONTROL_DESKTOP_MODE)
    public void requestExitDesktopMode() {
        try {
            mService.disableDesktopMode();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to disable desktop mode", e);
        }
    }

    /**
     * Sets the windowing mode for a specific task.
     *
     * @param taskId The ID of the task to modify
     * @param windowingMode The windowing mode to apply
     * @return true if the operation was successful
     */
    public boolean setTaskWindowingMode(int taskId, int windowingMode) {
        try {
            return mService.setTaskWindowingMode(taskId, windowingMode);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set task windowing mode", e);
            return false;
        }
    }

    /**
     * Minimizes a specific window/task.
     *
     * @param taskId The ID of the task to minimize
     * @return true if the operation was successful
     */
    public boolean minimizeWindow(int taskId) {
        try {
            return mService.minimizeWindow(taskId);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to minimize window", e);
            return false;
        }
    }

    /**
     * Maximizes a specific window/task.
     *
     * @param taskId The ID of the task to maximize
     * @return true if the operation was successful
     */
    public boolean maximizeWindow(int taskId) {
        try {
            return mService.maximizeWindow(taskId);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to maximize window", e);
            return false;
        }
    }

    /**
     * Restores a minimized window/task.
     *
     * @param taskId The ID of the task to restore
     * @return true if the operation was successful
     */
    public boolean restoreWindow(int taskId) {
        try {
            return mService.restoreWindow(taskId);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to restore window", e);
            return false;
        }
    }

    /**
     * Closes a specific window/task.
     *
     * @param taskId The ID of the task to close
     * @return true if the operation was successful
     */
    public boolean closeWindow(int taskId) {
        try {
            return mService.closeWindow(taskId);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to close window", e);
            return false;
        }
    }

    /**
     * Resizes a window to the specified bounds.
     *
     * @param taskId The ID of the task to resize
     * @param bounds The new bounds for the window
     * @return true if the operation was successful
     */
    public boolean resizeWindow(int taskId, @NonNull Rect bounds) {
        try {
            return mService.resizeWindow(taskId, bounds);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to resize window", e);
            return false;
        }
    }

    /**
     * Moves a window to the specified position.
     *
     * @param taskId The ID of the task to move
     * @param x The new x coordinate
     * @param y The new y coordinate
     * @return true if the operation was successful
     */
    public boolean moveWindow(int taskId, int x, int y) {
        try {
            return mService.moveWindow(taskId, x, y);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to move window", e);
            return false;
        }
    }

    /**
     * Sets the bounds for a window (combines resize and move).
     *
     * @param taskId The ID of the task
     * @param bounds The new bounds for the window
     * @return true if the operation was successful
     */
    public boolean setWindowBounds(int taskId, @NonNull Rect bounds) {
        return resizeWindow(taskId, bounds);
    }

    /**
     * Gets information about a specific window.
     *
     * @param taskId The ID of the task
     * @return WindowInfo for the task, or null if not found
     */
    @Nullable
    public android.app.desktop.WindowInfo getWindowInfo(int taskId) {
        try {
            return mService.getWindowInfo(taskId);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get window info", e);
            return null;
        }
    }

    /**
     * Gets information about all desktop windows.
     *
     * @return List of WindowInfo for all desktop tasks
     */
    @NonNull
    public List<android.app.desktop.WindowInfo> getDesktopWindows() {
        try {
            return mService.getDesktopWindows();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get desktop windows", e);
            return List.of();
        }
    }

    /**
     * Sets the visibility of the desktop taskbar.
     *
     * @param visible true to show the taskbar, false to hide
     */
    public void setTaskbarVisibility(boolean visible) {
        try {
            mService.setTaskbarVisibility(visible);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set taskbar visibility", e);
        }
    }

    /**
     * Checks if the taskbar is visible.
     *
     * @return true if the taskbar is visible
     */
    public boolean isTaskbarVisible() {
        try {
            return mService.isTaskbarVisible();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to check taskbar visibility", e);
            return false;
        }
    }

    /**
     * Gets the list of available desktop displays.
     *
     * @return List of display IDs that can be used for desktop mode
     */
    @NonNull
    public List<Integer> getAvailableDesktopDisplays() {
        try {
            return mService.getAvailableDesktopDisplays();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get available desktop displays", e);
            return List.of();
        }
    }

    /**
     * Sets the preferred display for desktop mode.
     *
     * @param displayId The display ID to use for desktop mode
     * @return true if the operation was successful
     */
    public boolean setPreferredDesktopDisplay(int displayId) {
        try {
            return mService.setPreferredDesktopDisplay(displayId);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set preferred desktop display", e);
            return false;
        }
    }

    /**
     * Gets the preferred display ID for desktop mode.
     *
     * @return The preferred display ID, or -1 if not set
     */
    public int getPreferredDesktopDisplay() {
        try {
            return mService.getPreferredDesktopDisplay();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get preferred desktop display", e);
            return DesktopModeConstants.INVALID_DISPLAY_ID;
        }
    }

    /**
     * Adds a listener for desktop mode state changes.
     *
     * @param executor The executor to use for callbacks
     * @param listener The listener to add
     */
    public void addOnDesktopModeStateListener(
            @NonNull Executor executor,
            @NonNull DesktopModeStateListener listener) {
        synchronized (mListeners) {
            if (mListeners.containsKey(listener)) {
                Log.w(TAG, "Listener already registered");
                return;
            }

            if (mListeners.isEmpty()) {
                // First listener - register with service
                try {
                    mService.addDesktopModeListener(mBinderListener);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to add desktop mode listener", e);
                    return;
                }
            }

            mListeners.put(listener, new ListenerWrapper(executor, listener));
        }
    }

    /**
     * Removes a desktop mode state listener.
     *
     * @param listener The listener to remove
     */
    public void removeOnDesktopModeStateListener(@NonNull DesktopModeStateListener listener) {
        synchronized (mListeners) {
            if (!mListeners.containsKey(listener)) {
                Log.w(TAG, "Listener not registered");
                return;
            }

            mListeners.remove(listener);

            if (mListeners.isEmpty()) {
                // Last listener removed - unregister from service
                try {
                    mService.removeDesktopModeListener(mBinderListener);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to remove desktop mode listener", e);
                }
            }
        }
    }

    /**
     * Gets the desktop mode configuration.
     *
     * @return Map containing desktop mode configuration
     */
    @NonNull
    public Map<String, Object> getConfiguration() {
        try {
            android.os.Bundle config = mService.getConfiguration();
            Map<String, Object> result = new ArrayMap<>();
            if (config != null) {
                for (String key : config.keySet()) {
                    result.put(key, config.get(key));
                }
            }
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get configuration", e);
            return new ArrayMap<>();
        }
    }
}
