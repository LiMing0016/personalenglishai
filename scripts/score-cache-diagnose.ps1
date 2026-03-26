param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$DocumentId,
    [string]$EssayText,
    [string]$EssayFile,
    [int]$Runs = 10,
    [long]$UserId = 1,
    [string]$Nickname = "codex",
    [string]$Mode = "exam",
    [string]$StudyStage = "postgrad",
    [string]$TaskType = "task2",
    [string]$TopicTitle = "某高校劳动实践课学生主要收获调查",
    [string]$TaskPrompt = "describe and interpret the chart, and give your comments.",
    [int]$MinWords = 150,
    [int]$RecommendedMaxWords = 180,
    [int]$MaxScore = 15,
    [int]$PollSeconds = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $scriptDir = Split-Path -Parent $MyInvocation.ScriptName
    return Split-Path -Parent $scriptDir
}

function Read-JwtSecret {
    param([string]$EnvPath)
    if (-not (Test-Path -LiteralPath $EnvPath)) {
        throw "Cannot find backend env file at $EnvPath"
    }
    $line = Get-Content -LiteralPath $EnvPath | Where-Object { $_ -match '^JWT_SECRET=' } | Select-Object -First 1
    if (-not $line) {
        throw "JWT_SECRET not found in $EnvPath"
    }
    return ($line -replace '^JWT_SECRET=', '').Trim()
}

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    return [Convert]::ToBase64String($Bytes).TrimEnd('=') -replace '\+', '-' -replace '/', '_'
}

function New-AccessToken {
    param(
        [string]$Secret,
        [long]$Sub,
        [string]$Nickname,
        [int]$TokenVersion = 0
    )
    $header = @{ alg = "HS256"; typ = "JWT" } | ConvertTo-Json -Compress
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $payload = @{
        sub = "$Sub"
        nickname = $Nickname
        type = "access"
        tv = $TokenVersion
        iat = $now
        exp = $now + 7200
    } | ConvertTo-Json -Compress

    $headerEncoded = ConvertTo-Base64Url -Bytes ([Text.Encoding]::UTF8.GetBytes($header))
    $payloadEncoded = ConvertTo-Base64Url -Bytes ([Text.Encoding]::UTF8.GetBytes($payload))
    $unsigned = "$headerEncoded.$payloadEncoded"

    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $signature = ConvertTo-Base64Url -Bytes ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned)))
    } finally {
        $hmac.Dispose()
    }

    return "$unsigned.$signature"
}

function Resolve-EssayText {
    param(
        [string]$InlineText,
        [string]$FilePath
    )
    if ($InlineText) {
        return $InlineText.Trim()
    }
    if ($FilePath) {
        if (-not (Test-Path -LiteralPath $FilePath)) {
            throw "Essay file not found: $FilePath"
        }
        return (Get-Content -LiteralPath $FilePath -Raw).Trim()
    }
    throw "Provide -EssayText or -EssayFile"
}

function Submit-EvaluateTask {
    param(
        [string]$Endpoint,
        [hashtable]$Headers,
        [string]$Essay,
        [string]$DocumentId,
        [string]$Mode,
        [string]$StudyStage,
        [string]$TaskType,
        [string]$TopicTitle,
        [string]$TaskPrompt,
        [int]$MinWords,
        [int]$RecommendedMaxWords,
        [int]$MaxScore
    )
    $body = @{
        essay = $Essay
        mode = $Mode
        studyStage = $StudyStage
        taskType = $TaskType
        topicTitle = $TopicTitle
        taskPrompt = $TaskPrompt
        minWords = $MinWords
        recommendedMaxWords = $RecommendedMaxWords
        maxScore = $MaxScore
    }
    if ($DocumentId) {
        $body.documentId = $DocumentId
    }
    return Invoke-RestMethod -Method Post -Uri "$Endpoint/api/writing/evaluate/submit" -Headers $Headers -ContentType "application/json" -Body ($body | ConvertTo-Json -Depth 5)
}

function Wait-EvaluateTask {
    param(
        [string]$Endpoint,
        [hashtable]$Headers,
        [string]$RequestId,
        [int]$TimeoutSeconds
    )
    for ($attempt = 0; $attempt -lt $TimeoutSeconds; $attempt++) {
        $task = Invoke-RestMethod -Method Get -Uri "$Endpoint/api/writing/evaluate/tasks/$RequestId" -Headers $Headers
        if ($task.status -eq "succeeded" -or $task.status -eq "failed") {
            return $task
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for task $RequestId"
}

$repoRoot = Get-RepoRoot
$envPath = Join-Path $repoRoot "backend\.env"
$secret = Read-JwtSecret -EnvPath $envPath
$essay = Resolve-EssayText -InlineText $EssayText -FilePath $EssayFile
$token = New-AccessToken -Secret $secret -Sub $UserId -Nickname $Nickname
$headers = @{ Authorization = "Bearer $token" }

$rows = New-Object System.Collections.Generic.List[object]

for ($run = 1; $run -le $Runs; $run++) {
    $submitted = Submit-EvaluateTask `
        -Endpoint $BaseUrl `
        -Headers $headers `
        -Essay $essay `
        -DocumentId $DocumentId `
        -Mode $Mode `
        -StudyStage $StudyStage `
        -TaskType $TaskType `
        -TopicTitle $TopicTitle `
        -TaskPrompt $TaskPrompt `
        -MinWords $MinWords `
        -RecommendedMaxWords $RecommendedMaxWords `
        -MaxScore $MaxScore
    $result = Wait-EvaluateTask -Endpoint $BaseUrl -Headers $headers -RequestId $submitted.requestId -TimeoutSeconds $PollSeconds
    $scoreResult = $result.result
    $rows.Add([pscustomobject]@{
        run = $run
        task_request_id = $submitted.requestId
        status = $result.status
        input_tokens = $scoreResult.input_tokens
        cached_tokens = $scoreResult.cached_tokens
        cache_hit_rate = $scoreResult.cache_hit_rate
        prompt_cache_key = $scoreResult.prompt_cache_key
        instructions_hash = $scoreResult.instructions_hash
        cached_prefix_hash = $scoreResult.cached_prefix_hash
        essay_hash = $scoreResult.essay_hash
    }) | Out-Null
}

$displayRows = $rows | Select-Object `
    run,
    task_request_id,
    status,
    input_tokens,
    cached_tokens,
    cache_hit_rate,
    prompt_cache_key,
    @{ Name = "instructions_hash_12"; Expression = { if ($_.instructions_hash) { $_.instructions_hash.Substring(0, [Math]::Min(12, $_.instructions_hash.Length)) } else { $null } } },
    @{ Name = "cached_prefix_hash_12"; Expression = { if ($_.cached_prefix_hash) { $_.cached_prefix_hash.Substring(0, [Math]::Min(12, $_.cached_prefix_hash.Length)) } else { $null } } },
    @{ Name = "essay_hash_12"; Expression = { if ($_.essay_hash) { $_.essay_hash.Substring(0, [Math]::Min(12, $_.essay_hash.Length)) } else { $null } } }

($displayRows | Format-Table -AutoSize | Out-String -Width 4096).TrimEnd()
