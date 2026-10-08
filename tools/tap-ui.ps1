# Taps the centre of the first node whose text or content description matches, and
# fails loudly when nothing matches instead of tapping a guessed coordinate.
#
#   pwsh -File tools/tap-ui.ps1 -Match "设置"
#
# Exit codes: 0 tapped, 2 nothing matched, 3 unparsable bounds.
param(
    [Parameter(Mandatory = $true)][string]$Match,
    [switch]$Contains
)

$adb = 'D:\Sdk\platform-tools\adb.exe'
$root = Split-Path -Parent $PSScriptRoot

# adb writes its transfer summary to stderr. PowerShell turns native stderr into
# an error record, and under a caller's ErrorActionPreference that aborts the
# script on an entirely successful pull, so the preference is relaxed just here
# and the exit code is what actually decides whether the pull worked.
$ErrorActionPreference = 'Continue'
& $adb shell uiautomator dump /sdcard/ui.xml *> $null
$local = Join-Path $root 'tools\shots\ui.xml'
& $adb pull /sdcard/ui.xml $local *> $null
$ErrorActionPreference = 'Stop'

if (-not (Test-Path $local)) {
    Write-Error "could not read the UI dump from the device"
    exit 3
}

$xml = [xml][System.IO.File]::ReadAllText($local)
$found = $null

function Find-Node($node) {
    if ($script:found) { return }
    $text = [string]$node.text
    $desc = [string]$node.GetAttribute('content-desc')
    $hit = $false
    if ($Contains) {
        $hit = ($text -like "*$Match*") -or ($desc -like "*$Match*")
    } else {
        $hit = ($text -eq $Match) -or ($desc -eq $Match)
    }
    if ($hit) { $script:found = $node; return }
    foreach ($child in $node.node) { Find-Node $child }
}

Find-Node $xml.hierarchy.node

if (-not $found) {
    Write-Error "no node matched '$Match'"
    exit 2
}

# "bounds" looks like [left,top][right,bottom].
if ($found.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
    Write-Error "unparsable bounds '$($found.bounds)'"
    exit 3
}
$left = [int]$Matches[1]; $top = [int]$Matches[2]
$right = [int]$Matches[3]; $bottom = [int]$Matches[4]
$x = [int](($left + $right) / 2)
$y = [int](($top + $bottom) / 2)

Write-Output ("tap '" + $Match + "' at (" + $x + "," + $y + ")  bounds=" + $found.bounds)
& $adb shell input tap $x $y
