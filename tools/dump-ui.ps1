# Prints every node of the current screen that carries text or a content
# description, with its bounds, so tap coordinates come from the hierarchy rather
# than from an estimate.
#
#   pwsh -File tools/dump-ui.ps1            # print the tree
#   pwsh -File tools/dump-ui.ps1 -Save x.xml
param([string]$Save = "")

$adb = 'D:\Sdk\platform-tools\adb.exe'
$root = Split-Path -Parent $PSScriptRoot

# See tap-ui.ps1: adb's review of its own progress goes to stderr, which
# PowerShell would otherwise treat as a failure.
$ErrorActionPreference = 'Continue'
& $adb shell uiautomator dump /sdcard/ui.xml *> $null
$local = Join-Path $root 'tools\shots\ui.xml'
& $adb pull /sdcard/ui.xml $local *> $null
$ErrorActionPreference = 'Stop'
if ($Save) { Copy-Item $local (Join-Path $root $Save) -Force }

$raw = [System.IO.File]::ReadAllText($local)
$xml = [xml]$raw

function Show-Node($node, $depth) {
    if ($depth -gt 10) { return }
    $text = [string]$node.text
    $desc = [string]$node.GetAttribute('content-desc')
    $cls = [string]$node.class
    $short = ($cls -split '\.')[-1]
    if ($text -or $desc) {
        $line = ('  ' * $depth) + $node.bounds + '  ' + $short
        if ($desc) { $line += '  desc="' + $desc + '"' }
        if ($text) { $line += '  text="' + $text + '"' }
        Write-Output $line
    }
    foreach ($child in $node.node) { Show-Node $child ($depth + 1) }
}

Show-Node $xml.hierarchy.node 0
