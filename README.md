# Secure Notes (Android)

A single-screen, offline notes app for sensitive information such as bank, telco, address and ID details.

## Features

**Notes**
- Sections with a label (for example Banking, Telco, Identity), shown as collapsible cards. All sections start collapsed each time the app opens.
- Notes with a title and details. You can add, edit, delete and move notes up or down.
- Tapping a section header expands or collapses it. Section menu (⋮) has Add note, Rename, Move up/down and Delete.
- Tapping a note opens it for editing. Note menu (⋮) has Edit, Move up/down and Delete.
- Copy button on each note. The clipboard is marked sensitive and is cleared after 1 minute.
- Optional **Hide details until tapped** per note. The value shows masked (like `S8••••••C`) until you tap the eye icon.
- Search across section names, note titles and details.
- Every delete asks for confirmation first.

**Security**
- The app locks with your fingerprint or phone screen lock (PIN, pattern or password). It locks again whenever you leave the app or the screen turns off, and there is also a **Lock now** option in the menu.
- Notes are stored encrypted (AES-256-GCM). The key is kept in the Android Keystore and never leaves the phone.
- Screenshots, screen recording and the recent-apps preview are blocked.
- The app has no internet permission, and Android backup and phone-to-phone transfer are disabled.

**Export and import (moving to a new phone)**
- **Export as PDF** (share icon or ⋮ menu) asks you to set a password of at least 6 characters, then saves an AES-256 password-protected PDF. It's readable in any PDF viewer with that password.
- **Import from PDF** (⋮ menu) lets you pick the PDF and enter its password. Then choose:
  - **Add**: keeps your current notes and merges in the new ones. Sections with the same name are combined and duplicate notes are skipped.
  - **Replace**: deletes everything currently in the app first.
- The PDF also carries an exact copy of your data inside it, so import restores everything precisely, including the hidden flags and order.

## Build the APK

1. Install [Android Studio](https://developer.android.com/studio) (a recent version).
2. **File > Open** and select the `SecureNotes` folder. Wait for the Gradle sync to finish, which downloads dependencies the first time.
   - If Android Studio offers to upgrade the Android Gradle Plugin, it's fine to accept.
3. **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
4. Click **locate** in the notification. The file is `app/build/outputs/apk/debug/app-debug.apk`.

## Install on your phone

- **Option A:** Copy `app-debug.apk` to the phone (for example with a USB cable or Telegram "Saved Messages"), open it, and allow "Install unknown apps" for that app when asked.
- **Option B:** Enable Developer options and USB debugging on the phone, connect it, and press ▶ Run in Android Studio.

Your phone must have a screen lock set up (PIN, pattern, password or fingerprint). Without one, the app won't unlock.

## Important notes

- **Updates:** Build future versions on the **same computer**, so the APK signature matches and it installs over the old version, keeping your notes. If Android ever says the app must be uninstalled first, **export a PDF before uninstalling**, because uninstalling deletes all notes.
- **Forgotten PDF password:** it can't be recovered, so store it somewhere safe that isn't on the same phone.
- **Non-English text in the PDF:** the printed pages use a built-in font that only covers Latin characters, so Sinhala, Tamil or emoji show as `?` in the readable pages. Import is unaffected because it uses the embedded data copy.
- **Clipboard clearing:** after 1 minute the clipboard is cleared, even if you copied something else in another app during that minute.
- **Play Store later:** you'll need a release signing key (Build > Generate Signed Bundle) and a privacy policy. Minify is off for now, and `proguard-rules.pro` already has keep rules for the PDF library if you turn it on.

## Project layout

```
app/src/main/java/com/ishara/securenotes/
  MainActivity.kt          lock / unlock, auto re-lock, screenshot blocking, file pickers
  model/Models.kt          Section and Note data, JSON format
  data/SecureStore.kt      encrypted on-device storage (Android Keystore)
  pdf/PdfBackup.kt         password-protected PDF export and import
  ui/NotesScreen.kt        the single screen: search, sections, notes
  ui/Dialogs.kt            add/edit, confirm, password and import dialogs
  ui/LockScreen.kt         locked screen
  ui/NotesViewModel.kt     state, saving, clipboard, import/export flow
```

Tech: Kotlin, Jetpack Compose (Material 3), AndroidX Biometric, PdfBox-Android. Min Android 8.0 (API 26).
