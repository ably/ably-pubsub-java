# Example App using Live Objects

This demo app showcases Ably Live Objects functionality with two interactive features:

- **Color Voting**: Real-time voting system where users can vote for their favorite color (Red, Green, Blue) and see live vote counts synchronized across all devices
- **Task Management**: Collaborative task management where users can add, edit, and delete tasks that sync in real-time across all connected devices

Follow the steps below to get started with the Live Objects demo app

## Prerequisites

Ensure you have the following installed:
- [Android Studio](https://developer.android.com/studio) (latest stable version)
- Java 17 or higher
- Android SDK with API Level 34 or higher

Add your Ably key to the `local.properties` file:

```properties
sdk.dir=/path/to/android/sdk

EXAMPLES_ABLY_KEY=xxxx:yyyyyy
```

## Steps to Run the App

1. Open in Android Studio

  - Open Android Studio.
  - Select File > Open and navigate to the cloned repository.
  - Open the project.

2. Sync Gradle

  - Wait for Gradle to sync automatically.
  - If it doesn’t, click on Sync Project with Gradle Files in the toolbar.

3. Configure an Emulator or Device

  - Set up an emulator or connect a physical Android device.
  - Ensure the device is configured with at least Android 10 (API 29), the example app's `minSdk`. The `io.ably.pubsub:device` artifact it depends on requires API 24 or later.

4. Run the App

  - Select your emulator or connected device in the device selector dropdown.
  - Click on the Run button ▶️ in the toolbar or press Shift + F10.

5. View the App

   Once the build is complete, the app will be installed and launched on the selected device or emulator.

## What You'll See

The app opens with two tabs:

1. **Color Voting Tab**: 
   - Vote for Red, Green, or Blue colors
   - See real-time vote counts that update instantly across all devices
   - Reset all votes with the "Reset all" button

2. **Task Management Tab**:
   - Add new tasks using the text input and "Add Task" button
   - Edit existing tasks by clicking the edit icon
   - Delete individual tasks or remove all tasks at once
   - See the total task count and real-time updates as tasks are modified

To see the real-time synchronization in action, run the app on multiple devices or emulators with the same Ably key.

## Building release APK

This is useful to check ProGuard rules, app size, etc. 

1. Create signing keys for the Android app

```shell
keytool -genkey -v -keystore release.keystore \
-storepass <store-password> \
-alias <key-alias> \
-keypass <key-password> \
-keyalg RSA -keysize 2048 -validity 25000 -dname "CN=Ably Example App,OU=Examples,O=Ably,L=London,ST=England,C=GB"
```

2. Update `local.properties` file:

```properties
EXAMPLES_STORE_FILE=/absolute/path/to/release.keystore
EXAMPLES_STORE_PASSWORD=<store-password>
EXAMPLES_KEY_ALIAS=<key-alias>
EXAMPLES_KEY_PASSWORD=<key-password>
```

3. Build release APK

```shell
./gradlew :examples:assembleRelease 
```

4. Install to the device

```shell
adb install -r examples/build/outputs/apk/release/examples-release.apk 
```

## Troubleshooting

- SDK Not Found: Install missing SDK versions from File > Settings > Appearance & Behavior > System Settings > Android SDK.
- Build Failures: Check the error logs and resolve dependencies or configuration issues.
