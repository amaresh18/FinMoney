# Downloads full-color brand icons into app/src/main/res/drawable (PNG).
# Tries site apple-touch-icon / favicon first, then Google favicon, then Simple Icons (brand-colored vector).
$ErrorActionPreference = "Continue"
$outDir = Join-Path $PSScriptRoot "..\app\src\main\res\drawable"
$reportPath = Join-Path $PSScriptRoot "brand-icon-sync-report.txt"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Add-Type -AssemblyName System.Drawing

# fileName -> @{ domains = @(); slug = $null|$string; color = brand hex; tint = chip bg hex }
$manifest = @{
    "brand_amazon" = @{ domains = @("amazon.in","amazon.com"); slug = "amazon"; color = "#FF9900"; tint = "#FFF4E6" }
    "brand_flipkart" = @{ domains = @("flipkart.com"); slug = "flipkart"; color = "#2874F0"; tint = "#E8F2FF" }
    "brand_myntra" = @{ domains = @("myntra.com"); slug = $null; color = "#FF3F6C"; tint = "#FDE8F0" }
    "brand_ajio" = @{ domains = @("ajio.com"); slug = $null; color = "#2C2C2C"; tint = "#F3F4F6" }
    "brand_meesho" = @{ domains = @("meesho.com"); slug = $null; color = "#9F2089"; tint = "#FCE7F3" }
    "brand_nykaa" = @{ domains = @("nykaa.com"); slug = $null; color = "#FC2779"; tint = "#FDF2F8" }
    "brand_tatacliq" = @{ domains = @("tatacliq.com"); slug = $null; color = "#DA291C"; tint = "#FEE2E2" }
    "brand_croma" = @{ domains = @("croma.com"); slug = $null; color = "#0096D6"; tint = "#E0F2FE" }
    "brand_reliance_digital" = @{ domains = @("reliancedigital.in"); slug = $null; color = "#E42527"; tint = "#FEE2E2" }
    "brand_dmart" = @{ domains = @("dmartindia.com","dmart.in"); slug = $null; color = "#006B3F"; tint = "#DCFCE7" }
    "brand_bigbasket" = @{ domains = @("bigbasket.com"); slug = "bigbasket"; color = "#84C225"; tint = "#ECFCCB" }
    "brand_jiomart" = @{ domains = @("jiomart.com"); slug = $null; color = "#0A2885"; tint = "#E0E7FF" }
    "brand_zomato" = @{ domains = @("zomato.com"); slug = "zomato"; color = "#E23744"; tint = "#FEE2E2" }
    "brand_swiggy" = @{ domains = @("swiggy.com"); slug = "swiggy"; color = "#FC8019"; tint = "#FFEDD5" }
    "brand_zepto" = @{ domains = @("zepto.com","zepto.co.in"); slug = $null; color = "#7F2BFF"; tint = "#EDE9FE" }
    "brand_blinkit" = @{ domains = @("blinkit.com"); slug = $null; color = "#F8D000"; tint = "#FEF9C3" }
    "brand_instamart" = @{ domains = @("swiggy.com"); slug = $null; color = "#FC8019"; tint = "#FFEDD5" }
    "brand_dunzo" = @{ domains = @("dunzo.in"); slug = "dunzo"; color = "#00D290"; tint = "#D1FAE5" }
    "brand_dominos" = @{ domains = @("dominos.co.in","dominos.com"); slug = $null; color = "#006491"; tint = "#E0F2FE" }
    "brand_mcdonalds" = @{ domains = @("mcdonalds.com","mcdonalds.co.in"); slug = "mcdonalds"; color = "#FFC72C"; tint = "#FEF9C3" }
    "brand_kfc" = @{ domains = @("kfc.com","online.kfc.co.in"); slug = "kfc"; color = "#E4002B"; tint = "#FEE2E2" }
    "brand_starbucks" = @{ domains = @("starbucks.com","starbucks.in"); slug = "starbucks"; color = "#00704A"; tint = "#D1FAE5" }
    "brand_restaurant" = @{ domains = @("zomato.com"); slug = $null; color = "#E23744"; tint = "#FEE2E2" }
    "brand_uber" = @{ domains = @("uber.com"); slug = "uber"; color = "#000000"; tint = "#F3F4F6" }
    "brand_ola" = @{ domains = @("olacabs.com","book.olacabs.com"); slug = $null; color = "#2D9E4D"; tint = "#DCFCE7" }
    "brand_rapido" = @{ domains = @("rapido.bike"); slug = $null; color = "#FFCA28"; tint = "#FEF9C3" }
    "brand_namma_yatri" = @{ domains = @("nammayatri.com","www.nammayatri.com"); slug = $null; color = "#FCD34D"; tint = "#FEF9C3" }
    "brand_redbus" = @{ domains = @("redbus.in"); slug = $null; color = "#D84E55"; tint = "#FEE2E2" }
    "brand_irctc" = @{ domains = @("irctc.co.in","indianrail.gov.in"); slug = $null; color = "#213D98"; tint = "#E0E7FF" }
    "brand_makemytrip" = @{ domains = @("makemytrip.com"); slug = $null; color = "#008CFF"; tint = "#E0F2FE" }
    "brand_goibibo" = @{ domains = @("goibibo.com"); slug = $null; color = "#F34F4F"; tint = "#FEE2E2" }
    "brand_indigo" = @{ domains = @("goindigo.in"); slug = "indigo"; color = "#001B94"; tint = "#E0E7FF" }
    "brand_air_india" = @{ domains = @("airindia.com"); slug = "airindia"; color = "#DA0E29"; tint = "#FEE2E2" }
    "brand_metro" = @{ domains = @("delhimetrorail.com"); slug = $null; color = "#2563EB"; tint = "#EFF6FF" }
    "brand_iocl" = @{ domains = @("iocl.com"); slug = $null; color = "#F37021"; tint = "#FFEDD5" }
    "brand_hpcl" = @{ domains = @("hindustanpetroleum.com","hpcl.co.in"); slug = $null; color = "#ED1C24"; tint = "#FEE2E2" }
    "brand_bpcl" = @{ domains = @("bharatpetroleum.in"); slug = $null; color = "#007A33"; tint = "#DCFCE7" }
    "brand_shell" = @{ domains = @("shell.com","shell.in"); slug = "shell"; color = "#FFD500"; tint = "#FEF9C3" }
    "brand_netflix" = @{ domains = @("netflix.com"); slug = "netflix"; color = "#E50914"; tint = "#FEE2E2" }
    "brand_prime_video" = @{ domains = @("primevideo.com"); slug = "amazonprime"; color = "#00A8E1"; tint = "#E0F2FE" }
    "brand_hotstar" = @{ domains = @("hotstar.com"); slug = $null; color = "#1F80E0"; tint = "#E0F2FE" }
    "brand_sonyliv" = @{ domains = @("sonyliv.com"); slug = $null; color = "#FFC600"; tint = "#FEF9C3" }
    "brand_spotify" = @{ domains = @("spotify.com"); slug = "spotify"; color = "#1DB954"; tint = "#DCFCE7" }
    "brand_youtube" = @{ domains = @("youtube.com"); slug = "youtube"; color = "#FF0000"; tint = "#FEE2E2" }
    "brand_jio" = @{ domains = @("jio.com"); slug = $null; color = "#0A2885"; tint = "#E0E7FF" }
    "brand_airtel" = @{ domains = @("airtel.in"); slug = "airtel"; color = "#E40000"; tint = "#FEE2E2" }
    "brand_vi" = @{ domains = @("myvi.in"); slug = "vodafone"; color = "#EE2D24"; tint = "#FEE2E2" }
    "brand_bsnl" = @{ domains = @("bsnl.co.in","bsnl.in"); slug = $null; color = "#254AA5"; tint = "#E0E7FF" }
    "brand_phonepe" = @{ domains = @("phonepe.com"); slug = "phonepe"; color = "#5F259F"; tint = "#EDE9FE" }
    "brand_paytm" = @{ domains = @("paytm.com"); slug = "paytm"; color = "#00BAF2"; tint = "#E0F2FE" }
    "brand_googlepay" = @{ domains = @("pay.google.com"); slug = "googlepay"; color = "#4285F4"; tint = "#E8F0FE" }
    "brand_cred" = @{ domains = @("cred.club"); slug = $null; color = "#1B1B1B"; tint = "#F3F4F6" }
    "brand_pharmeasy" = @{ domains = @("pharmeasy.in"); slug = $null; color = "#10847E"; tint = "#CCFBF1" }
    "brand_tata_1mg" = @{ domains = @("1mg.com"); slug = $null; color = "#FF6F61"; tint = "#FEE2E2" }
    "brand_apollo" = @{ domains = @("apollo247.com"); slug = $null; color = "#007F6E"; tint = "#CCFBF1" }
    "brand_byjus" = @{ domains = @("byjus.com"); slug = "byjus"; color = "#7C3AED"; tint = "#EDE9FE" }
    "brand_unacademy" = @{ domains = @("unacademy.com"); slug = "unacademy"; color = "#08BD80"; tint = "#D1FAE5" }
}

