# Development aid: two players queue, get paired, and a real game appears.
#
# The claim under test: matchmaking pairs two waiting players and the game it announces is
# actually created by game-service — i.e. the matchmaking.match.found event is consumed.
#
# Requires: infra up, auth-service 8081, game-service 8082, matchmaking-service 8084, gateway 8080.
param([string]$Gateway = 'http://localhost:8080', [int]$WaitForMatchSeconds = 20)

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

function NewPlayer([string]$prefix) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    Invoke-RestMethod -Uri "$Gateway/api/auth/me" -WebSession $session | Out-Null
    $session.Headers['X-XSRF-TOKEN'] = (CsrfValue $session)
    $email = "$prefix-$([guid]::NewGuid().ToString('N').Substring(0,8))@example.com"
    $reg = Invoke-RestMethod -Uri "$Gateway/api/auth/register" -Method Post -ContentType 'application/json' `
        -WebSession $session -Body (@{ email = $email; password = 'password123'; displayName = $prefix } | ConvertTo-Json)
    return @{ Session = $session; UserId = $reg.user.id; Name = $prefix }
}

$one = NewPlayer 'Alpha'
$two = NewPlayer 'Bravo'
Write-Output ("PLAYERS       " + $one.Name + "=" + $one.UserId.Substring(0,8) + "  " + $two.Name + "=" + $two.UserId.Substring(0,8))

$queueBody = '{"timeControl":"BLITZ"}'
$q1 = Invoke-RestMethod -Uri "$Gateway/api/matchmaking/queue" -Method Post -ContentType 'application/json' `
    -WebSession $one.Session -Body $queueBody
Write-Output ("QUEUE_ALPHA   queued=" + $q1.queued + " position=" + $q1.position)

$q2 = Invoke-RestMethod -Uri "$Gateway/api/matchmaking/queue" -Method Post -ContentType 'application/json' `
    -WebSession $two.Session -Body $queueBody
Write-Output ("QUEUE_BRAVO   queued=" + $q2.queued)

# Idempotency: queueing again must not add a second entry.
$q1again = Invoke-RestMethod -Uri "$Gateway/api/matchmaking/queue" -Method Post -ContentType 'application/json' `
    -WebSession $one.Session -Body $queueBody
Write-Output ("QUEUE_AGAIN   queued=" + $q1again.queued)

Write-Output 'waiting for the matcher...'
$status = $null
for ($i = 0; $i -lt $WaitForMatchSeconds; $i++) {
    Start-Sleep -Seconds 1
    $status = Invoke-RestMethod -Uri "$Gateway/api/matchmaking/status" -WebSession $one.Session
    if ($status.matched) { break }
}

if (-not $status.matched) {
    Write-Output 'MATCH         NEVER_HAPPENED  <-- MATCHER BROKEN'
    exit 1
}
Write-Output ("MATCH         matched=true gameId=" + $status.gameId.Substring(0,8))

$statusTwo = Invoke-RestMethod -Uri "$Gateway/api/matchmaking/status" -WebSession $two.Session
if ($statusTwo.gameId -eq $status.gameId) {
    Write-Output 'SAME_GAME     both players were sent to the same game'
} else {
    Write-Output ("SAME_GAME     MISMATCH alpha=" + $status.gameId + " bravo=" + $statusTwo.gameId)
}

# The announced game must actually exist in game-service, created from the event. The event is
# consumed asynchronously, so poll rather than assuming it has landed by the time we ask.
$game = $null
for ($i = 0; $i -lt $WaitForMatchSeconds; $i++) {
    try { $game = Invoke-RestMethod -Uri "$Gateway/api/games/$($status.gameId)" -TimeoutSec 5; break }
    catch { Start-Sleep -Seconds 1 }
}
if ($null -eq $game) {
    Write-Output 'GAME_CREATED  NEVER_APPEARED  <-- MATCH FOUND EVENT NOT CONSUMED'
    exit 1
}
Write-Output ("GAME_CREATED  white=" + $game.whitePlayer + " black=" + $game.blackPlayer +
    " status=" + $game.status + " mode=" + $game.mode)

# And the paired players must be the ones seated.
$seated = @($game.whiteUserId, $game.blackUserId)
if ($seated -contains $one.UserId -and $seated -contains $two.UserId) {
    Write-Output 'SEATING       both queued players are seated'
} else {
    Write-Output 'SEATING       MISMATCH  <-- paired players are not the seat owners'
}
