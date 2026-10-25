$ErrorActionPreference = "Continue"
$outDir = Join-Path $PSScriptRoot "..\app\src\main\res\drawable"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# DEPRECATED: use sync-color-brand-icons.ps1 (Google color favicons, 128px).
# Real site favicons / logos via icon.horse (bundled for offline use in the app).
$brands = @{
    "brand_amazon" = "amazon.in"
    "brand_flipkart" = "flipkart.com"
    "brand_myntra" = "myntra.com"
    "brand_ajio" = "ajio.com"
    "brand_meesho" = "meesho.com"
    "brand_nykaa" = "nykaa.com"
    "brand_tatacliq" = "tatacliq.com"
    "brand_croma" = "croma.com"
    "brand_reliance_digital" = "reliancedigital.in"
    "brand_dmart" = "dmartindia.com"
    "brand_bigbasket" = "bigbasket.com"
    "brand_jiomart" = "jiomart.com"
    "brand_zomato" = "zomato.com"
    "brand_swiggy" = "swiggy.com"
    "brand_zepto" = "zepto.co.in"
    "brand_blinkit" = "blinkit.com"
    "brand_instamart" = "swiggy.com"
    "brand_dunzo" = "dunzo.com"
    "brand_dominos" = "dominos.co.in"
    "brand_mcdonalds" = "mcdonalds.co.in"
    "brand_kfc" = "online.kfc.co.in"
    "brand_starbucks" = "starbucks.in"
    "brand_restaurant" = "zomato.com"
    "brand_uber" = "uber.com"
    "brand_ola" = "olacabs.com"
    "brand_rapido" = "rapido.bike"
    "brand_namma_yatri" = "nammayatri.com"
    "brand_redbus" = "redbus.in"
    "brand_irctc" = "irctc.co.in"
    "brand_makemytrip" = "makemytrip.com"
    "brand_goibibo" = "goibibo.com"
    "brand_indigo" = "goindigo.in"
    "brand_air_india" = "airindia.com"
    "brand_metro" = "delhimetrorail.com"
    "brand_iocl" = "iocl.com"
    "brand_hpcl" = "hindustanpetroleum.com"
    "brand_bpcl" = "bharatpetroleum.in"
    "brand_shell" = "shell.in"
    "brand_netflix" = "netflix.com"
    "brand_prime_video" = "primevideo.com"
    "brand_hotstar" = "hotstar.com"
    "brand_sonyliv" = "sonyliv.com"
    "brand_spotify" = "spotify.com"
    "brand_youtube" = "youtube.com"
    "brand_jio" = "jio.com"
    "brand_airtel" = "airtel.in"
    "brand_vi" = "myvi.in"
    "brand_bsnl" = "bsnl.co.in"
    "brand_phonepe" = "phonepe.com"
    "brand_paytm" = "paytm.com"
    "brand_googlepay" = "pay.google.com"
    "brand_cred" = "cred.club"
    "brand_pharmeasy" = "pharmeasy.in"
    "brand_tata_1mg" = "1mg.com"
    "brand_apollo" = "apollo247.com"
    "brand_byjus" = "byjus.com"
    "brand_unacademy" = "unacademy.com"
}

foreach ($entry in $brands.GetEnumerator()) {
    $name = $entry.Key
    $domain = $entry.Value
    $pngPath = Join-Path $outDir "$name.png"
    $xmlPath = Join-Path $outDir "$name.xml"
    if (Test-Path $xmlPath) { Remove-Item $xmlPath -Force }
    $url = "https://icon.horse/icon/$domain"
    try {
        Invoke-WebRequest -Uri $url -OutFile $pngPath -UseBasicParsing -TimeoutSec 25
        Write-Host "OK $name"
    } catch {
        Write-Host "FAIL $name ($domain)"
    }
}
