# Deploy Realtime Database rules for recital-tracker
# (ApexTrack PWA + Scavenger Hunt Android)
#
# First run: browser opens for Google sign-in — use the account that owns recital-tracker.
# Later runs: just deploys rules instantly.

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

Write-Host "Project folder: $Root" -ForegroundColor Cyan
Write-Host "Firebase project: recital-tracker" -ForegroundColor Cyan

# Ensure firebase on PATH (npm global)
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
Write-Host "Deploying database.rules.json ..." -ForegroundColor Green
firebase use recital-tracker
firebase deploy --only database

Write-Host ""
Write-Host "Done. Rules live for: recitals, joinCodes, users, apextrack" -ForegroundColor Green
