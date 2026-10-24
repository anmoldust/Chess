# Development aid: end-to-end verification of the gateway's browser-facing behaviour.
#
# Checks the session, CSRF enforcement, and — the point of the whole design — that a proxied
# request reaches the downstream with a Bearer token the browser never saw.
#
# NOTE ON THE HARNESS: PowerShell's WebRequestSession persists -Headers across calls, so an
# earlier version of this script sent a CSRF header it believed it had omitted and reported a
# false "CSRF bypass". CSRF state is therefore now set explicitly on $session.Headers before
# every mutating call — never via -Headers.
#
# Requires: infra up (postgres, rabbitmq, redis), auth-service on 8081, gateway on 8080,
# and (to observe the token) infra/tools/stub-downstream.ps1 on 8082.
param(
    [string]$Gateway = 'http://localhost:8080',
    [string]$CaptureFile = "$PSScriptRoot\stub-capture.log",
    [string]$GatewayLog = ''
)

$ErrorActionPreference = 'Stop'

$up = $false
for ($i = 0; $i -lt 90; $i++) {
    try { Invoke-RestMethod -Uri "$Gateway/actuator/health" -TimeoutSec 4 | Out-Null; $up = $true; break }
    catch { Start-Sleep -Seconds 2 }
}
if (-not $up) {
    Write-Output 'GATEWAY_NOT_UP'
    if ($GatewayLog -and (Test-Path $GatewayLog)) { Get-Content $GatewayLog -Tail 30 }
    exit 1
}
Write-Output 'GATEWAY_UP'

function CsrfValue($session) {
    return ($session.Cookies.GetCookies($Gateway) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }).Value
}

# Explicit, unambiguous control over whether a CSRF header is sent.
function SetCsrf($session, [bool]$send) {
    if ($send) { $session.Headers['X-XSRF-TOKEN'] = (CsrfValue $session) }
    else { $session.Headers.Remove('X-XSRF-TOKEN') }
}

function Status($errorRecord) {
    return [int]$errorRecord.Exception.Response.StatusCode
}

$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Uri "$Gateway/api/auth/me" -WebSession $s | Out-Null

$me = Invoke-RestMethod -Uri "$Gateway/api/auth/me" -WebSession $s
Write-Output ("ANON_ME                      authenticated=" + $me.authenticated)

try { Invoke-RestMethod -Uri "$Gateway/api/games" -WebSession $s | Out-Null; Write-Output 'ANON_GAMES_READ              ok' }
catch { Write-Output ("ANON_GAMES_READ              status=" + (Status $_)) }

SetCsrf $s $true
try {
    Invoke-RestMethod -Uri "$Gateway/api/games" -Method Post -ContentType 'application/json' `
        -Body '{"mode":"HUMAN_VS_HUMAN"}' -WebSession $s | Out-Null
    Write-Output 'ANON_POST                    UNEXPECTED_OK'
} catch { Write-Output ("ANON_POST                    status=" + (Status $_)) }

# CSRF must reject a mutation that genuinely omits the header.
SetCsrf $s $false
try {
    Invoke-RestMethod -Uri "$Gateway/api/auth/register" -Method Post -ContentType 'application/json' `
        -WebSession $s -Body (@{ email = "no-csrf-$([guid]::NewGuid().ToString('N').Substring(0,6))@example.com"; password = 'password123'; displayName = 'No Csrf' } | ConvertTo-Json) | Out-Null
    Write-Output 'CSRF_MISSING_REJECTED        UNEXPECTED_OK  <-- REAL BYPASS'
} catch { Write-Output ("CSRF_MISSING_REJECTED        status=" + (Status $_)) }

SetCsrf $s $true
$email = "gw-$([guid]::NewGuid().ToString('N').Substring(0,8))@example.com"
$reg = Invoke-RestMethod -Uri "$Gateway/api/auth/register" -Method Post -ContentType 'application/json' `
    -WebSession $s -Body (@{ email = $email; password = 'password123'; displayName = 'Gateway User' } | ConvertTo-Json)
Write-Output ("REGISTER                     user=" + $reg.user.id.Substring(0,8) + " tokenFromAuthService=" + ($reg.token.Length -gt 100))

$me2 = Invoke-RestMethod -Uri "$Gateway/api/auth/me" -WebSession $s
Write-Output ("SESSION_ME                   authenticated=" + $me2.authenticated + " email=" + $me2.user.email)

$sessionCookie = ($s.Cookies.GetCookies($Gateway) | Where-Object { $_.Name -eq 'SESSION' }).Value
Write-Output ("SESSION_COOKIE               present=" + ($null -ne $sessionCookie))

try { Invoke-RestMethod -Uri "$Gateway/api/games" -WebSession $s | Out-Null; Write-Output 'PROXIED_CALL                 ok' }
catch { Write-Output ("PROXIED_CALL                 status=" + (Status $_)) }

SetCsrf $s $false
try {
    Invoke-WebRequest -Uri "$Gateway/api/auth/logout" -Method Post -WebSession $s -UseBasicParsing | Out-Null
    Write-Output 'LOGOUT_WITHOUT_CSRF          UNEXPECTED_OK  <-- REAL BYPASS'
} catch { Write-Output ("LOGOUT_WITHOUT_CSRF          status=" + (Status $_)) }

SetCsrf $s $true
try {
    Invoke-WebRequest -Uri "$Gateway/api/auth/logout" -Method Post -WebSession $s -UseBasicParsing | Out-Null
    Write-Output 'LOGOUT_WITH_CSRF             ok'
} catch { Write-Output ("LOGOUT_WITH_CSRF             status=" + (Status $_)) }

$me3 = Invoke-RestMethod -Uri "$Gateway/api/auth/me" -WebSession $s
Write-Output ("AFTER_LOGOUT                 authenticated=" + $me3.authenticated)

Write-Output '=== what the downstream stub received (token redacted) ==='
if (Test-Path $CaptureFile) {
    Get-Content $CaptureFile | ForEach-Object { $_ -replace 'Bearer [A-Za-z0-9._\-]+', 'Bearer <REDACTED-JWT>' }
} else {
    Write-Output '(no capture file)'
}
