# 🚀 Project Task: AOSP Desktop Mode (DeX-like Experience)

## 📋 Project Overview
**Objective:** Develop a custom Android system modification that replicates the Samsung DeX desktop experience on AOSP-based Custom ROMs.  
**Goal:** Enable a windowed desktop environment, taskbar, and mouse/keyboard support when an external display is connected via USB-C/HDMI.  
**Target Android Versions:** Android 13 (Tiramisu) / Android 14 (UpsideDownCake)  
**License:** Apache 2.0 (Must not include proprietary Samsung binaries)

---

## 🔍 Feature Research (Samsung DeX Capabilities)
Based on analysis of Samsung DeX functionality, the following features must be replicated using AOSP frameworks:

| Feature | Description | AOSP Equivalent/Implementation |
| :--- | :--- | :--- |
| **Desktop UI** | Taskbar, Start Menu, System Tray | Custom `SystemUI` Plugin or Desktop Launcher |
| **Windowing** | Freeform resizable windows | `ActivityManager` Freeform Stack + `WindowManager` |
| **Input** | Mouse hover, Right-click, Scroll | `InputManager` Pointer Capture + Custom Input Filter |
| **Display** | Mirror or Extend Mode | `DisplayManager` Multi-Display API |
| **Audio** | Route audio to external display | `AudioService` Device Routing |
| **Phone Screen** | Turn off or use as trackpad | `PowerManager` & Custom Input Mapper |

---

## 🛠 Technology Stack

### 1. Programming Languages
- **Java / Kotlin:** For Framework modifications (`frameworks/base`), SystemUI, and Desktop Launcher app.
- **C++:** For Native Services (SurfaceFlinger, InputFlinger) if low-level display handling is needed.
- **XML:** For layout resources (Taskbar, Window decorations).
- **Soong (Android.bp):** For build system configuration.

### 2. Build System
- **Android Build System (Soong):** All modules must be compilable within the AOSP tree using `Android.bp`.
- **Repo Manifest:** To manage source tree dependencies.

### 3. CI/CD
- **GitHub Actions:** For automated building, linting, and testing of the module.
- **Docker:** Using `android-build` containers for consistency.

### 4. Key Android Frameworks
- `android.view.WindowManager`
- `android.hardware.display.DisplayManager`
- `android.inputmethod.InputMethodManager`
- `android.app.ActivityTaskManager`

---

## ✅ Implementation Tasks
### Phase 1: Kernel & Hardware Abstraction
- [ ] **Enable DRM/Display Drivers:** Ensure kernel supports DP Alt Mode over USB-C.
- [ ] **Configure Device Tree:** Add overlays for external display detection.
- [ ] **Test HDMI Output:** Verify raw video signal output before UI modifications.

### Phase 2: Framework Modifications (`frameworks/base`)
- [ ] **Force Resizeable Activities:** Modify `ActivityRecord` to allow resizing for all apps.
- [ ] **Multi-Display Stack:** Configure `DisplayContent` to handle secondary display as a separate stack.
- [ ] **Input Policy:** Update `InputManagerService` to recognize mouse hover events on external display.
- [ ] **System Properties:** Add `persist.sys.desktop_mode.enabled=true`.

### Phase 3: SystemUI & Desktop Launcher
- [ ] **Create Desktop Launcher:** Build an APK that runs only on External Display ID.
- [ ] **Implement Taskbar:** Create a system overlay for bottom taskbar (Start button, Open Apps).
- [ ] **Window Decorations:** Add title bars with minimize/maximize/close buttons for freeform windows.
- [ ] **Notification Shade:** Adapt notification panel for mouse interaction.

### Phase 4: Build & Integration
- [ ] **Android.bp Configuration:** Define build rules for the new modules.
- [ ] **Overlay Integration:** Merge resources into `device/<vendor>/<codename>/overlay`.
- [ ] **Signature Permissions:** Handle `SIGNATURE` or `SIGNATURE_OR_SYSTEM` permissions for system apps.

### Phase 5: CI/CD & Testing
- [ ] **GitHub Actions Workflow:** Set up auto-build on push.
- [ ] **Emulator Testing:** Test multi-display logic using Android Emulator with secondary screen.
- [ ] **Device Testing:** Flash on target hardware (e.g., Pixel, OnePlus) with USB-C to HDMI dock.

---

## 📦 Build Configuration (Android.bp)

Below is the template for building the Desktop Mode System Service and Overlay.

```python
# device/custom/desktop_mode/Android.bp

android_library {
    name: "desktop-mode-framework",
    srcs: ["src/**/*.java", "src/**/*.kt"],
    static_libs: [
        "androidx.core_core-ktx",
        "framework-common",
    ],
    sdk_version: "current",
    installable: false,
}

android_app_import {
    name: "DesktopLauncher",
    apk: "prebuilt/DesktopLauncher.apk",    cert: "platform",
    privileged: true,
    system_ext_specific: true,
    required: ["desktop-mode-framework"],
}

android_soong_config {
    name: "desktop_mode_config",
    variables: ["DESKTOP_MODE_ENABLED"],
}
```

---

## 🤖 GitHub Actions Workflow

This workflow ensures the code compiles within an Android build environment.

```yaml
# .github/workflows/build_desktop_mode.yml

name: Build Desktop Mode Module

on:
  push:
    branches: [ "main", "android-14" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest
    container:
      image: androidbuild/container:latest
    
    steps:
    - name: Checkout Code
      uses: actions/checkout@v3

    - name: Setup Build Environment
      run: |
        sudo apt-get update
        sudo apt-get install -y openjdk-17-jdk repo git

    - name: Initialize Repo (If full ROM)
      run: |
        repo init -u https://android.googlesource.com/platform/manifest -b android-14.0.0_r1
        # Sync only necessary components for module testing
        repo sync -c --no-clone-bundle
    - name: Apply Desktop Mode Patch
      run: |
        cp -r $GITHUB_WORKSPACE/* device/custom/desktop_mode/

    - name: Build Module
      run: |
        source build/envsetup.sh
        lunch aosp_arm64-userdebug
        m DesktopLauncher desktop-mode-framework

    - name: Upload Artifacts
      uses: actions/upload-artifact@v3
      with:
        name: desktop-mode-build
        path: out/target/product/generic/system/system_ext/priv-app/DesktopLauncher/
```

---

## ⚠️ Compatibility & Constraints

1.  **Proprietary Restrictions:** Do not decompile or include `com.samsung.desktopmode` packages. Use only AOSP APIs.
2.  **SELinux Policies:** New services require `sepolicy` updates to prevent bootloops.
3.  **Performance:** Desktop mode requires significant GPU memory. Ensure `ro.hardware.composer` is optimized.
4.  **App Compatibility:** Some apps force fullscreen. Use `ActivityOptions` to force `WINDOWING_MODE_FREEFORM`.

---

## 📚 References
- [AOSP Multi-Display Documentation](https://source.android.com/devices/architecture/modular-system/multi-display)
- [WindowManagerService Source](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/wm/)
- [Samsung DeX Technical Analysis (Public Blogs)](https://developer.samsung.com/)

---

**Project Maintainer:** [phhgsi]  
**Status:** 🟡 In Planning  
**Last Updated:** 2023-10-27
