param(
  [string]$Root = "."
)

$ErrorActionPreference = "Stop"

$exclude = '\\(node_modules|target|dist|\.git|\.idea|build|out|coverage)\\'
$textExt = @(
  ".java",".kt",".xml",".yml",".yaml",".properties",".sql",
  ".md",".txt",".json",".js",".ts",".vue",".css",".scss",".html",
  ".sh",".ps1",".bat",".env",".gitignore",".gitattributes",".editorconfig"
)

$replacementChar = [string][char]0xFFFD
$jin = [string][char]0x951F
$jinWord = $jin + [string][char]0x65A4 + [string][char]0x62F7

$files = Get-ChildItem -Path $Root -Recurse -File | Where-Object {
  $_.FullName -notmatch $exclude -and (
    $textExt -contains $_.Extension.ToLowerInvariant() -or
    $_.Name -in @(".env",".gitignore",".gitattributes",".editorconfig")
  )
}

$bomFiles = New-Object System.Collections.Generic.List[string]
$mojibakeHits = New-Object System.Collections.Generic.List[string]

foreach ($f in $files) {
  $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
  if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
    $bomFiles.Add($f.FullName)
  }

  try {
    $text = Get-Content -Raw -Path $f.FullName
    if ($text.Contains($replacementChar) -or $text.Contains($jinWord)) {
      $mojibakeHits.Add($f.FullName)
    }
  } catch {
    # Skip files that cannot be read as text
  }
}

if ($bomFiles.Count -gt 0) {
  Write-Host "BOM files found:" -ForegroundColor Red
  $bomFiles | Sort-Object -Unique | ForEach-Object { Write-Host "  $_" }
}

if ($mojibakeHits.Count -gt 0) {
  Write-Host "Potential mojibake files found (high confidence):" -ForegroundColor Yellow
  $mojibakeHits | Sort-Object -Unique | ForEach-Object { Write-Host "  $_" }
}

if ($bomFiles.Count -eq 0 -and $mojibakeHits.Count -eq 0) {
  Write-Host "Encoding check passed (no BOM / no high-confidence mojibake)." -ForegroundColor Green
  exit 0
}

exit 1
