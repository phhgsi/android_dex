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

import android.graphics.Rect;
import android.os.Parcelable;

/**
 * AIDL data class containing information about a desktop window.
 *
 * This class provides detailed information about a window running in
 * desktop mode, including its position, size, windowing mode, and
 * capabilities.
 *
 * @hide
 */
parcelable WindowInfo {
    /** The unique identifier for this window/task */
    int taskId;

    /** The activity component name associated with this window */
    String packageName;

    /** The activity name */
    String activityName;

    /** The title of the window */
    String title;

    /** The bounds of the window in desktop coordinates */
    Rect bounds;

    /** The windowing mode (e.g., FREEFORM, FULLSCREEN) */
    int windowingMode;

    /** Whether the window is visible */
    boolean visible;

    /** Whether the window has focus */
    boolean hasFocus;

    /** Whether the window is minimized */
    boolean isMinimized;

    /** Whether the window is maximized */
    boolean isMaximized;

    /** Whether the window can be minimized */
    boolean canMinimize;

    /** Whether the window can be maximized */
    boolean canMaximize;

    /** Whether the window can be resized */
    boolean canResize;

    /** Whether the window can be closed */
    boolean canClose;

    /** Whether the window supports drag and drop */
    boolean supportsDragAndDrop;

    /** The display ID where this window is shown */
    int displayId;
}
