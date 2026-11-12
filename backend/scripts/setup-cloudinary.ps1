# Author: HuyDV. Nhap Cloudinary rieng tren may; giu nguyen database, Gmail va SePay.
param([switch]$SecretFromClipboard)

$ErrorActionPreference = 'Stop'
function Assert-CloudinaryConfig {
    param([string]$CloudName, [string]$ApiKey, [string]$ApiSecret)
    if ($CloudName -cnotmatch '^[a-z0-9_-]+$') {
        throw 'Cloud Name is invalid. Copy only the Cloud name value, not the account label or CLOUDINARY_URL. No configuration was changed.'
    }
    if ($ApiKey -notmatch '^\d+$') {
        throw 'API Key is invalid. Copy the numeric API Key, not its display name or API Secret. No configuration was changed.'
    }
    if ([string]::IsNullOrWhiteSpace($ApiSecret) -or $ApiSecret -match '^[*\u2022\u25cf]+$' -or $ApiSecret -eq '<your_api_secret>') {
        throw 'API Secret is empty or masked. Reveal/copy the actual API Secret in Cloudinary, not the displayed dots or stars. No configuration was changed.'
    }
    if ($ApiSecret -match "['\p{Cc}]") {
        throw 'API Secret contains a quote or control character that cannot be saved in this configuration. Copy only the Secret value. No configuration was changed.'
    }
}
$envPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.env'))
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Missing backend/.env. Create your local configuration first.' }
$cloudName = (Read-Host 'Cloudinary Cloud Name').Trim()
$apiKey = (Read-Host 'Cloudinary API Key').Trim()
if ($SecretFromClipboard) {
    [void](Read-Host 'Copy the actual API Secret in Cloudinary, return here and press Enter. Do NOT paste it here')
    $apiSecret = ([string](Get-Clipboard -Raw)).Trim()
} else {
    $secureSecret = Read-Host 'Cloudinary API Secret (hidden input)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecret)
    try { $apiSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim() }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secureSecret.Dispose() }
}
try {
    Assert-CloudinaryConfig $cloudName $apiKey $apiSecret
    $before = [System.IO.File]::ReadAllText($envPath)
    $newline = if ($before.Contains("`r`n")) { "`r`n" } else { "`n" }
    $values = [ordered]@{
        CLOUDINARY_ENABLED = 'true'
        CLOUDINARY_CLOUD_NAME = $cloudName
        CLOUDINARY_API_KEY = $apiKey
        CLOUDINARY_API_SECRET = "'" + $apiSecret + "'"
    }
    $after = $before
    foreach ($key in $values.Keys) {
        $pattern = '(?m)^' + [regex]::Escape($key) + '=[^\r\n]*'
        if ([regex]::Matches($after, $pattern).Count -gt 1) { throw "Duplicate $key in .env. No configuration was changed." }
        $entry = $key + '=' + $values[$key]
        if ([regex]::IsMatch($after, $pattern)) {
            $after = [regex]::Replace($after, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $entry })
        }
        else {
            if ($after.Length -gt 0 -and -not $after.EndsWith("`n")) { $after += $newline }
            $after += $entry + $newline
        }
    }
    [System.IO.File]::WriteAllText($envPath, $after, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host 'Cloudinary configured locally. API Secret was not printed. Restart the backend.'
} finally { $apiSecret = $null; $values = $null; $after = $null; $before = $null }
