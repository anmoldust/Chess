# End-to-end acceptance run against a composed stack.
#
#   powershell -File scripts/e2e.ps1 -Base http://localhost:8080
#   powershell -File scripts/e2e.ps1 -Base http://localhost:3000     # through nginx
#
# Exercises the whole platform: register -> matchmake -> play to mate -> notifications + analysis.
param(
    [string]$Base = 'http://localhost:8080',
    [int]$WaitSeconds = 30
)

$ErrorActionPreference = 'Stop'
$script:failures = 0

function Check([string]$name, [bool]$passed, [string]$detail) {
    if ($passed) {
        Write-Output ("PASS  " + $name + $(if ($detail) { "  (" + $detail + ")" } else { "" }))
    } else {
        $script:failures++
        Write-Output ("FAIL  " + $name + "  " + $detail)
    }
}

# --- reachability -----------------------------------------------------------
$up = $false
for ($i = 0; $i -lt 60; $i++) {
    try { Invoke-RestMethod -Uri "$Base/api/auth/me" -TimeoutSec 4 | Out-Null; $up = $true; break }
    catch { Start-Sleep -Seconds 2 }
}
Check 'platform reachable' $up $Base

function NewPlayer([string]$name) {
    $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    Invoke-RestMethod -Uri "$Base/api/auth/me" -WebSession $s | Out-Null
    $s.Headers['X-XSRF-TOKEN'] = ($s.Cookies.GetCookies($Base) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }).Value
    $email = "$name-$([guid]::NewGuid().ToString('N').Substring(0,8))@example.com"
    $reg = Invoke-RestMethod -Uri "$Base/api/auth/register" -Method Post -ContentType 'application/json' `
        -WebSession $s -Body (@{ email = $email; password = 'password123'; displayName = $name } | ConvertTo-Json)
    return @{ Session = $s; UserId = $reg.user.id; Name = $name }
}

# --- registration -----------------------------------------------------------
$alpha = NewPlayer 'Alpha'
$bravo = NewPlayer 'Bravo'
Check 'two accounts registered' ($alpha.UserId -and $bravo.UserId) "$($alpha.UserId.Substring(0,8)) / $($bravo.UserId.Substring(0,8))"

$me = Invoke-RestMethod -Uri "$Base/api/auth/me" -WebSession $alpha.Session
Check 'session established' ($me.authenticated -eq $true) $me.user.email

# --- matchmaking ------------------------------------------------------------
foreach ($p in @($alpha, $bravo)) {
    $p.Session.Headers['X-XSRF-TOKEN'] = ($p.Session.Cookies.GetCookies($Base) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }).Value
    Invoke-RestMethod -Uri "$Base/api/matchmaking/queue" -Method Post -ContentType 'application/json' `
        -WebSession $p.Session -Body '{"timeControl":"BLITZ"}' | Out-Null
}

$match = $null
for ($i = 0; $i -lt $WaitSeconds; $i++) {
    Start-Sleep -Seconds 1
    $match = Invoke-RestMethod -Uri "$Base/api/matchmaking/status" -WebSession $alpha.Session
    if ($match.matched) { break }
}
Check 'players matched' ($match.matched -eq $true) $(if ($match.gameId) { $match.gameId.Substring(0,8) } else { 'no game' })

$theirMatch = Invoke-RestMethod -Uri "$Base/api/matchmaking/status" -WebSession $bravo.Session
Check 'both players sent to the same game' ($theirMatch.gameId -eq $match.gameId) $match.gameId

# --- the announced game exists, with both seated ----------------------------
$game = $null
for ($i = 0; $i -lt $WaitSeconds; $i++) {
    try { $game = Invoke-RestMethod -Uri "$Base/api/games/$($match.gameId)" -TimeoutSec 5; break } catch { Start-Sleep -Seconds 1 }
}
Check 'game created from the match event' ($null -ne $game) $(if ($game) { $game.status } else { 'missing' })

if ($game) {
    $seated = @($game.whiteUserId, $game.blackUserId)
    Check 'both matched players seated' (($seated -contains $alpha.UserId) -and ($seated -contains $bravo.UserId)) "$($game.whitePlayer) vs $($game.blackPlayer)"

    # --- play to checkmate --------------------------------------------------
    $whiteSession = if ($game.whiteUserId -eq $alpha.UserId) { $alpha.Session } else { $bravo.Session }
    $blackSession = if ($game.whiteUserId -eq $alpha.UserId) { $bravo.Session } else { $alpha.Session }

    function Play($session, [string]$from, [string]$to) {
        Invoke-RestMethod -Uri "$Base/api/games/$($game.id)/move" -Method Post -ContentType 'application/json' `
            -WebSession $session -Body (@{ from = $from; to = $to } | ConvertTo-Json) | Out-Null
    }

    Play $whiteSession 'f2' 'f3'
    Play $blackSession 'e7' 'e5'
    Play $whiteSession 'g2' 'g4'
    Play $blackSession 'd8' 'h4'

    $final = Invoke-RestMethod -Uri "$Base/api/games/$($game.id)"
    Check "Fool's Mate ends the game" ($final.status -eq 'BLACK_WON') "$($final.status) $($final.result)"

    # --- fan-out ------------------------------------------------------------
    $notifications = $null
    for ($i = 0; $i -lt $WaitSeconds; $i++) {
        Start-Sleep -Seconds 1
        try { $notifications = Invoke-RestMethod -Uri "$Base/api/notifications" -WebSession $alpha.Session } catch { }
        if ($notifications | Where-Object { $_.kind -eq 'GAME_ENDED' }) { break }
    }
    $kinds = ($notifications | ForEach-Object { $_.kind }) -join ','
    Check 'game.ended notification recorded' (($kinds -split ',') -contains 'GAME_ENDED') $kinds

    $report = $null
    for ($i = 0; $i -lt $WaitSeconds; $i++) {
        try { $report = Invoke-RestMethod -Uri "$Base/api/analysis/$($game.id)" -TimeoutSec 5; break } catch { Start-Sleep -Seconds 1 }
    }
    Check 'analysis report produced' ($null -ne $report) $(if ($report) { "moves=$($report.movesAnalysed) engineBacked=$($report.engineBacked)" } else { 'missing' })
}

Write-Output ''
if ($script:failures -eq 0) {
    Write-Output 'E2E: ALL CHECKS PASSED'
    exit 0
}
Write-Output ("E2E: " + $script:failures + " CHECK(S) FAILED")
exit 1
