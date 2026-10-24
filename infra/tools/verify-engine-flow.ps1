# Development aid: verifies the AI move is computed by engine-service over the bus.
#
# The claim under test: the client never supplies the computer's move. It plays, and the reply
# arrives asynchronously through RabbitMQ — so the game gains two moves, and an attempt to move
# for the computer is refused.
#
# Requires: infra up, auth-service 8081, game-service 8082, engine-service 8083, gateway 8080.
param([string]$Gateway = 'http://localhost:8080', [int]$WaitForEngineSeconds = 20)

$ErrorActionPreference = 'Stop'

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

$email = "eng-$([guid]::NewGuid().ToString('N').Substring(0,8))@example.com"
$reg = Invoke-RestMethod -Uri "$Gateway/api/auth/register" -Method Post -ContentType 'application/json' `
    -WebSession $s -Body (@{ email = $email; password = 'password123'; displayName = 'Engine Test' } | ConvertTo-Json)
Write-Output ("REGISTER       user=" + $reg.user.id.Substring(0,8))

$game = Invoke-RestMethod -Uri "$Gateway/api/games" -Method Post -ContentType 'application/json' `
    -WebSession $s -Body '{"mode":"HUMAN_VS_AI","preferredColor":"WHITE","difficulty":"MEDIUM"}'
Write-Output ("CREATE_GAME    id=" + $game.id.Substring(0,8) + " aiColor=" + $game.aiColor + " moves=" + $game.moves.Count)

$after = Invoke-RestMethod -Uri "$Gateway/api/games/$($game.id)/move" -Method Post -ContentType 'application/json' `
    -WebSession $s -Body '{"from":"e2","to":"e4"}'
Write-Output ("HUMAN_MOVE     uci=" + $after.moves[-1].uci + " movesNow=" + $after.moves.Count + " turn=" + $after.turn)

Write-Output 'waiting for the engine reply over the bus...'
$final = $after
for ($i = 0; $i -lt $WaitForEngineSeconds; $i++) {
    Start-Sleep -Seconds 1
    $final = Invoke-RestMethod -Uri "$Gateway/api/games/$($game.id)"
    if ($final.moves.Count -ge 2) { break }
}

if ($final.moves.Count -ge 2) {
    Write-Output ("ENGINE_REPLIED uci=" + $final.moves[1].uci + " totalMoves=" + $final.moves.Count + " turn=" + $final.turn)
} else {
    Write-Output ("ENGINE_REPLY   NEVER_ARRIVED moves=" + $final.moves.Count + "  <-- BUS WIRING BROKEN")
}

# It is white's turn and white is the human. Sending a black pawn move must be rejected — a
# caller cannot simply move the opponent's pieces.
#
# NOTE: this proves the *illegality* check, not the AI-seat rule. The AI-seat rule ("the owner may
# not play the computer's move") cannot be observed reliably here — the engine replies within about
# a second, so the window is racy — and is covered deterministically by
# GameServiceTest.ownerCannotMoveForTheComputer instead.
$status = 0
try {
    Invoke-RestMethod -Uri "$Gateway/api/games/$($game.id)/move" -Method Post -ContentType 'application/json' `
        -WebSession $s -Body '{"from":"a7","to":"a6"}' | Out-Null
} catch { $status = [int]$_.Exception.Response.StatusCode }

if ($status -eq 422) {
    Write-Output 'OPPONENT_PIECE_MOVE status=422 (correct: not a white move)'
} else {
    Write-Output ("OPPONENT_PIECE_MOVE status=$status  <-- EXPECTED 422")
}
