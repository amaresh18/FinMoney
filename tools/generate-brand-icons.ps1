# Downloads Simple Icons (MIT) SVGs and writes Android vector drawables.
$ErrorActionPreference = "Stop"
$outDir = Join-Path $PSScriptRoot "..\app\src\main\res\drawable"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$brands = @{
    "brand_amazon" = @{ slug = "amazon"; color = "#FF9900" }
    "brand_flipkart" = @{ slug = "flipkart"; color = "#2874F0" }
    "brand_myntra" = @{ slug = "myntra"; color = "#FF3F6C" }
    "brand_zomato" = @{ slug = "zomato"; color = "#E23744" }
    "brand_swiggy" = @{ slug = "swiggy"; color = "#FC8019" }
    "brand_uber" = @{ slug = "uber"; color = "#000000" }
    "brand_ola" = @{ slug = "ola"; color = "#2D9E4D" }
    "brand_rapido" = @{ slug = "rapido"; color = "#FFCA28" }
    "brand_netflix" = @{ slug = "netflix"; color = "#E50914" }
    "brand_spotify" = @{ slug = "spotify"; color = "#1DB954" }
    "brand_youtube" = @{ slug = "youtube"; color = "#FF0000" }
    "brand_phonepe" = @{ slug = "phonepe"; color = "#5F259F" }
    "brand_paytm" = @{ slug = "paytm"; color = "#00BAF2" }
    "brand_googlepay" = @{ slug = "googlepay"; color = "#4285F4" }
    "brand_cred" = @{ slug = "cred"; color = "#1B1B1B" }
    "brand_bigbasket" = @{ slug = "bigbasket"; color = "#84C225" }
    "brand_blinkit" = @{ slug = "blinkit"; color = "#F8D000" }
    "brand_zepto" = @{ slug = "zepto"; color = "#7F2BFF" }
    "brand_dominos" = @{ slug = "dominos"; color = "#006491" }
    "brand_mcdonalds" = @{ slug = "mcdonalds"; color = "#FFC72C" }
    "brand_kfc" = @{ slug = "kfc"; color = "#E4002B" }
    "brand_starbucks" = @{ slug = "starbucks"; color = "#00704A" }
    "brand_redbus" = @{ slug = "redbus"; color = "#D84E55" }
    "brand_makemytrip" = @{ slug = "makemytrip"; color = "#008CFF" }
    "brand_goibibo" = @{ slug = "goibibo"; color = "#F34F4F" }
    "brand_airindia" = @{ slug = "airindia"; color = "#DA0E29" }
    "brand_airtel" = @{ slug = "airtel"; color = "#E40000" }
    "brand_jio" = @{ slug = "jio"; color = "#0A2885" }
    "brand_meesho" = @{ slug = "meesho"; color = "#9F2089" }
    "brand_nykaa" = @{ slug = "nykaa"; color = "#FC2779" }
    "brand_dunzo" = @{ slug = "dunzo"; color = "#00D290" }
    "brand_pharmeasy" = @{ slug = "pharmeasy"; color = "#10847E" }
    "brand_byjus" = @{ slug = "byjus"; color = "#7C3AED" }
    "brand_unacademy" = @{ slug = "unacademy"; color = "#08BD80" }
    "brand_hotstar" = @{ slug = "hotstar"; color = "#1F80E0" }
    "brand_shell" = @{ slug = "shell"; color = "#FFD500" }
    "brand_apollo" = @{ slug = "apollo247"; color = "#007F6E" }
    "brand_tatacliq" = @{ slug = "tatacliq"; color = "#DA291C" }
    "brand_vodafone" = @{ slug = "vodafone"; color = "#EE2D24" }
    "brand_amazonprime" = @{ slug = "amazonprime"; color = "#00A8E1" }
    "brand_irctc" = @{ slug = "irctc"; color = "#213D98" }
    "brand_indigo" = @{ slug = "indigo"; color = "#001B94" }
    "brand_ajio" = @{ slug = "ajio"; color = "#2C2C2C" }
    "brand_dmarts" = @{ slug = "dmart"; color = "#006B3F" }
}

function Convert-SvgToAndroidVector {
    param([string]$svg, [string]$fillColor, [string]$fileName)
    $viewBox = "0 0 24 24"
    if ($svg -match 'viewBox="([^"]+)"') { $viewBox = $Matches[1] }
    $parts = $viewBox.Split(" ")
    $vw = if ($parts.Length -ge 4) { $parts[2] } else { "24" }
    $vh = if ($parts.Length -ge 4) { $parts[3] } else { "24" }

    $pathMatches = [regex]::Matches($svg, '<path[^>]*d="([^"]+)"[^>]*>')
    if ($pathMatches.Count -eq 0) {
        return $false
    }

    $pathXml = ""
    foreach ($m in $pathMatches) {
        $d = $m.Groups[1].Value
        $pathXml += "    <path android:fillColor=`"$fillColor`" android:pathData=`"$d`"/>`n"
    }

    $xml = @"
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="$vw"
    android:viewportHeight="$vh">
$pathXml</vector>
"@
    $path = Join-Path $outDir "$fileName.xml"
    [System.IO.File]::WriteAllText($path, $xml.TrimEnd())
    return $true
}

$ok = 0
$fail = @()
foreach ($entry in $brands.GetEnumerator()) {
    $file = $entry.Key
    $slug = $entry.Value.slug
    $color = $entry.Value.color
    $url = "https://cdn.jsdelivr.net/npm/simple-icons@11.14.0/icons/$slug.svg"
    try {
        $svg = (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 20).Content
        if (Convert-SvgToAndroidVector -svg $svg -fillColor $color -fileName $file) {
            $ok++
            Write-Host "OK $file ($slug)"
        } else {
            $fail += $file
        }
    } catch {
        $fail += $file
        Write-Host "FAIL $file ($slug): $($_.Exception.Message)"
    }
}
Write-Host "Generated $ok icons. Failed: $($fail.Count)"
if ($fail.Count -gt 0) { Write-Host ($fail -join ", ") }
