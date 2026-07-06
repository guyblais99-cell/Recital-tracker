# Scavenger Hunt

GPS scavenger hunt for Android and iPhone — sign in, join a hunt, follow the radar to each stop, align your camera with the ghost photo, and hear clues read aloud.

Uses Firebase project **recital-tracker** (auth + Realtime Database).

## Live app (Vercel)

After you push to GitHub, Vercel deploys the repo root as the app. Open your Vercel URL on a phone over **HTTPS**, then **Install app** (Android) or **Add to Home Screen** (iPhone, Safari only).

## Player flow

1. Sign in with email/password
2. Enter join code + team name
3. Follow GPS radar to the checkpoint
4. Tap **Read clue aloud** on the direction hint
5. Align the ghost photo with your camera
6. Reward clue plays automatically when you match

## Host (Android)

Create and edit hunts with the native app in `scavenger-hunt-android/` — add direction clues, GPS pins, ghost photos, and reward clues per stop, then share the join code.

See [FIREBASE_QUICKSTART.md](FIREBASE_QUICKSTART.md) for auth setup, database rules, and build instructions.

## Firebase Hosting (optional)

```powershell
firebase deploy --only hosting,database
```

Then open: `https://recital-tracker.web.app/`
