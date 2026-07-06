# Scavenger Hunt (Android)

Photo + GPS scavenger hunt on Firebase project **`recital-tracker`** (same as Recital / ApexTrack).

## Features

- Email sign-in / sign-up
- **Create hunt** — direction clues, GPS pins, ghost photos, reward clues
- **Join hunt** — 6-character code
- **Hot / cold GPS** — warmer as you approach each stop
- **Ghost camera** — align photo to unlock reward clue
- **Progress** — checkpoint list + host dashboard

---

## Firebase setup (do this once)

You only need the **Realtime Database** and **Email/Password auth**. No Firestore, no Storage required for basic hunts.

### Step 1 — Open your project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select project **`recital-tracker`**

### Step 2 — Enable sign-in

1. Left menu → **Build** → **Authentication**
2. **Sign-in method** tab
3. Click **Email/Password**
4. Turn **Enable** on → **Save**

(If you already use ApexTrack / Recital PWA with email, this is already done.)

### Step 3 — Confirm Realtime Database exists

1. Left menu → **Build** → **Realtime Database**
2. You should see URL: `https://recital-tracker-default-rtdb.firebaseio.com`
3. If there is no database, click **Create Database** → choose a region → **Start in locked mode** (rules come from your repo)

### Step 4 — Deploy security rules (required for join codes)

From a terminal in the **Recital app** folder (where `database.rules.json` lives):

```powershell
cd "c:\Users\guybl\OneDrive\Documents\My Made Software\Recital app"
firebase login
firebase use recital-tracker
firebase deploy --only database
```

This publishes rules for:

- `recitals/{huntId}` — hunt data (checkpoints, teams, clues, GPS)
- `joinCodes/{code}` — maps 6-letter codes to hunt IDs

**You must deploy after pulling this repo** if `joinCodes` was added recently.

### Step 5 — Create a test account (optional)

1. **Authentication** → **Users** → **Add user**
2. Or sign up inside the app on first launch

### What you do NOT need

| Item | Needed? |
|------|---------|
| Firestore | No |
| Firebase Storage | No (photos stored compressed in RTDB) |
| `google-services.json` | No (app uses manual Firebase init) |
| New Firebase project | No — uses `recital-tracker` |
| Blaze / billing plan | No for small hunts |

### Troubleshooting

| Problem | Fix |
|---------|-----|
| `Permission denied` on create/join | Deploy database rules (Step 4); make sure you're signed in |
| `Invalid join code` | Host must publish hunt after rules deploy; code is uppercase |
| Auth fails | Enable Email/Password (Step 2) |
| Hot/cold doesn't move | Host must tap **Set GPS here** at each stop when creating |

---

## Build & install

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd scavenger-hunt-android
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## How to run a hunt

**Host**

1. Sign in → **Create hunt**
2. For each stop: direction clue, reward clue, **Set GPS here** (stand at the spot), ghost photo
3. **Publish** → share **join code**

**Player**

1. Sign in → **Join hunt** → code + team name
2. Read **direction clue** + follow **hot/cold GPS**
3. **I'm here — align photo** → match ghost → read **reward clue**

Package: `com.recital.scavengerhunt`
