# ApexTrack Live

RC lap timer with AprilTag gate detection, live leaderboard, and cloud sync (PWA + Firebase).

**Native Android app (LAN, no browser):** see [`apextrack-android/README.md`](apextrack-android/README.md) — two phones on the same Wi‑Fi, native CameraX + AprilTag detection.

## How it works

1. **Monitor** — one phone/tablet runs the race clock and leaderboard.
2. **Camera gate** — a second phone at the finish line scans colored tape on each car.
3. Both devices join the same **room ID** over Firebase (works across WiFi and cellular).

## Run locally (testing only)

```powershell
npx serve
```

Open `http://localhost:3000`. **Install to phone home screen will not work** on `http://192.168.x.x` — Android requires **HTTPS**.

## Deploy to Vercel (HTTPS — required for phone install)

1. Push to GitHub — include `icon-192.png` and `icon-512.png` if you want home-screen icons
2. Import project in [vercel.com](https://vercel.com) → deploy from repo
3. Open your `https://….vercel.app` URL on the phone (not `http://192.168…`)

**Install on Android:** Chrome → **Install app**, or ⋮ → **Install app**.

**Install on iPhone:** Safari only → Share → **Add to Home Screen**.

## Deploy to Firebase Hosting (alternative)

```bash
firebase deploy --only hosting,database
```

## Firebase setup

1. **Authentication** → **Sign-in method** → enable **Email/Password**
2. **Realtime Database** → **Rules** → publish `database.rules.json` (includes `users/` and `apextrack/` paths)
3. **Account tab** — create an account or sign in; your cars and settings sync under your user ID
4. Share a room ID for each race — monitor and camera gate use the same room name

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Could not connect to cloud | Deploy over HTTPS; enable Email/Password auth; sign in on Account tab |
| Sign-in failed | Enable Email/Password in Firebase Console → Authentication |
| Camera gate won't link | Start **Monitor** first, then **Camera gate** with same room ID |
| Permission denied | Publish database rules; enable Anonymous sign-in |
| No laps counting | Sync each car's tape color on the Racers tab before the race |
