# Firebase quick reference — recital-tracker

Everything in this repo uses one Firebase project: **recital-tracker**.

| App | Uses Firebase? | What for |
|-----|----------------|----------|
| **Scavenger Hunt PWA** (repo root) | Yes | Player hunts in browser (Android + iOS) |
| **Scavenger Hunt Android** | Yes | Host create/edit + native player app |

---

## One-time: deploy database rules

Deploy from PowerShell:

```powershell
cd "c:\Users\guybl\OneDrive\Documents\My Made Software\Recital app"
.\scripts\deploy-database-rules.ps1
```

First time: a browser opens → sign in with your Google account → then rules deploy automatically.

### Manual alternative (no CLI)

1. [Firebase Console](https://console.firebase.google.com/) → **recital-tracker**
2. **Realtime Database** → **Rules**
3. Paste contents of `database.rules.json` → **Publish**

---

## Auth

1. Console → **Authentication** → **Sign-in method**
2. **Email/Password** → **Enabled**

Create users under **Authentication → Users**, or sign up in the app.

### Vercel sign-in not working?

New deploy URLs must be added to **Authentication → Settings → Authorized domains**. Run:

```powershell
.\scripts\configure-auth-domains.ps1
```

Or open: [Authorized domains](https://console.firebase.google.com/project/recital-tracker/authentication/settings) and add `scavenger-hunt-live.vercel.app` (and your other Vercel URLs).

---

## Web PWA (Android + iPhone)

Open in Safari or Chrome over **HTTPS** (required for GPS + camera).

**Vercel:** push to GitHub — Vercel deploys the repo root automatically.

**Firebase Hosting:**

```powershell
firebase deploy --only hosting
```

Then open: `https://recital-tracker.web.app/`

On iPhone: Share → **Add to Home Screen** for full-screen app feel.

**Player flow:** Sign in → join code + team name → GPS radar → align ghost photo → reward clue (read aloud automatically).

**Host:** Create/edit hunts with the Android app (web host tools coming later).

---

## Android app

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
