# Development aid: end-to-end game flow through the gateway across three services.
#
# register (gateway -> auth-service) -> create game (gateway -> game-service) -> move -> read.
# Proves the browser never sees a token and that identity survives every hop.
#
# Requires: infra up, auth-service 8081, game-service 8082, gateway 8080.
param([string]$Gateway = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'

foreach ($name in 'actuator/health') { }
$up = $false
for ($i = 0; $i -lt 120; $i++) {
    try { Invoke-RestMethod -Uri "$Gateway/actuator/health" -TimeoutSec 4 | Out-Null; $up = $true; break }
    catch { Start-Sleep -Seconds 2 }
}
if (-not $up) { Write-Output 'GATEWAY_NOT_UP'; exit 1 }
Write-Output 'GATEWAY_UP'

function CsrfValue($session) {
    return ($session.Cookies.GetCookies($Gateway) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }).Value
}
function SetCsrf($session, [bool]$send) {
    if ($send) { $session.Headers['X-XSRF-TOKEN'] = (CsrfValue $session) }
    else { $session.Headers.Remove('X-XSRF-TOKEN') }
}

$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Uri "$Gateway/api/auth/me" -WebSession $s | Out-Null
SetCsrf $s $true

$email = "e2e-$([guid]::NewGuid().ToString('N').Substring(0,8))@example.com"
$reg = Invoke-RestMethod -Uri "$Gateway/api/auth/register" -Method Post -ContentType 'application/json' `
    -WebSession $s -Body (@{ email = $email; password = 'password123'; displayName = 'E2E Player' } | ConvertTo-Json)
Write-Output ("REGISTER      user=" + $reg.user.id.Substring(0,8) + " name=" + $reg.user.displayName)

$game = Invoke-RestMethod -Uri "$Gateway/api/games" -Method Post -ContentType 'application/json' `
    -WebSession $s -Body '{"mode":"HUMAN_VS_AI","preferredColor":"WHITE"}'
Write-Output ("CREATE_GAME   id=" + $game.id.Substring(0,8) + " white=" + $game.whitePlayer +
    " black=" + $game.blackPlayer + " aiColor=" + $game.aiColor + " status=" + $game.status)

$moved = Invoke-RestMethod -Uri "$Gateway/api/games/$($game.id)/move" -Method Post -ContentType 'application/json' `
    -WebSession $s -Body '{"from":"e2","to":"e4"}'
Write-Output ("MOVE          uci=" + $moved.moves[-1].uci + " turn=" + $moved.turn + " version=" + $moved.version)

try {
    Invoke-RestMethod -Uri "$Gateway/api/games/$($game.id)/move" -Method Post -ContentType 'application/json' `
        -WebSession $s -Body '{"from":"e7","to":"e4"}' | Out-Null
    Write-Output 'ILLEGAL_MOVE  UNEXPECTED_OK'
} catch { Write-Output ("ILLEGAL_MOVE  status=" + [int]$_.Exception.Response.StatusCode) }

# A second account must not be able to move in the first player's game.
$other = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Uri "$Gateway/api/auth/me" -WebSession $other | Out-Null
SetCsrf $other $true
Invoke-RestMethod -Uri "$Gateway/api/auth/register" -Method Post -ContentType 'application/json' `
    -WebSession $other -Body (@{ email = "other-$([guid]::NewGuid().ToString('N').Substring(0,8))@example.com"; password = 'password123'; displayName = 'Intruder' } | ConvertTo-Json) | Out-Null
try {
    Invoke-RestMethod -Uri "$Gateway/api/games/$($game.id)/move" -Method Post -ContentType 'application/json' `
        -WebSession $other -Body '{"from":"e7","to":"e5"}' | Out-Null
    Write-Output 'INTRUDER_MOVE UNEXPECTED_OK  <-- AUTHORIZATION HOLE'
} catch { Write-Output ("INTRUDER_MOVE status=" + [int]$_.Exception.Response.StatusCode) }

$anon = Invoke-RestMethod -Uri "$Gateway/api/games/$($game.id)"
Write-Output ("ANON_READ     fen=" + $anon.fen.Substring(0, 20) + " moves=" + $anon.moves.Count)
