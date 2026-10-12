# Voice

Independent Android test app for comparing Android's on-device and default speech recognizers, using British English (en-GB) and American English (en-US).

## Build

GitHub Actions builds an APK automatically on pushes to main. Open the Actions tab, select the latest **Build APK** run, and download the **voice-debug-apk** artifact. The APK is signed with the Android debug key.

## Test

1. Grant the microphone permission.
2. Choose English (UK), start the **On-device** recognizer and speak an address or business name.
3. Repeat in airplane mode, with Wi-Fi disabled.
4. Compare against **Default** recognition and English (US).

Try "221B Baker Street, London", "Trader Joe's", or unusual local place names.

The app does not request INTERNET permission, but the default recognizer is an external Android service and may use the network. The dedicated on-device recognizer explicitly asks for offline recognition. Language availability depends on the installed service and language packs.

Times measure the whole request, including your speaking duration, not just inference. History exists only in memory and is cleared when the process ends.
