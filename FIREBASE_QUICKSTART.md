# Firebase quick reference — recital-tracker

Everything in this repo uses one Firebase project: **recital-tracker**.

| App | Uses Firebase? | What for |
|-----|----------------|----------|
| **ApexTrack PWA** (`index.html`) | Yes | Internet race sync, profiles |
| **ApexTrack Android** | No | LAN only (Wi‑Fi between phones) |
| **Scavenger Hunt PWA** (`scavenger-hunt/`) | Yes | Play hunts in browser (Android + iOS) |
| **Scavenger Hunt Android** | Yes | Host create/edit + native player app |

---

## One-time: deploy database rules

Scavenger Hunt needs `joinCodes/` in the rules. Deploy from PowerShell:

```powershell
cd "c:\Users\guybl\OneDrive\Documents\My Made Software\Recital app"
.\scripts\deploy-database-rules.ps1
```

First time: a browser opens → sign in with your Google account → then rules deploy automatically.

**No Node installed?** The script installs Firebase CLI via npm. Node LTS was added via winget if missing.

### Manual alternative (no CLI)

1. [Firebase Console](https://console.firebase.google.com/) → **recital-tracker**
2. **Realtime Database** → **Rules**
3. Paste contents of `database.rules.json` → **Publish**

---

## Auth (both PWA + Scavenger)

1. Console → **Authentication** → **Sign-in method**
2. **Email/Password** → **Enabled**

Create users under **Authentication → Users**, or sign up in the app.

---

## ApexTrack lap timer (reminder)

**Two phones, same Wi‑Fi — no Firebase required on Android:**

1. Monitor phone: **Monitor (host)** → add racers → **Start as Monitor**
2. Gate phone: **Camera gate** → same session ID → **Start as Camera Gate**

Firebase is only needed for the **web PWA** when racing over the internet.

**Build Android app:**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd apextrack-android
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## Scavenger Hunt

### Web PWA (Android + iPhone)

Open in Safari or Chrome (HTTPS required for GPS + camera):

**Local:** run any static server in the repo root, then visit `/scavenger-hunt/`

**Firebase Hosting:**

```powershell
cd "c:\Users\guybl\OneDrive\Documents\My Made Software\Recital app"
firebase deploy --only hosting
```

Then open: `https://recital-tracker.web.app/scavenger-hunt/`

On iPhone: Share → **Add to Home Screen** for full-screen app feel.

**Player flow:** Sign in → join code + team name → GPS radar → align ghost photo → reward clue (read aloud automatically).

**Host:** Create/edit hunts with the Android app (web host tools coming later).

### Android app

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd scavenger-hunt-android
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Host:** Create hunt → direction clue + GPS + ghost photo + reward clue per stop → Publish → share join code.

**Player:** Join → follow hot/cold GPS → align photo → get reward clue.

---

## Database URLs

- **Console:** https://console.firebase.google.com/project/recital-tracker  
- **RTDB:** https://recital-tracker-default-rtdb.firebaseio.com  

## Deploy hosting (PWA only — optional)

```powershell
firebase deploy --only hosting
```
