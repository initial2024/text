# Read-only Windows/ADB export of the currently installed OmniGate APK.
# No app data, cookies, accounts, signing keys or Play Services cache copied.
# This DOES NOT upload a private APK to public GitHub.
# Requirement: Android developer USB debugging already authorized by owner.
$ErrorActionPreference = 'Stop'
$Package = 'com.omnigate.core'
$ExpectedSha = 'da1f77fe479190f1f2fb0b1655e0977e02cca85b7a45bfb6bf9fc5ae7a66eb34'
$Output = Join-Path (Get-Location) 'OmniGate-v1.2.9-PRIVATE-BASE.apk'

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw 'ADB not found. Install Android platform-tools and make adb available in PATH.'
}
$devices = @(& adb devices | Select-String '\s+device$')
if ($devices.Count -ne 1) {
    throw ('Exactly one authorized Android device required; found '+$devices.Count)
}
$paths = @(& adb shell pm path $Package)
$match = @($paths | ForEach-Object { $_.Trim() } |
    Where-Object { $_ -match '^package:.*\/base\.apk$' })
if ($match.Count -ne 1) {
    throw ('Installed package '+$Package+' not found or base.apk path ambiguous')
}
$devicePath = $match[0].Substring('package:'.Length).Trim()
if (Test-Path $Output) {
    throw 'Output already exists. Move it aside; never overwrite a private baseline.'
}
& adb pull $devicePath $Output
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $Output)) {
    throw 'ADB pull failed. No files were uploaded or modified on the phone.'
}
$actual = (Get-FileHash -Algorithm SHA256 -Path $Output).Hash.ToLowerInvariant()
Write-Host ('PACKAGE='+$Package)
Write-Host ('FILE='+$Output)
Write-Host ('SHA256='+$actual)
if ($actual -ne $ExpectedSha) {
    Write-Warning 'The installed APK is not the audited v1.2.9 baseline. No patch/sign operation permitted.'
    exit 2
}
Write-Host 'V129_BASELINE_SHA256=PASS'
Write-Host 'The APK contains application code, not user data. Transfer only via a private channel.'
