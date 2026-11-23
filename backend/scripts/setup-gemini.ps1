$ErrorActionPreference = 'Stop'
$envPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.env'))
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Missing backend/.env. Create local configuration first.' }
$secure = Read-Host 'Gemini API Key (hidden input)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $apiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim() }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
try {
    if ($apiKey.Length -ge 2 -and (($apiKey.StartsWith('"') -and $apiKey.EndsWith('"')) -or ($apiKey.StartsWith("'") -and $apiKey.EndsWith("'")))) {
        $apiKey = $apiKey.Substring(1, $apiKey.Length - 2).Trim()
    }
    if ([string]::IsNullOrWhiteSpace($apiKey) -or $apiKey -match '^[*.\u2022\u2026]+$') {
        throw 'API key is empty or masked. Use Copy API key in Google AI Studio, not the displayed dots/stars. No configuration was changed.'
    }
    if ($apiKey -match '\s') {
        throw 'API key contains spaces or line breaks. Copy only the key value, not a command or configuration snippet. No configuration was changed.'
    }
    # Keys are opaque; allow token characters used by both standard and authorization keys. Google validates authenticity.
    if ($apiKey.Length -lt 20 -or $apiKey.Length -gt 4096 -or $apiKey -notmatch '^[A-Za-z0-9._~+/-]+={0,2}$') {
        throw 'API key is incomplete or contains unsupported characters. Copy the full key value using Copy API key in Google AI Studio. No configuration was changed.'
    }
    $before = [IO.File]::ReadAllText($envPath)
    $after = $before
    $values = @{ GCMB_AI_ENABLED = 'true'; GEMINI_API_KEY = $apiKey; GCMB_AI_MODEL = 'gemini-3.5-flash-lite' }
    foreach ($key in $values.Keys) {
        $pattern = '(?m)^' + [regex]::Escape($key) + '=[^\r\n]*'
        if ([regex]::Matches($after, $pattern).Count -gt 1) { throw "Duplicate $key. No configuration was changed." }
        $entry = $key + '=' + $values[$key]
        if ([regex]::IsMatch($after, $pattern)) {
            $after = [regex]::Replace($after, $pattern, [Text.RegularExpressions.MatchEvaluator]{ param($match) $entry })
        } else { $after = $after.TrimEnd("`r", "`n") + "`r`n" + $entry + "`r`n" }
    }
    [IO.File]::WriteAllText($envPath, $after, (New-Object Text.UTF8Encoding($false)))
    Write-Host 'Gemini configured locally. Key was not printed. Restart backend. Use a Free Tier project without billing for free usage.'
} finally { $apiKey = $null; $secure = $null; $values = $null; $before = $null; $after = $null }
