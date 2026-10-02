# Deploy BasinWatch on Windows

This guide builds a portable Windows application image with a private Java runtime. The recipient does not need to install Java, Maven, Swing, or any other framework. BasinWatch is an offline desktop application; this process does not deploy a web server.

## What to distribute

Distribute the generated **ZIP of the complete `BasinWatch` app-image folder**, not the JAR by itself. The image contains `BasinWatch.exe`, the application JAR, and the runtime required to start it.

## 1. Prepare the build computer

Use a Windows computer with:

- JDK 17 or newer, including `jpackage`.
- Maven 3.8 or newer.
- PowerShell.

Set `JAVA_HOME` to the JDK root, and make sure the JDK `bin` directory and Maven `bin` directory are on `PATH`. Open a new PowerShell window and check:

```powershell
java -version
javac -version
mvn -version
jpackage --version
```

All four commands must succeed. `JAVA_HOME` should be the JDK folder itself (the folder containing `bin`), not its `bin` subfolder.

## 2. Open the BasinWatch project folder

In PowerShell:

```powershell
cd C:\Users\srish\Downloads\unit2_oops_raw\basinwatch
```

For a copy of the project in another location, replace this path with the folder containing `pom.xml`.

## 3. Run the tests

```powershell
.\scripts\test.ps1
```

Wait for `BasinWatch invariant suite passed (...) assertions.` Do not distribute a build that did not pass.

The test runner performs Java compilation and exercises concurrent resource reservations, producer/consumer wake-up and queue closing, operations, corrupt session handling, archive validation, and an engine save/restore/shutdown workflow. PowerShell's current execution policy can be overridden for just this process if needed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test.ps1
```

## 4. Build and package the portable app

Recommended: run the packaging script. It runs the tests, builds the JAR, packages a runtime with `jpackage`, and zips the resulting app image:

```powershell
.\scripts\package-windows.ps1
```

The script prints the full release directory and ZIP path. Releases are given unique timestamped folders under `dist`, so an earlier build or user data is not overwritten.

To build manually instead:

```powershell
mvn -q package
$packageInput = "target\package-input"
New-Item -ItemType Directory -Force -Path $packageInput | Out-Null
Copy-Item "target\basinwatch-1.0.0.jar" "$packageInput\basinwatch-1.0.0.jar"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$release = Join-Path (Get-Location) "dist\BasinWatch-Windows-$stamp"
New-Item -ItemType Directory -Force -Path $release | Out-Null
& "$env:JAVA_HOME\bin\jpackage.exe" `
  --type app-image `
  --name BasinWatch `
  --app-version 1.0.0 `
  --vendor BasinWatch `
  --description "Offline floodplain operations console" `
  --input $packageInput `
  --main-jar basinwatch-1.0.0.jar `
  --main-class com.basinwatch.app.BasinWatchApp `
  --dest $release
Compress-Archive `
  -Path (Join-Path $release "BasinWatch") `
  -DestinationPath (Join-Path $release "BasinWatch-Windows.zip")
```

The result looks like:

```text
dist\
  BasinWatch-Windows-<timestamp>\
    BasinWatch\
      BasinWatch.exe
      app\
      runtime\
    BasinWatch-Windows.zip
```

Keep the `BasinWatch` directory intact. `app` and `runtime` are required files, not optional build leftovers.

## 5. Smoke-test the release before sharing it

Run the built launcher on the build computer:

```powershell
& ".\dist\BasinWatch-Windows-<timestamp>\BasinWatch\BasinWatch.exe"
```

Verify that the window opens, start observations, make one dispatch, save a session, export a report, then close the application normally. Confirm the app starts after extracting the ZIP to a different writable folder. For a meaningful portability check, use a Windows account or test computer without a JDK on `PATH`.

The application's working data is deliberately outside the installation folder, so the app image itself can be placed in a read-only location.

## 6. Deliver the ZIP

Copy `BasinWatch-Windows.zip` to the recipient. Provide the recipient with these steps:

1. Extract the entire ZIP to a folder they can access, such as `Documents\BasinWatch`.
2. Open the extracted `BasinWatch` folder.
3. Double-click `BasinWatch.exe`.
4. If Windows asks for confirmation, verify the ZIP came from the expected sender before choosing to run it.

The app image bundles its Java runtime. **Do not send only `BasinWatch.exe`, the JAR, or the `app` subfolder.**

## 7. Explain where sessions and reports are stored

On Windows, BasinWatch stores user data here:

```text
%APPDATA%\BasinWatch\data\
  logs\       operations.log
  archive\    sensors.bin
  saves\      last-session.bws and user-named saves
  reports\    exported situation reports
```

Back up this `data` folder before upgrading or moving to a different PC. The `last-session.bws` file is updated after a clean exit. For a separate data location, start the launcher from PowerShell with:

```powershell
& ".\BasinWatch.exe" --data-dir "D:\BasinWatch Data"
```

With a custom directory, BasinWatch creates its `data` subfolders underneath the selected directory.

## 8. Updating a deployment

Build and distribute a new timestamped ZIP using the same process. The app-image folder does not contain the operator's saved scenarios, so replacing it does not require replacing the separate `%APPDATA%\BasinWatch\data` folder. Keep a backup before upgrading. Session formats are versioned; if an older save is no longer supported, BasinWatch reports the restore problem instead of silently discarding it.

## Optional: create an installer

The portable app image is the simplest, framework-free distribution. Creating an MSI is a separate Windows installer build and requires a supported WiX Toolset installation on the packaging computer. This project's packaging script intentionally creates a portable ZIP and does not require WiX.

For a signed public release, sign the launcher/installer with an organizational code-signing certificate and verify the signature on the final artifact. Signing certificates are not included in this project or its build.

## Troubleshooting

- **`jpackage` is not recognized:** Install a full JDK (not a JRE), set `JAVA_HOME` to its root, add `%JAVA_HOME%\bin` to `PATH`, then open a new PowerShell window.
- **`mvn` is not recognized:** Install Maven and add its `bin` directory to `PATH`, then reopen PowerShell.
- **`.\scripts\test.ps1` is blocked:** Run the documented `powershell -NoProfile -ExecutionPolicy Bypass -File ...` command for that one script.
- **The app cannot create its data folders:** Choose a writable per-user directory or pass `--data-dir` with a writable path.
- **A save will not restore:** Keep the original save unchanged; verify that it is a BasinWatch `.bws` file and that it came from a compatible app version.
- **Only a Java process starts, with no window:** Launch `BasinWatch.exe` on an interactive Windows desktop. This is a Swing desktop program, not a Windows service.

## Deployment boundary

BasinWatch is an illustrative offline planning simulation. It is not a validated flood forecast, public warning system, infrastructure controller, or replacement for emergency procedures.
