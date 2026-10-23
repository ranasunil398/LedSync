LED Sync - ambient light sync for Android TV -> Arduino/ESP LED strip (Adalight over USB serial)

1. Flash arduino/ledsync/ledsync.ino (set NUM_LEDS, DATA_PIN; install FastLED).
2. Open this folder in Android Studio, let Gradle sync, set LEFT/TOP/RIGHT/BOTTOM in Config.kt, Build > Build APK.
3. Install on the TV (adb install app-debug.apk). Connect the board to the TV's USB port (OTG adapter if needed).
4. Open LED Sync, press Start, allow USB and screen capture.
Notes: DRM apps (Netflix etc.) may capture as black. If colors run the wrong way around, reverse the order in CaptureService.process.
