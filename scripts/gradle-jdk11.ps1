param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

function Get-Jdk11Home {
    $candidates = @()

    if ($env:JAVA11_HOME) {
        $candidates += $env:JAVA11_HOME
    }

    $searchRoots = @(
        (Join-Path $env:USERPROFILE ".jdks"),
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft"
    )

    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) {
            continue
        }

        $candidates += Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "jdk-11*" -or $_.Name -like "microsoft-jdk-11*" } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -ExpandProperty FullName
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        $javaExe = Join-Path $candidate "bin\java.exe"
        if (Test-Path $javaExe) {
            return $candidate
        }
    }

    throw "JDK 11 not found. Install one under %USERPROFILE%\\.jdks or set JAVA11_HOME first."
}

$jdkHome = Get-Jdk11Home
$env:JAVA_HOME = $jdkHome

$pathParts = @("$jdkHome\bin")
$pathParts += ($env:Path -split ";") | Where-Object {
    $_ -and $_ -notmatch "\\jdk-11" -and $_ -notmatch "\\jdk-21" -and $_ -notmatch "\\jdk-8"
}
$env:Path = [string]::Join(";", $pathParts)

Write-Host "Using JDK 11: $jdkHome"
& "$jdkHome\bin\java.exe" -version

& ".\gradlew.bat" --stop | Out-Null

if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @("test")
}

& ".\gradlew.bat" "--no-daemon" @GradleArgs
exit $LASTEXITCODE
