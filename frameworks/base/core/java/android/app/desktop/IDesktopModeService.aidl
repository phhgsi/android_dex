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

import android.app.desktop.IDesktopModeListener;
import android.app.desktop.DesktopDisplayConfig;
import android.graphics.Rect;
import android.view.WindowInfo;

/**
 * AIDL interface for the Desktop Mode Service.
 *
 * This interface provides methods for system components and applications
 * to interact with the Desktop Mode feature. It allows enabling/disabling
 * desktop mode, managing windows, and observing state changes.
 *
 * @hide
 */
interface IDesktopModeService {

    // ============================================================
    // State Queries
    // ============================================================

    /**
     * Checks if desktop mode is currently enabled.
     *
     * @return true if desktop mode is enabled, false otherwise
     */
    boolean isDesktopModeEnabled();

    /**
     * Gets the display ID of the current desktop display.
     *
     * @return The desktop display ID, or -1 if desktop mode is not enabled
     */
    int getDesktopDisplayId();

    /**
     * Gets the current desktop mode state.
     *
     * @return The current state (PHONE_MODE, DESKTOP_ACTIVATING, DESKTOP_MODE,
     *         DESKTOP_DEACTIVATING, or SUSPENDED)
     */
    int getDesktopModeState();

    /**
     * Gets the current desktop display configuration.
     *
     * @return The DesktopDisplayConfig for the current desktop display,
     *         or null if desktop mode is not enabled
     */
    DesktopDisplayConfig getDesktopDisplayConfig();

    // ============================================================
    // Mode Control
    // ============================================================

    /**
     * Requests to enable desktop mode.
     *
     * This will activate desktop mode on the available external display.
     * The operation is asynchronous and listeners will be notified of state changes.
     *
     * @return true if the request was accepted, false otherwise
     */
    boolean enableDesktopMode();

    /**
     * Requests to disable desktop mode.
     *
     * This will deactivate desktop mode and return to phone UI.
     * The operation is asynchronous and listeners will be notified of state changes.
     *
     * @return true if the request was accepted, false otherwise
     */
    boolean disableDesktopMode();

    // ============================================================
    // Window Management
    // ============================================================

    /**
     * Sets the windowing mode for a specific task.
     *
     * @param taskId The ID of the task to modify
     * @param windowingMode The windowing mode to apply (e.g., FREEFORM, FULLSCREEN)
     * @return true if the operation was successful
     */
    boolean setTaskWindowingMode(int taskId, int windowingMode);

    /**
     * Minimizes a specific window/task.
     *
     * @param taskId The ID of the task to minimize
     * @return true if the operation was successful
     */
    boolean minimizeWindow(int taskId);

    /**
     * Maximizes a specific window/task.
     *
     * @param taskId The ID of the task to maximize
     * @return true if the operation was successful
     */
    boolean maximizeWindow(int taskId);

    /**
     * Restores a minimized window/task to its previous state.
     *
     * @param taskId The ID of the task to restore
     * @return true if the operation was successful
     */
    boolean restoreWindow(int taskId);

    /**
     * Closes a specific window/task.
     *
     * @param taskId The ID of the task to close
     * @return true if the operation was successful
     */
    boolean closeWindow(int taskId);

    /**
     * Resizes a window to the specified bounds.
     *
     * @param taskId The ID of the task to resize
     * @param bounds The new bounds for the window
     * @return true if the operation was successful
     */
    boolean resizeWindow(int taskId, in Rect bounds);

    /**
     * Moves a window to the specified position.
     *
     * @param taskId The ID of the task to move
     * @param x The new x coordinate
     * @param y The new y coordinate
     * @return true if the operation was successful
     */
    boolean moveWindow(int taskId, int x, int y);

    /**
     * Gets information about a specific window.
     *
     * @param taskId The ID of the task
     * @return WindowInfo for the task, or null if not found
     */
    WindowInfo getWindowInfo(int taskId);

    /**
     * Gets information about all desktop windows.
     *
     * @return List of WindowInfo for all desktop tasks
     */
    List<WindowInfo> getDesktopWindows();

    // ============================================================
    // Taskbar Control
    // ============================================================

    /**
     * Sets the visibility of the desktop taskbar.
     *
     * @param visible true to show the taskbar, false to hide
     */
    void setTaskbarVisibility(boolean visible);

    /**
     * Checks if the taskbar is visible.
     *
     * @return true if the taskbar is visible
     */
    boolean isTaskbarVisible();

    // ============================================================
    // Listener Management
    // ============================================================

    /**
     * Adds a listener for desktop mode state changes.
     *
     * @param listener The listener to add
     */
    void addDesktopModeListener(IDesktopModeListener listener);

    /**
     * Removes a desktop mode state listener.
     *
     * @param listener The listener to remove
     */
    void removeDesktopModeListener(IDesktopModeListener listener);

    // ============================================================
    // Display Management
    // ============================================================

    /**
     * Gets the list of available desktop displays.
     *
     * @return List of display IDs that can be used for desktop mode
     */
    List<Integer> getAvailableDesktopDisplays();

    /**
     * Sets the preferred display for desktop mode.
     *
     * @param displayId The display ID to use for desktop mode
     * @return true if the operation was successful
     */
    boolean setPreferredDesktopDisplay(int displayId);

    /**
     * Gets the preferred display ID for desktop mode.
     *
     * @return The preferred display ID, or -1 if not set
     */
    int getPreferredDesktopDisplay();

    // ============================================================
    // Configuration
    // ============================================================

    /**
     * Gets the desktop mode configuration.
     *
     * @return Bundle containing desktop mode configuration
     */
    Bundle getConfiguration();

    /**
     * Updates desktop mode configuration.
     *
     * @param config Bundle containing configuration updates
     * @return true if the configuration was updated successfully
     */
    boolean updateConfiguration(in Bundle config);
}
