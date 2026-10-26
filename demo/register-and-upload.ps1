$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8080"
$email = "careerai-u54-demo@example.invalid"
$username = "careerai-u54-demo"
$pdf = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "anonymous-resume.pdf"
if (-not (Test-Path -LiteralPath $pdf)) { throw "Demo PDF not found: $pdf" }

$securePassword = Read-Host "Enter a local U54 demo password (8-64 characters)" -AsSecureString
$password = [System.Net.NetworkCredential]::new("", $securePassword).Password
if ($password.Length -lt 8 -or $password.Length -gt 64) {
    throw "Password must have 8-64 characters."
}
function SendJson($url, $value) {
    $json = $value | ConvertTo-Json -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Uri $url -Method Post -ContentType "application/json; charset=utf-8" -Body $bytes
}

try {
    try {
        $login = SendJson "$base/auth/login" @{ email = $email; password = $password }
    } catch {
        $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        if ($code -ne 401) { throw }
        $null = SendJson "$base/auth/register" @{ username = $username; email = $email; password = $password }
        $login = SendJson "$base/auth/login" @{ email = $email; password = $password }
    }
} finally {
    $password = $null
    $securePassword.Dispose()
}
$token = $login.data.token
if ([string]::IsNullOrWhiteSpace($token)) { throw "Login returned no token." }
$profile = Invoke-RestMethod -Uri "$base/user/profile" -Method Get -Headers @{ Authorization = "Bearer $token" }
$userId = $profile.data.id
if (-not $userId) { throw "Profile returned no user ID." }

$uploadText = & curl.exe -sS -f -H "Authorization: Bearer $token" -F "file=@$pdf;type=application/pdf" "$base/resumes/upload?userId=$userId"
if ($LASTEXITCODE -ne 0) { throw "PDF upload failed (curl exit code $LASTEXITCODE)." }
$upload = $uploadText | ConvertFrom-Json
if (-not $upload.id) { throw "Upload returned no resume ID." }
Write-Output "userId=$userId"
Write-Output "resumeId=$($upload.id)"
$token = $null
