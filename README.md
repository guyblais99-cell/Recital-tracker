# RecitalSync

Backstage recital check-in and live show tracker (PWA + Firebase Realtime Database).

## Run locally (testing only)

```powershell
npx serve
```

Open `http://localhost:3000`. **Install to phone home screen will not work** on `http://192.168.x.x` — Android requires **HTTPS**.

## Deploy to Vercel (HTTPS — required for phone install)

1. Push to GitHub — **must include** `icons/icon-192.png` and `icons/icon-512.png`
2. Import project in [vercel.com](https://vercel.com) → deploy from repo
3. Open your `https://….vercel.app` URL on the phone (not `http://192.168…`)

**Install on Android:** Chrome → green **Install** button in the header, or ⋮ → **Install app**.

**Install on iPhone:** Safari only → Share → **Add to Home Screen** (Chrome on iOS does not offer full install).

## Deploy to Firebase Hosting (alternative)

```bash
firebase deploy --only hosting
```

## Firebase setup

1. **Authentication** → Anonymous → **Enabled**
2. **Realtime Database** → **Rules** → publish `database.rules.json`
3. Share recital name (e.g. `2026`) — everyone joins the same name

## Troubleshooting

| Issue | Fix |
|-------|-----|
| "Realtime Database runtime failed" | Deploy to Hosting (HTTPS), hard-refresh; ensure Anonymous Auth is on |
| No Install on Android | Must use `https://recital-tracker.web.app`, not LAN IP |
| Permission denied | Publish database rules; enable Anonymous sign-in |
