param(
    [string]$PublicBaseUrl = "",
    [string]$LocalBaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$endpoint = "/api/whatsapp/webhook"
$results = New-Object System.Collections.Generic.List[object]

function Probe([string]$name, [string]$base, [string]$method, [string]$path, [string]$body = "", [string]$signature = "") {
    $url = $base.TrimEnd("/") + $path
    $headers = @{}
    if ($signature) { $headers["X-Hub-Signature-256"] = $signature }
    try {
        $params = @{ Uri = $url; Method = $method; Headers = $headers; TimeoutSec = 15; UseBasicParsing = $true }
        if ($method -eq "POST") {
            $params.Body = $body
            $params.ContentType = "application/json"
        }
        $response = Invoke-WebRequest @params
        $status = [int]$response.StatusCode
        $result = [pscustomobject]@{ Check = $name; Status = $status; Detail = "HTTP response" }
    } catch {
        $httpResponse = $_.Exception.Response
        if ($null -ne $httpResponse -and $null -ne $httpResponse.StatusCode) {
            $result = [pscustomobject]@{ Check = $name; Status = [int]$httpResponse.StatusCode; Detail = "HTTP response (non-2xx)" }
        } else {
            $result = [pscustomobject]@{ Check = $name; Status = "-"; Detail = $_.Exception.Message }
        }
    }
    $results.Add($result)
    return $result
}

Write-Host "WhatsApp webhook diagnostics (read-only except rejected POST probes)" -ForegroundColor Cyan
Write-Host "GET without hub parameters is EXPECTED to return 403 (controller behavior)."
$targets = @(@{ Label = "local"; Base = $LocalBaseUrl })
if ($PublicBaseUrl) { $targets += @{ Label = "public"; Base = $PublicBaseUrl } }

foreach ($target in $targets) {
    $label = $target.Label
    $base = $target.Base
    Probe "$label GET without challenge (expected 403)" $base "GET" $endpoint | Out-Null

    # Invalid signature is rejected before parsing or persisting the body.
    Probe "$label POST invalid signature (expected 403)" $base "POST" $endpoint '{"object":"diagnostic_probe"}' "sha256=invalid" | Out-Null

    if ($env:WHATSAPP_VERIFY_TOKEN) {
        $challenge = "webhook-diagnostic-123"
        $token = [uri]::EscapeDataString($env:WHATSAPP_VERIFY_TOKEN)
        $path = "$endpoint" + "?hub.mode=subscribe&hub.verify_token=$token&hub.challenge=$challenge"
        Probe "$label GET valid challenge (expected 200)" $base "GET" $path | Out-Null
    }
}

$results | Format-Table -AutoSize
$failed = $false
foreach ($result in $results) {
    $expected = if ($result.Check -like "*valid challenge*") { 200 } else { 403 }
    if ($result.Status -ne $expected) {
        Write-Warning "$($result.Check): expected $expected, received $($result.Status)"
        $failed = $true
    }
}
if ($failed) { exit 1 }
Write-Host "All probes returned the expected status. This does NOT prove Meta delivery or DB persistence." -ForegroundColor Green
