# RecitalSync

Backstage recital check-in and live show tracker (PWA + Firebase Realtime Database).

## Run locally (testing only)

```powershell
npx serve
```

Open `http://localhost:3000`. **Install to phone home screen will not work** on `http://192.168.x.x` — Android requires **HTTPS**.

## Deploy (HTTPS — required for phone install + stable Firebase)

1. Install CLI: `npm install -g firebase-tools`
2. Login: `firebase login`
3. From this folder: `firebase use recital-tracker` (or your project id)
4. Deploy: `firebase deploy`

Your app URL: `https://recital-tracker.web.app` (or the URL shown in the console).

Open that URL on Android → Chrome menu → **Install app** or **Add to Home screen**.

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
