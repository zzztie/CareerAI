param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$ResumeId,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$JobId
)
$ErrorActionPreference = "Stop"
$email = "careerai-u54-demo@example.invalid"
$securePassword = Read-Host "Enter the same U54 demo account password" -AsSecureString
$password = [System.Net.NetworkCredential]::new("", $securePassword).Password
try {
    $loginJson = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Uri "http://127.0.0.1:8080/auth/login" -Method Post -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($loginJson))
} finally {
    $password = $null
    $securePassword.Dispose()
}
$token = $login.data.token
if ([string]::IsNullOrWhiteSpace($token)) { throw "Login returned no token." }

$question = "请依据这份简历和岗位 JD，分别列出有证据的必要条件、当前简历没有证据的必要条件，以及加分项；引用 [R] 和 [J] 来源，不要把简历未写当成候选人不会。"
$requestJson = @{ question = $question } | ConvertTo-Json -Compress
$uri = "http://127.0.0.1:8081/ai/rag/compare/$ResumeId/$JobId"
$timer = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $result = Invoke-RestMethod -Uri $uri -Method Post -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($requestJson)) -TimeoutSec 240
} finally {
    $timer.Stop()
    $token = $null
}
if ([string]::IsNullOrWhiteSpace($result.answer)) { throw "Comparison returned no answer." }
$out = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "last-compare-answer.txt"
[System.IO.File]::WriteAllText($out, $result.answer, [System.Text.UTF8Encoding]::new($false))
Write-Output "elapsedSeconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1))"
Write-Output "answerFile=$out"
Write-Output $result.answer
