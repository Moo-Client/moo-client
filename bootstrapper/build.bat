@echo off
setlocal
echo ====================================================
echo   Kompilacja Moo Client Bootstrapper (C# .NET)
echo ====================================================

set CSC=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe

if not exist "%CSC%" (
    echo [ERROR] csc.exe nie znaleziono pod sciezka %CSC%
    exit /b 1
)

set ICON=icon.ico
set MANIFEST=app.manifest
set OUT=..\launcher\dist-win\MooClient-Setup.exe

if not exist "..\launcher\dist-win" mkdir "..\launcher\dist-win"

echo Kompilacja z uzyciem csc.exe i osadzaniem zasobow...
"%CSC%" /target:winexe /platform:anycpu /optimize+ /win32icon:"%ICON%" /win32manifest:"%MANIFEST%" /resource:logo.png /resource:icon.ico /out:"%OUT%" /r:System.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /r:Microsoft.CSharp.dll Program.cs

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ====================================================
    echo [SUKCES] Bootstrapper zbudowany pomyslnie!
    echo Plik docelowy: %OUT%
    echo ====================================================
) else (
    echo.
    echo [ERROR] Blad kompilacji csc.exe!
    exit /b 1
)
