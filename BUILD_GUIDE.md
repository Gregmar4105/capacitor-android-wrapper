# Android Wrapper Build Guide

This guide explains how to synchronize settings, add new Capacitor plugins, and compile the Android app (both Debug and Production Release builds) on your own.

---

## 🛠️ Step 1: Prerequisites

### 1. JDK 21 (Java Development Kit)
Because this app targets **Android SDK 36**, Android requires **Java 21** to compile. 
* By default, your computer's system JDK might be set to a lower version (like Java 17).
* You can use the high-performance JDK 21 that comes built-in with **Android Studio**, located at:
  `C:\Program Files\Android\Android Studio\jbr`

### 2. Node & npm dependencies
Make sure all dependencies are installed in your workspace:
```bash
npm install
```

---

## 🔄 Step 2: Syncing Configuration & Plugins

Whenever you change **`capacitor.config.json`** or run **`npm install`** for new Capacitor plugins, you must sync those changes to the native Android project.

1. **If you only edited `capacitor.config.json`:**
   ```bash
   npx cap copy
   ```
2. **If you installed new plugins (e.g., `npm install @capacitor/camera`):**
   ```bash
   npx cap sync
   ```

---

## 🏗️ Step 3: Compiling the Application

To build the application, open your terminal (PowerShell is recommended) and follow these commands:

### 1. Set the Java Environment Variable
Tell your terminal session to use Android Studio's Java 21 engine:
* **PowerShell (Recommended):**
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  ```
* **Command Prompt (CMD):**
  ```cmd
  set JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
  ```

### 2. Navigate to the Android Folder
```powershell
cd android
```

### 3. Choose your Build Command:

* **To build a Debug APK (for quick local testing on a device):**
  ```powershell
  .\gradlew assembleDebug
  ```
  *Output location:* `android/app/build/outputs/apk/debug/app-debug.apk`

* **To build a Signed Production APK (to install the final app directly on any phone):**
  ```powershell
  .\gradlew assembleRelease
  ```
  *Output location:* `android/app/build/outputs/apk/release/app-release.apk`

* **To build a Signed Google Play Store Bundle (.aab - for uploading to Google Play):**
  ```powershell
  .\gradlew bundleRelease
  ```
  *Output location:* `android/app/build/outputs/bundle/release/app-release.aab`

---

## ⚡ Quick One-Line Commands (Copy & Paste)

If you are in the **root folder** of your project, you can copy, paste, and run these single-line commands to sync and compile instantly:

#### Build Debug APK:
```powershell
npx cap copy; cd android; $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew assembleDebug; cd ..
```

#### Build Production Release APK & Google Play Bundle:
```powershell
npx cap copy; cd android; $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew assembleRelease bundleRelease; cd ..
```
