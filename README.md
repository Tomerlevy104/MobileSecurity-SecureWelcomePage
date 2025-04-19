# 🔒 Mobile Security Challenge

## 📱 Overview
The Mobile Security Challenge is an Android app that shows how phone sensors can be used for security. Instead of just using passwords, this app makes you complete challenges using different phone features to unlock it.

## 🛠️ Technology Stack
- **Language**: Kotlin
- **Platform**: Android

## ⚙️ How It Works
To use the app, you need to complete 5 security challenges:

1. **Proximity Challenge**: Cover the proximity sensor for 10 seconds continuously.
2. **Time Challenge**: Open the app when the current minute is divisible by 2 (e.g., 2:00, 2:02, 2:04).
3. **Bluetooth Challenge**: Connect to a specific Bluetooth device (MAC address: XX:XX:XX:XX:XX:XX).
4. **Brightness Challenge**: Set the device's screen brightness to maximum.
5. **Barcode Challenge**: Scan a specific barcode (7290002331124).

## 🛡️ Security Concepts
This app shows how phones can use different security methods:

- 🔐 **Multiple security factors**: Using both things you have (Bluetooth device, barcode) and things you do (sensor actions)
- ⏱️ **Time-based security**: Only allowing access at certain times
- 📱 **Sensor security**: Using phone sensors to verify it's really you
- 📸 **Camera security**: Using the camera to scan special codes

## 🏗️ App Structure
Each challenge has its own manager:

- 👋 One for the proximity sensor
- ⏰ One for checking the time
- 📶 One for Bluetooth connections
- ☀️ One for screen brightness
- 📷 One for barcode scanning

## 📋 How To Use
1. Open the app
2. Complete all 5 challenges:
   - Cover the proximity sensor for 10 seconds
   - Open the app when the minute is even (like 2:02, 2:04)
   - Connect to the specific Bluetooth device
   - Turn screen brightness to maximum
   - Scan the specific barcode (7290002331124)
3. When all challenges are done, the "UNLOCK APP" button will light up
4. Press the button to unlock the app

## 💡 Why This Matters
This app shows how phones can use more than just passwords for security. By using sensors and hardware that phones already have, we can create new ways to keep information safe.

## 🔑 Permissions Needed
- 📷 Camera (for scanning barcodes)
- 📶 Bluetooth (for connecting to devices)
- ⚙️ System settings (for checking screen brightness)

---

*⚠️ Note: This app is for learning about mobile security and shouldn't be used to protect real sensitive information.*