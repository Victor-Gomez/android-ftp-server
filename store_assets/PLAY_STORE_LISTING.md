# Google Play Store Listing & Declaration Package

This package contains all copy, declarations, and metadata ready for submission to the Google Play Console.

---

## 1. Store Listing Metadata

### App Title (max 30 characters)
`Android FTP Server`

### Short Description (max 80 characters)
`Fast & secure Wi-Fi FTP server and Web browser file manager for your device.`

### Full Description (max 4000 characters)
```text
Turn your Android phone or tablet into a high-speed, secure FTP server and Web File Manager! Transfer photos, videos, music, documents, and backups between your PC, Mac, Linux, or other mobile devices over your local Wi-Fi network without cables or cloud uploads.

KEY FEATURES:
• One-Tap Server: Start and stop the FTP server with a single tap.
• Web Browser File Manager: Don't have an FTP client? Open any web browser on your PC or Mac and view, upload, download, and manage your device storage directly.
• QR Code Quick Connect: Display a QR code on your phone to quickly connect other mobile devices or browsers.
• High-Speed Local Wi-Fi Transfers: Maximum transfer speeds utilizing your local network bandwidth without internet consumption.
• Multi-User Accounts & Access Control: Configure individual user accounts with customizable passwords and permission levels (Read-Only or Read & Write).
• Anonymous Access Mode: Quick guest connections with optional read-only or read-and-write permissions.
• Customizable Ports: Configure your custom FTP server port (default 2121), passive port range, and Web Manager port (default 8080).
• Full Internal Storage Access: Manage your entire storage including Download, DCIM, Documents, and custom folders.
• Battery-Optimized Foreground Service: Transfers stay active seamlessly in the background even when your screen turns off or while using other apps.
• Modern Material 3 UI: Beautiful, responsive design with full dark and light theme support.
• 100% Private & Offline: Operates entirely on your local Wi-Fi network. Zero tracking, zero telemetry, and zero cloud uploads.

HOW TO CONNECT:
1. Connect your phone and your computer to the same Wi-Fi network.
2. Open Android FTP Server and tap the start button.
3. On your computer:
   - Using FileZilla / WinSCP / Cyberduck: Enter the FTP address shown on your phone (e.g. ftp://192.168.1.xxx:2121) and your user credentials.
   - Using Chrome / Firefox / Edge / Safari: Simply enter the Web Manager address (e.g. http://192.168.1.xxx:8080) to browse and manage files directly.

SUPPORT & PRIVACY:
Developer: Victor Gomez (victorgomez.studio)
Privacy Policy: https://victorgomez.studio/privacy
```

### Category & Tags
- **Category:** Tools / Productivity
- **Tags:** FTP, File Transfer, Wi-Fi File Transfer, File Manager, Network Storage, Server

---

## 2. Policy Declarations for Google Play Console

### A. All Files Access (`MANAGE_EXTERNAL_STORAGE`)
- **Core Feature Selection:** Select **"Device-to-device file transfer / File management"**.
- **Declaration Text:**
> "Android FTP Server is an FTP and HTTP server utility that enables users to wirelessly transfer and manage files between their Android device and computers over their local Wi-Fi network. To allow users to access, upload, download, and organize their files across all directories (such as Documents, Downloads, DCIM, and project folders) from external FTP clients (e.g., FileZilla) and web browsers, full storage management access is strictly required. Standard MediaStore and Storage Access Framework (SAF) APIs cannot provide the direct POSIX file path access required by the underlying Apache FTP server protocol."

- **Video Demonstration Link:**
Record a quick 30-45 second screen capture showing:
1. Opening the app and granting storage access.
2. Tapping the big Start button.
3. Connecting from your computer (FileZilla or web browser) and transferring a file.
Upload to YouTube as **Unlisted** and paste the link in the declaration field.

### B. Foreground Service Declaration (`FOREGROUND_SERVICE`)
- **Service Types:** Select `connectedDevice` and `dataSync`.
- **Declaration Text:**
> "The foreground service keeps the FTP socket and file transfer service active in the background. This ensures active file transfers between the Android device and connected network clients are not terminated by Android battery optimizations when the user switches apps or turns off the screen. A persistent notification is displayed with connection details and a quick-stop action."

### C. Data Safety Form
- Does your app collect or share any user data? **No**
- Does your app transmit data to external third parties? **No** (Local LAN only)
- Is data encrypted in transit? **No** (Standard local FTP/HTTP protocol)

---

## 3. Upload Checklist
- [x] Upload Keystore: `release.keystore` (Alias: `androidftpserver`)
- [x] Release App Bundle: `app-release.aab` (15.2 MB)
- [x] App Icon: `store_assets/play_store_icon_512.png` (512x512 PNG)
- [x] Feature Graphic: `store_assets/play_store_feature_graphic_1024x500.png` (1024x500 PNG)
- [x] Screenshots: `store_assets/screenshot_1_home.png` and `screenshot_2_settings.png` (1080x2400)
- [x] Privacy Policy: `store_assets/privacy.html` (or `PRIVACY_POLICY.md`)
