# Add Vercel domains to Firebase Auth authorized domains (fixes PWA sign-in)
#
# First run: browser opens for Google sign-in — use the account that owns recital-tracker.

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

$domainsToAdd = @(
  "scavenger-hunt-live.vercel.app",
  "my-scavenger-hunt.vercel.app",
  "gps-scavenger-hunt.vercel.app",
  "hunt-scavenger.vercel.app",
  "scavenger-hunt-guyblais99-cells-projects.vercel.app",
  "recital-tracker.vercel.app",
  "recital-tracker.web.app"
)

$npmGlobal = "$env:APPDATA\npm"
if (Test-Path $npmGlobal) { $env:Path = "$npmGlobal;$env:Path" }

if (-not (Get-Command firebase -ErrorAction SilentlyContinue)) {
  Write-Host "Installing Firebase CLI..." -ForegroundColor Yellow
  npm install -g firebase-tools
  $env:Path = "$env:APPDATA\npm;$env:Path"
}

$accounts = firebase login:list 2>&1 | Out-String
if ($accounts -match "No authorized accounts") {
  Write-Host ""
  Write-Host "Sign in with Google (recital-tracker owner account)..." -ForegroundColor Yellow
  firebase login
}

Write-Host ""
Write-Host "Firebase cannot add authorized domains via CLI yet." -ForegroundColor Yellow
Write-Host "Opening the Authorized domains page in your browser..." -ForegroundColor Cyan
Write-Host ""
Write-Host "Add these domains (one at a time, no https://):" -ForegroundColor Green
foreach ($d in $domainsToAdd) { Write-Host "  - $d" }

$consoleUrl = "https://console.firebase.google.com/project/recital-tracker/authentication/settings"
Start-Process $consoleUrl

Write-Host ""
Write-Host "After adding domains, wait ~1 minute, then try Sign in again on the app." -ForegroundColor Cyan