function Save-BytesAsPng {
    param([byte[]]$bytes, [string]$pngPath)
    if ($bytes.Length -lt 120) { return $false }
    $ms = New-Object System.IO.MemoryStream(,$bytes)
    try {
        $img = [System.Drawing.Image]::FromStream($ms)
        $img.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $img.Dispose()
        return (Test-Path $pngPath) -and ((Get-Item $pngPath).Length -gt 200)
    } catch {
        return $false
    } finally {
        $ms.Dispose()
    }
}

function Try-DownloadUrl {
    param([string]$url, [string]$pngPath)
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 8 -MaximumRedirection 4
        if ($resp.StatusCode -lt 200 -or $resp.StatusCode -ge 400) { return $false }
        return Save-BytesAsPng -bytes $resp.Content -pngPath $pngPath
    } catch { return $false }
}

function Download-BrandPng {
    param([string]$fileName, [string[]]$domains)
    $pngPath = Join-Path $outDir "$fileName.png"
    $xmlPath = Join-Path $outDir "$fileName.xml"
    if (Test-Path $xmlPath) { Remove-Item $xmlPath -Force }

    foreach ($domain in $domains) {
        $hosts = @($domain)
        if (-not $domain.StartsWith("www.")) { $hosts += "www.$domain" }
        foreach ($hostName in $hosts) {
            $urls = @(
                "https://$hostName/apple-touch-icon.png",
                "https://$hostName/apple-touch-icon-precomposed.png",
                "https://$hostName/favicon.ico",
                "https://www.google.com/s2/favicons?domain=$hostName&sz=128"
            )
            foreach ($url in $urls) {
                if (Try-DownloadUrl -url $url -pngPath $pngPath) {
                    return @{ ok = $true; source = $url; size = (Get-Item $pngPath).Length }
                }
            }
        }
    }
    return @{ ok = $false; source = ""; size = 0 }
}

