# SMS-to-Telegram Relay

A lightweight, privacy-focused Android application designed for Android 14+ that automatically forwards incoming SMS messages to a private Telegram Bot. It is optimized for secondary phones and features a robust "Advanced Hybrid" security architecture.

## 🚀 Key Features

*   **Instant Telegram Relay**: Primary delivery via Telegram Bot API for sub-second notifications.
*   **Legacy SMS Fallback**: Automatically relays via standard SMS if the phone is offline or the Telegram API fails.
*   **Foreground Reliability**: Uses an Android Foreground Service to prevent the system from freezing the process during carrier interrogation windows (critical for Jio/VoLTE).
*   **Custom Filtering**: Manage regex-based sender filters via the dashboard. (Empty list = Relay Everything).
*   **Identity Prefixing**: Customizable user name to identify which device sent the relay (e.g., `[Work Phone - HDFCBank]`).
*   **Zero Battery Drain**: Uses a `BroadcastReceiver` architecture that only wakes the app when a message arrives.

## 🛡️ Security Architecture

This app uses an **Advanced Hybrid** approach to protect your sensitive credentials:

1.  **Build-Time Obfuscation**: Tokens and phone numbers are XOR-scrambled in the APK binary to defeat basic decompiler string searches.
2.  **Hardware-Backed Vault**: On first launch, secrets are "seeded" into a vault encrypted by the **Android Keystore System**. The keys are stored in the device's secure hardware (TEE/SE) and never leave the chip.
3.  **Encrypted Storage**: Uses `DataStore` combined with hardware-backed AES-GCM encryption.
4.  **Privacy**: No third-party servers. The app communicates directly with Telegram's official API.

## 🛠️ Setup Instructions

### 1. Telegram Configuration
1.  Message [@BotFather](https://t.me/botfather) on Telegram to create a new bot and get your **API Token**.
2.  Message [@userinfobot](https://t.me/userinfobot) to find your **Chat ID**.

### 2. Local Configuration
Create a `local.properties` file in the project root (if it doesn't exist) and add your secrets:

```properties
telegram.bot.token=YOUR_BOT_TOKEN
telegram.chat.id=YOUR_CHAT_ID
forwarding.number=+1234567890
```
*Note: This file is ignored by Git to prevent secret leakage.*

### 3. Deployment
1.  Build and install the APK on your secondary phone.
2.  **Open the app once** to trigger the initial "seeding" of the encrypted vault.
3.  Grant the requested **SMS** and **Notification** permissions.

## ⚠️ Important for Android 13/14+

Since this is a side-loaded app using sensitive permissions, you must perform these two manual steps for 100% reliability:

1.  **Allow Restricted Settings**: 
    - Go to **App Info** for SMS-Relay.
    - Tap the **three-dot menu** in the top right.
    - Select **"Allow restricted settings"**.
2.  **Unrestricted Battery**:
    - Tap the **FIX** button on the "Improve Reliability" card in the app dashboard.
    - Select **"Unrestricted"** for this app to prevent delays during deep sleep (Doze mode).

## 🛠 Tech Stack

*   **Language**: Kotlin
*   **UI**: Jetpack Compose (Material 3)
*   **Storage**: Jetpack DataStore + Android Keystore
*   **Networking**: HttpURLConnection (Lightweight, no extra dependencies)
*   **Background**: Foreground Service + BroadcastReceiver
