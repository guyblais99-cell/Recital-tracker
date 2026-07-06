# Deploy Firebase rules — in your browser (no terminal)

You do **not** paste your Documents folder path anywhere.  
Rules are copied from a **file** into the **Firebase website**.

---

## Step 1 — Open the rules file in Notepad

1. Open **File Explorer**
2. Go to this folder (copy this whole line into the address bar at the top, then press Enter):

```
c:\Users\guybl\OneDrive\Documents\My Made Software\Recital app
```

3. Find the file **`database.rules.json`**
4. **Right-click** it → **Open with** → **Notepad**
5. Press **Ctrl+A** (select all), then **Ctrl+C** (copy)

---

## Step 2 — Paste into Firebase

1. Open this link in Chrome/Edge (sign in with your Google account if asked):

   https://console.firebase.google.com/project/recital-tracker/database/recital-tracker-default-rtdb/rules

2. Click inside the big rules text box
3. Press **Ctrl+A** to select what's there
4. Press **Ctrl+V** to paste what you copied from Notepad
5. Click **Publish** (top right)

Done. Scavenger Hunt and Recital can read/write the database when signed in.

---

## Step 3 — Email sign-in (if not already on)

1. Open:

   https://console.firebase.google.com/project/recital-tracker/authentication/providers

2. Click **Email/Password**
3. Turn **Enable** on → **Save**

---

## If Realtime Database doesn't exist yet

1. https://console.firebase.google.com/project/recital-tracker/database
2. Click **Create Database**
3. Pick a region → **Next**
4. Choose **Start in locked mode** → **Enable**
5. Then do Step 2 above (paste rules → Publish)

---

## You do NOT need

- To paste folder paths into Firebase
- Firestore
- A new Firebase project
- Node or PowerShell (this guide uses the website only)