function Write-BrandColoredVector {
    param([string]$slug, [string]$fileName, [string]$brandColor)
    $pngPath = Join-Path $outDir "$fileName.png"
    $xmlPath = Join-Path $outDir "$fileName.xml"
    if (Test-Path $pngPath) { Remove-Item $pngPath -Force }
    try {
        $svg = (Invoke-WebRequest -Uri "https://cdn.jsdelivr.net/npm/simple-icons@11.14.0/icons/$slug.svg" -UseBasicParsing -TimeoutSec 20).Content
    } catch { return $false }
    if ($svg -notmatch 'viewBox="([^"]+)"') { return $false }
    $parts = $Matches[1].Split(" ")
    $vw = if ($parts.Length -ge 4) { $parts[2] } else { "24" }
    $vh = if ($parts.Length -ge 4) { $parts[3] } else { "24" }
    $pathMatches = [regex]::Matches($svg, '<path[^>]*d="([^"]+)"[^>]*>')
    if ($pathMatches.Count -eq 0) { return $false }
    $pathXml = ""
    foreach ($m in $pathMatches) {
        $d = $m.Groups[1].Value
        $pathXml += "    <path android:fillColor=`"$brandColor`" android:pathData=`"$d`"/>`n"
    }
    $xml = @"
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="$vw"
    android:viewportHeight="$vh">
$pathXml</vector>
"@
    [System.IO.File]::WriteAllText($xmlPath, $xml.TrimEnd())
    return $true
}

$lines = @()
$fail = @()
$ok = 0
foreach ($entry in $manifest.GetEnumerator() | Sort-Object Name) {
    $file = $entry.Key
    $meta = $entry.Value
    $result = Download-BrandPng -fileName $file -domains $meta.domains
    if (-not $result.ok -and $meta.slug) {
        if (Write-BrandColoredVector -slug $meta.slug -fileName $file -brandColor $meta.color) {
            $xmlPath = Join-Path $outDir "$file.xml"
            $result = @{ ok = $true; source = "simple-icons:$($meta.slug)"; size = (Get-Item $xmlPath).Length }
        }
    }
    if (-not $result.ok) {
        $xmlPath = Join-Path $outDir "$file.xml"
        $pngPath = Join-Path $outDir "$file.png"
        if (Test-Path $pngPath) { Remove-Item $pngPath -Force }
        $c = $meta.color
        $xml = @"
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="$c" android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z"/>
</vector>
"@
        [System.IO.File]::WriteAllText($xmlPath, $xml)
        $result = @{ ok = $true; source = "fallback-dot"; size = (Get-Item $xmlPath).Length }
    }
    if ($result.ok) {
        $ok++
        $lines += "OK   $file  $($result.size) bytes  $($result.source)"
    } else {
        $fail += $file
        $lines += "FAIL $file"
    }
}

$lines += ""
$lines += "Success: $ok / $($manifest.Count)"
if ($fail.Count -gt 0) {
    $lines += "Failed: $($fail -join ', ')"
}
$lines | Set-Content -Path $reportPath -Encoding UTF8
$lines | ForEach-Object { Write-Host $_ }

if ($fail.Count -gt 0) { exit 1 }
exit 0
