# Ensures every brand_* resource referenced in ExpenseTagBrands.kt exists and is not tiny.
$ErrorActionPreference = "Stop"
$root = Join-Path $PSScriptRoot ".."
$kotlin = Join-Path $root "app\src\main\java\com\example\ui\components\ExpenseTagBrands.kt"
$drawable = Join-Path $root "app\src\main\res\drawable"
$content = Get-Content $kotlin -Raw
$matches = [regex]::Matches($content, 'R\.drawable\.(brand_[a-z0-9_]+)')
$names = $matches | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
$minBytes = 400
$missing = @()
$tiny = @()
foreach ($name in $names) {
    $png = Join-Path $drawable "$name.png"
    $xml = Join-Path $drawable "$name.xml"
    if (Test-Path $png) {
        $len = (Get-Item $png).Length
        if ($len -lt $minBytes) { $tiny += "$name ($len)" }
    } elseif (Test-Path $xml) {
        $len = (Get-Item $xml).Length
        if ($len -lt 200) { $tiny += "$name.xml ($len)" }
    } else {
        $missing += $name
    }
}
if ($missing.Count -gt 0) {
    Write-Host "MISSING: $($missing -join ', ')"
    exit 1
}
if ($tiny.Count -gt 0) {
    Write-Host "TOO SMALL: $($tiny -join ', ')"
    exit 1
}
Write-Host "All $($names.Count) brand icons present and above size threshold."
exit 0
