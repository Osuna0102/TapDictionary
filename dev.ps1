#requires -version 5.1
# GodTap Dictionary - Development Mode (PowerShell)

param(
    [switch]$NoHotReload
)

$GREEN = [ConsoleColor]::Green
$BLUE = [ConsoleColor]::Blue
$YELLOW = [ConsoleColor]::Yellow
$RED = [ConsoleColor]::Red
$NC = [ConsoleColor]::White

function Write-Colored {
    param([string]$Text, [ConsoleColor]$Color = $NC)
    Write-Host $Text -ForegroundColor $Color
}

Write-Colored "GodTap Dictionary - Development Mode" $BLUE
Write-Host ""

# Check device
$devices = (& adb devices 2>$null) -join "`n"
if ($devices -notmatch "\s+device$") {
    Write-Colored "No device connected. Please connect a device." $YELLOW
    exit 1
}

Write-Colored "Device connected" $GREEN
Write-Host ""

# Initial build and deploy
Write-Colored "Building and installing..." $BLUE
try {
    & ./gradlew assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }

    $installResult = (& adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1) -join " "
    if ($installResult -notmatch "Success") { throw "Install failed" }

    & adb shell am start -n com.godtap.dictionary/.MainActivity
    Write-Colored "App launched!" $GREEN
    Write-Host ""
} catch {
    Write-Colored "Initial build/install failed: $_" $RED
    exit 1
}

if ($NoHotReload) {
    Write-Colored "Hot-reload disabled. Exiting." $YELLOW
    exit 0
}

# Check for fswatch
$fswatch = Get-Command fswatch -ErrorAction SilentlyContinue
if (-not $fswatch) {
    Write-Colored "fswatch not found. Install with: scoop install fswatch" $YELLOW
    Write-Colored "Falling back to manual rebuild mode..." $YELLOW
    Write-Colored "Press Ctrl+C to exit, then re-run when files change." $YELLOW
    Read-Host
    exit 0
}

Write-Colored "Starting hot-reload (fswatch)..." $BLUE
Write-Colored "Watching for file changes..." $BLUE
Write-Host ""

# Create a file watcher
$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = $PSScriptRoot
$watcher.IncludeSubdirectories = $true
$watcher.Filter = "*.*"
$watcher.EnableRaisingEvents = $true

$lastBuild = Get-Date

$action = {
    $file = $Event.SourceEventArgs.FullPath
    if ($file -match "\\(app/src/main/(java|res|AndroidManifest\.xml)\\|build\\|gradle\\)") {
        $global:lastBuild = Get-Date
        Write-Host ""
        Write-Colored "Change detected in $file, rebuilding..." $YELLOW

        try {
            $buildOutput = & ./gradlew assembleDebug --quiet 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Colored "Build successful" $GREEN

                $installResult = (& adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1) -join " "
                if ($installResult -match "Success") {
                    & adb shell am force-stop com.godtap.dictionary 2>$null
                    Start-Sleep -Milliseconds 300
                    & adb shell am start -n com.godtap.dictionary/.MainActivity 2>$null
                    Write-Colored "App restarted" $GREEN
                    Write-Host ""
                    Write-Colored "Watching..." $BLUE
                } else {
                    Write-Colored "Install failed" $RED
                }
            } else {
                Write-Colored "Build failed, fix errors and save again" $YELLOW
                Write-Colored "Build output:" $RED
                $buildOutput | ForEach-Object { Write-Colored "  $_" $RED }
            }
        } catch {
            Write-Colored "Error during rebuild: $_" $RED
        }
    }
}

Register-ObjectEvent $watcher "Changed" -Action $action
Register-ObjectEvent $watcher "Created" -Action $action
Register-ObjectEvent $watcher "Renamed" -Action $action

Write-Colored "Hot-reload active. Press Ctrl+C to stop." $GREEN
try {
    while ($true) {
        Start-Sleep -Seconds 1
    }
} finally {
    $watcher.Dispose()
    Write-Colored "Hot-reload stopped." $YELLOW
}
