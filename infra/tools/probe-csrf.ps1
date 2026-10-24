# Development aid: determines whether the gateway's CSRF check can be bypassed, or whether an
# earlier verification produced a false positive because PowerShell's WebRequestSession
# accumulates -Headers across calls.
#
#   powershell -File infra/tools/probe-csrf.ps1
param([string]$Gateway = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'

$up = $false
for ($i = 0; $i -lt 90; $i++) {
    try { Invoke-RestMethod -Uri "$Gateway/actuator/health" -TimeoutSec 4 | Out-Null; $up = $true; break }
    catch { Start-Sleep -Seconds 2 }
}
if (-not $up) { Write-Output 'GATEWAY_NOT_UP'; exit 1 }

function NewEmail([string]$prefix) {
    return "$prefix-$([guid]::NewGuid().ToString('N').Substring(0,6))@example.com"
}

function TryRegister($session, $prefix, $headers) {
    $body = @{ email = (NewEmail $prefix); password = 'password123'; displayName = $prefix } | ConvertTo-Json
    try {
        if ($null -eq $headers) {
            Invoke-RestMethod -Uri "$Gateway/api/auth/register" -Method Post -ContentType 'application/json' `
                -WebSession $session -Body $body | Out-Null
        } else {
            Invoke-RestMethod -Uri "$Gateway/api/auth/register" -Method Post -ContentType 'application/json' `
                -WebSession $session -Headers $headers -Body $body | Out-Null
        }
        return '2xx'
    } catch {
        return ([int]$_.Exception.Response.StatusCode).ToString()
    }
}

$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Uri "$Gateway/api/auth/me" -WebSession $s | Out-Null
$token = ($s.Cookies.GetCookies($Gateway) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }).Value

Write-Output ("session.Headers initially      = [" + (($s.Headers.Keys) -join ',') + "]")

Write-Output ("1 no header                    = " + (TryRegister $s 'A' $null))
Write-Output ("  session.Headers now          = [" + (($s.Headers.Keys) -join ',') + "]")

Write-Output ("2 with header                  = " + (TryRegister $s 'B' @{ 'X-XSRF-TOKEN' = $token }))
Write-Output ("  session.Headers now          = [" + (($s.Headers.Keys) -join ',') + "]")
Write-Output ("  accumulated value            = " + $s.Headers['X-XSRF-TOKEN'])

# If the harness is the culprit, call 3 still sends the header from call 2 and therefore passes.
Write-Output ("3 no header again              = " + (TryRegister $s 'C' $null))

# Removing it explicitly is the real test: this is what a genuine cross-site request looks like.
$s.Headers.Remove('X-XSRF-TOKEN')
Write-Output ("  session.Headers after remove = [" + (($s.Headers.Keys) -join ',') + "]")
Write-Output ("4 no header, explicitly cleared = " + (TryRegister $s 'D' $null))

$s.Headers.Remove('X-XSRF-TOKEN')
try {
    Invoke-WebRequest -Uri "$Gateway/api/auth/logout" -Method Post -WebSession $s -UseBasicParsing | Out-Null
    Write-Output '5 logout, no header            = 2xx'
} catch { Write-Output ("5 logout, no header            = " + [int]$_.Exception.Response.StatusCode) }
