# Privacy Policy for Android FTP Server

**Effective Date:** September 14, 2026
**Developer / Publisher:** Victor Gomez (victorgomez.studio)
**Application ID:** studio.victorgomez.androidftpserver

### 1. Overview
Android FTP Server is developed by Victor Gomez as a local network utility designed to allow users to transfer and manage files between their Android device and computers or other devices connected to the same local Wi-Fi network.

Your privacy is paramount. Android FTP Server does not collect, track, transmit, or share any personal information or file data with the developer or any third parties.

### 2. Information We Do NOT Collect
- No Personal Identifiable Information (PII): We do not collect names, email addresses, phone numbers, or account details.
- No File Tracking or Uploading: The files on your device are accessible strictly via your local network through the FTP/HTTP server when you explicitly turn it on. No files are ever transmitted to any cloud servers or external analytics services.
- No Telemetry or Tracking: We do not use third-party analytics SDKs, behavioral trackers, or ad networks.

### 3. Permissions Used by the Application
The App requests specific Android permissions strictly to provide local file-sharing functionality:
1. Storage Access (MANAGE_EXTERNAL_STORAGE): Allows the FTP server and Web file manager to read, write, upload, and download files from your device storage as instructed by authorized connections made on your local network.
2. Wi-Fi and Network State (INTERNET, ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE): Used to bind the local server sockets to your device's Wi-Fi IP address and respond to UPnP/SSDP network discovery requests from local computers.
3. Foreground Service (FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE, FOREGROUND_SERVICE_DATA_SYNC): Keeps the file transfer service running continuously in the background so that file transfers are not interrupted when you switch apps or turn off your screen.
4. Notifications (POST_NOTIFICATIONS): Displays an active persistent notification showing current server status, IP address, port, and connected client count with quick stop controls.

### 4. Third-Party Services
The App contains no third-party advertising SDKs, data brokers, or analytics frameworks. All communication remains strictly point-to-point on your local Area Network (LAN).

### 5. Contact Us
If you have any questions about this Privacy Policy or the app, please contact:
- Website: https://victorgomez.studio
- Developer: Victor Gomez
