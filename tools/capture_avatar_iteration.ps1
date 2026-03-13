param(
    [int]$Round = 1,
    [string]$PackageName = "com.example.newstart",
    [string]$LaunchActivity = ".MainActivity",
    [string]$OutputRoot = "d:/newstart/artifacts/avatar-iteration",
    [string]$DeviceId = "",
    [int]$SettleMs = 1400,
    [double]$RelaxEntryXRatio = 0.50,
    [double]$RelaxEntryYRatio = 0.33
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    if ([string]::IsNullOrWhiteSpace($DeviceId)) {
        & adb @Args
    } else {
        & adb -s $DeviceId @Args
    }
}

function Dump-UiXml([string]$LocalPath) {
    Invoke-Adb shell uiautomator dump /sdcard/ui.xml | Out-Null
    if ([string]::IsNullOrWhiteSpace($DeviceId)) {
        & adb pull /sdcard/ui.xml $LocalPath | Out-Null
    } else {
        & adb -s $DeviceId pull /sdcard/ui.xml $LocalPath | Out-Null
    }
}

function Get-NodeCenterFromUiXml {
    param(
        [string]$XmlPath,
        [string]$ResourceId
    )
    $xml = Get-Content -Raw $XmlPath
    $escapedId = [regex]::Escape($ResourceId)
    $pattern = "resource-id=""$escapedId"".*?bounds=""\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""
    $match = [regex]::Match($xml, $pattern)
    if (-not $match.Success) {
        return $null
    }
    $left = [int]$match.Groups[1].Value
    $top = [int]$match.Groups[2].Value
    $right = [int]$match.Groups[3].Value
    $bottom = [int]$match.Groups[4].Value
    return @{
        X = [int](($left + $right) / 2)
        Y = [int](($top + $bottom) / 2)
        Bounds = "[$left,$top][$right,$bottom]"
    }
}

function Tap-ResourceId {
    param(
        [string]$ResourceId,
        [string]$UiXmlPath
    )
    $center = Get-NodeCenterFromUiXml -XmlPath $UiXmlPath -ResourceId $ResourceId
    if ($null -eq $center) {
        throw "Resource not found in UI xml: $ResourceId"
    }
    Invoke-Adb shell input tap $($center.X) $($center.Y) | Out-Null
    Write-Host "[TAP] $ResourceId @ $($center.Bounds)"
}

function Capture-Page {
    param(
        [string]$Name,
        [string]$RoundDir
    )
    Start-Sleep -Milliseconds $SettleMs
    $local = Join-Path $RoundDir "$Name.png"
    if ([string]::IsNullOrWhiteSpace($DeviceId)) {
        cmd /c "adb exec-out screencap -p > `"$local`""
    } else {
        cmd /c "adb -s $DeviceId exec-out screencap -p > `"$local`""
    }
    if (-not (Test-Path -LiteralPath $local)) {
        throw "Screenshot failed: $local"
    }
    Write-Host "[CAPTURE] $Name -> $local"
}

$roundDir = Join-Path $OutputRoot ("round_{0:d2}" -f $Round)
$null = New-Item -ItemType Directory -Force -Path $roundDir

$devicesOutput = (Invoke-Adb devices | Out-String)
$onlineDevice = $false
foreach ($line in ($devicesOutput -split "`r?`n")) {
    if ($line -match "^\S+\s+device(\s|$)") {
        $onlineDevice = $true
        break
    }
}
if (-not $onlineDevice) {
    throw "No online adb device found. Run 'adb devices' and reconnect device."
}

Invoke-Adb shell am start -n "$PackageName/$LaunchActivity" | Out-Null
Start-Sleep -Milliseconds ($SettleMs + 600)

# Bottom navigation page order: home, doctor, trend, device, profile.
$uiXmlPath = Join-Path $roundDir "ui_round_dump.xml"
$pages = @(
    @{ Name = "home"; ResourceId = "${PackageName}:id/navigation_home" },
    @{ Name = "doctor"; ResourceId = "${PackageName}:id/navigation_doctor" },
    @{ Name = "trend"; ResourceId = "${PackageName}:id/navigation_trend" },
    @{ Name = "device"; ResourceId = "${PackageName}:id/navigation_device" },
    @{ Name = "profile"; ResourceId = "${PackageName}:id/navigation_profile" }
)

foreach ($page in $pages) {
    Dump-UiXml -LocalPath $uiXmlPath
    Tap-ResourceId -ResourceId $page.ResourceId -UiXmlPath $uiXmlPath
    Capture-Page -Name $page.Name -RoundDir $roundDir
}

# Open relax hub from home via quick action button.
Dump-UiXml -LocalPath $uiXmlPath
Tap-ResourceId -ResourceId "${PackageName}:id/navigation_home" -UiXmlPath $uiXmlPath
Start-Sleep -Milliseconds $SettleMs
Dump-UiXml -LocalPath $uiXmlPath
$relaxResource = "${PackageName}:id/btn_relax_center"
$relaxNode = Get-NodeCenterFromUiXml -XmlPath $uiXmlPath -ResourceId $relaxResource
if ($null -eq $relaxNode) {
    Write-Host "[WARN] btn_relax_center not found, fallback to ratio tap."
    # Keep an emergency fallback if UI id changes.
    $raw = Invoke-Adb shell wm size | Out-String
    if ($raw -match "Physical size:\s*(\d+)x(\d+)") {
        $w = [int]$Matches[1]
        $h = [int]$Matches[2]
        $x = [int]([math]::Round($w * $RelaxEntryXRatio))
        $y = [int]([math]::Round($h * $RelaxEntryYRatio))
        Invoke-Adb shell input tap $x $y | Out-Null
    }
} else {
    Tap-ResourceId -ResourceId $relaxResource -UiXmlPath $uiXmlPath
}
Capture-Page -Name "relax_hub" -RoundDir $roundDir

Write-Host "[DONE] Avatar iteration captures saved at: $roundDir" -ForegroundColor Green
