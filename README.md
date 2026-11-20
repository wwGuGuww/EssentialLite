<h1 align="center">ESSENTIAL ● LITE</h1>

<p align="center"><b>Capture anything in two keys.</b><br>
A Nothing OS-style Android app for screenshots, notes and voice memos, with a quick-access panel, a dot-matrix toolbox and a phone ↔ PC link.</p>

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-111?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="Android" src="https://img.shields.io/badge/Android%208.0%2B-111?style=flat-square&logo=android&logoColor=white">
  <img alt="Glyph" src="https://img.shields.io/badge/Nothing%20Glyph-supported-d71921?style=flat-square">
  <img alt="Offline" src="https://img.shields.io/badge/data-on%20device-111?style=flat-square">
</p>

<p align="center"><img src="assets/hero.png" alt="Home, note editor and setup screens"></p>

> Fan project, not affiliated with Nothing. The interface is in English; the original Turkish version lives on the [`turkish`](../../tree/turkish) branch.

## Capture

Press **volume up + volume down** together from any app and the quick-access panel opens on top of it: grab the screen, jot a note or start a voice memo without leaving what you're doing.

- **Screenshots** with OCR. Tap text on the capture to select and copy it; phone numbers, IBANs, dates, tracking codes and links become tappable (phone, IBAN and address detection follows Turkish formats).
- **Notes** with reminders, saved from the clipboard or shared from other apps. Write "tomorrow 3pm", "monday" or "in 2 hours" and it offers a reminder (Turkish phrases work too).
- **Voice memos** (M4A, on device) with a live dot-matrix level meter and a built-in player.
- **Markup editor** to crop and draw on screenshots, export to PDF.
- **Assistant**: long-press the power button to read the screen and pick text from it.


## Tools

Clock, alarm, stopwatch, timer, basic and scientific calculator (VAT, loan, unit conversion), metronome, spirit level, battery health and a password generator, all drawn in the same dot-matrix style. Alarms can light up the **Nothing Glyph** interface with breathe, strip and blink patterns.

## Phone ↔ PC

Pair with your computer by scanning a QR code. The link is encrypted end to end and nothing is stored on a server.

- Send notifications, clipboard and files to the PC
- Mirror the screen (view only) or control the phone from the PC with separate permission
- PC side is a small Node.js app in [`pc/`](pc): run `pc/baslat.bat`


## Privacy

Everything stays on the device. Backups are optional and encrypted with your password (AES-GCM) before they leave the phone. A vault with biometric app lock keeps private captures separate.

## Install

Grab the APK from the [Releases](../../releases) page and install it (Android 8.0+). It is signed with a debug key for now, so Android may ask you to allow installs from your browser or file manager.

## Build

Requirements: Android Studio (JDK 17), Android SDK 36.

```bash
git clone https://github.com/wwGuGuww/EssentialLite
```

Open the folder in Android Studio and run the `app` configuration. Minimum Android 8.0 (API 26). Glyph features need a Nothing phone; everything else works on any Android device.

## Credits

- Dot-matrix type: [Doto](https://fonts.google.com/specimen/Doto) (SIL Open Font License, see `licenses/`)
- Glyph lighting: Nothing Glyph Matrix SDK (`app/libs/`)
