param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleUserHome = $env:GRADLE_USER_HOME,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$socketTemp = Join-Path $projectRoot '.gradle-local/socket-temp'
$gradle = Join-Path $projectRoot 'gradlew.bat'
$savedEnvironment = @{}
foreach ($name in @('JAVA_HOME', 'JAVA_TOOL_OPTIONS', 'GRADLE_USER_HOME')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

Push-Location -LiteralPath $projectRoot
try {
    if ($JavaHome) {
        if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
            throw "Java executable not found under $JavaHome"
        }
        $env:JAVA_HOME = $JavaHome
    }
    New-Item -ItemType Directory -Force -Path $socketTemp | Out-Null
    # Windows Java selectors use Unix sockets in TEMP. A sandbox TEMP path can
    # make connect fail even when TCP loopback works. Limit this workaround to
    # this invocation; do not alter global Java or Gradle settings.
    if ($env:JAVA_TOOL_OPTIONS -notmatch 'jdk\.net\.unixdomain\.tmpdir=') {
        $option = '"-Djdk.net.unixdomain.tmpdir=' + $socketTemp + '"'
        $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' ' + $option).Trim()
    }
    if ($GradleUserHome) {
        $env:GRADLE_USER_HOME = [System.IO.Path]::GetFullPath($GradleUserHome)
    }
    $arguments = @('-Dorg.gradle.vfs.watch=false', '--rerun-tasks')
    if ($Offline) {
        $arguments += '--offline'
    }
    $arguments += @('testDebugUnitTest', 'assembleDebug', 'lintDebug')
    & $gradle @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Project verification failed (Gradle exit $LASTEXITCODE)."
    }
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        if ($null -eq $savedEnvironment[$name]) {
            # PowerShell can distinguish an absent variable from an empty one.
            Remove-Item -LiteralPath ('Env:\' + $name) -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
        }
    }
    Pop-Location
}
