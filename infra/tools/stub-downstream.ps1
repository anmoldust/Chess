# Development aid: a stub downstream that records the headers it receives.
#
# Used to prove the gateway exchanges a browser session for a Bearer token, i.e. that
# DownstreamAuthFilter really attaches an identity. Point ccmmd.services.game-service at this
# and inspect the capture file.
#
#   powershell -File infra/tools/stub-downstream.ps1 -Port 8082 -CaptureFile stub-capture.log
param(
    [int]$Port = 8082,
    [string]$CaptureFile = "$PSScriptRoot\stub-capture.log"
)

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://localhost:$Port/")
$listener.Start()

Set-Content -Path $CaptureFile -Value "listening on $Port"
Write-Output "stub downstream listening on http://localhost:$Port"

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $request = $context.Request

        $authorization = $request.Headers['Authorization']
        if ([string]::IsNullOrEmpty($authorization)) { $authorization = '<none>' }

        Add-Content -Path $CaptureFile -Value ("{0} {1} | Authorization={2}" -f `
            $request.HttpMethod, $request.Url.AbsolutePath, $authorization)

        $body = [System.Text.Encoding]::UTF8.GetBytes('{"stub":true}')
        $context.Response.StatusCode = 200
        $context.Response.ContentType = 'application/json'
        $context.Response.OutputStream.Write($body, 0, $body.Length)
        $context.Response.Close()
    }
} finally {
    $listener.Stop()
}
