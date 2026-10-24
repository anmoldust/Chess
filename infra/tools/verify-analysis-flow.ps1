# Development aid: a finished game produces a notification and an analysis report.
#
# Claim under test: game.ended fans out — notification-service records it for both players, and
# analysis-service fetches the moves from game-service and produces a report.
#
# Requires: infra up, auth-service 8081, game-service 8082, notification-service 8086,
# analysis-service 8087, gateway 8080.
param([string]$Gateway = 'http://localhost:8080', [int]$WaitSeconds = 25)

$ErrorActionPreference = 'Stop'

$up = $false
for ($i = 0; $i -lt 120; $i++) {
    try { Invoke-RestMethod -Uri "$Gateway/actuator/health" -TimeoutSec 4 | Out-Null; $up = $true; break }
    catch { Start-Sleep -Seconds 2 }
}
if (-not $up) { Write-Output 'GATEWAY_NOT_UP'; exit 1 }
Write-Output 'GATEWAY_UP'

function NewPlayer([string]$name) {
    $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    Invoke-RestMethod -Uri "$Gateway/api/auth/me" -WebSession $s | Out-Null
    $s.Headers['X-XSRF-TOKEN'] = ($s.Cookies.GetCookies($Gateway) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }).Value
    $email = "$name-$([guid]::NewGuid().ToString('N').Substring(0,8))@example.com"
    Invoke-RestMethod -Uri "$Gateway/api/auth/register" -Method Post -ContentType 'application/json' `
        -WebSession $s -Body (@{ email = $email; password = 'password123'; displayName = $name } | ConvertTo-Json) | Out-Null
    return $s
}

$white = NewPlayer 'White'
$black = NewPlayer 'Black'

$game = Invoke-RestMethod -Uri "$Gateway/api/games" -Method Post -ContentType 'application/json' `
    -WebSession $white -Body '{"mode":"HUMAN_VS_HUMAN","preferredColor":"WHITE"}'
Invoke-RestMethod -Uri "$Gateway/api/games/$($game.id)/join" -Method Post -WebSession $black | Out-Null
Write-Output ("GAME          id=" + $game.id.Substring(0,8) + " started")

function Play($session, $from, $to) {
    $body = @{ from = $from; to = $to } | ConvertTo-Json
    Invoke-RestMethod -Uri "$Gateway/api/games/$($game.id)/move" -Method Post -ContentType 'application/json' `
        -WebSession $session -Body $body | Out-Null
}

Play $white 'f2' 'f3'
Play $black 'e7' 'e5'
Play $white 'g2' 'g4'
Play $black 'd8' 'h4'   # Fool's Mate
$final = Invoke-RestMethod -Uri "$Gateway/api/games/$($game.id)"
Write-Output ("RESULT        " + $final.status + " " + $final.result)

Write-Output 'waiting for notification + analysis fan-out...'
$notifications = $null
$report = $null
$lastAnalysisStatus = $null
for ($i = 0; $i -lt $WaitSeconds; $i++) {
    Start-Sleep -Seconds 1
    try { $notifications = Invoke-RestMethod -Uri "$Gateway/api/notifications" -WebSession $white } catch { }
    try {
        $report = Invoke-RestMethod -Uri "$Gateway/api/analysis/$($game.id)" -TimeoutSec 5
        $lastAnalysisStatus = 200
    } catch {
        $lastAnalysisStatus = try { [int]$_.Exception.Response.StatusCode } catch { 'no-response' }
    }
    if ($notifications -and $report) { break }
}

if ($notifications) {
    $kinds = ($notifications | ForEach-Object { $_.kind }) -join ','
    Write-Output ("NOTIFICATIONS count=" + @($notifications).Count + " kinds=" + $kinds)
} else {
    Write-Output 'NOTIFICATIONS NONE  <-- FAN-OUT BROKEN'
}

if ($report) {
    Write-Output ("ANALYSIS      moves=" + $report.movesAnalysed + " blundersWhite=" + $report.blundersWhite +
        " accuracyWhite=" + $report.accuracyWhite + " engineBacked=" + $report.engineBacked)
} else {
    Write-Output ("ANALYSIS      NONE (last status=" + $lastAnalysisStatus + ")  <-- investigate: check" +
        " analysis_db.reports and chess.analysis-service.ended.dlq before blaming the fan-out")
}
