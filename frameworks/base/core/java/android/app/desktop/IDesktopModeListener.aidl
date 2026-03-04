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

import android.app.desktop.DesktopDisplayConfig;
import android.view.WindowInfo;

/**
 * AIDL interface for listening to Desktop Mode state changes.
 *
 * This interface is used by system components and applications to receive
 * callbacks when desktop mode state changes or when windows are added/removed.
 *
 * @hide
 */
oneway interface IDesktopModeListener {

    /**
     * Called when desktop mode is enabled.
     *
     * @param displayId The display ID where desktop mode is active
     */
    void onDesktopModeEnabled(int displayId);

    /**
     * Called when desktop mode is disabled.
     */
    void onDesktopModeDisabled();

    /**
     * Called when desktop mode state changes.
     *
     * @param state The new desktop mode state
     */
    void onDesktopModeStateChanged(int state);

    /**
     * Called when a new window is added to the desktop.
     *
     * @param windowInfo Information about the new window
     */
    void onWindowAdded(in WindowInfo windowInfo);

    /**
     * Called when a window is removed from the desktop.
     *
     * @param taskId The ID of the task that was removed
     */
    void onWindowRemoved(int taskId);

    /**
     * Called when a window gains or loses focus.
     *
     * @param taskId The ID of the task
     * @param hasFocus true if the window gained focus, false if it lost focus
     */
    void onWindowFocusChanged(int taskId, boolean hasFocus);

    /**
     * Called when a window's visibility changes.
     *
     * @param taskId The ID of the task
     * @param visible true if the window is now visible
     */
    void onWindowVisibilityChanged(int taskId, boolean visible);

    /**
     * Called when a window's bounds change (resize/move).
     *
     * @param taskId The ID of the task
     * @param bounds The new bounds of the window
     */
    void onWindowBoundsChanged(int taskId, in android.graphics.Rect bounds);

    /**
     * Called when a desktop display is connected.
     *
     * @param displayId The ID of the newly connected display
     * @param config The configuration of the connected display
     */
    void onDesktopDisplayConnected(int displayId, in DesktopDisplayConfig config);

    /**
     * Called when a desktop display is disconnected.
     *
     * @param displayId The ID of the disconnected display
     */
    void onDesktopDisplayDisconnected(int displayId);

    /**
     * Called when the desktop display configuration changes.
     *
     * @param displayId The ID of the display
     * @param config The new configuration
     */
    void onDesktopDisplayConfigChanged(int displayId, in DesktopDisplayConfig config);

    /**
     * Called when the taskbar visibility changes.
     *
     * @param visible true if the taskbar is now visible
     */
    void onTaskbarVisibilityChanged(boolean visible);
}
