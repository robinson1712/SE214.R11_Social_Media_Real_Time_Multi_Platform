@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "infra\no-docker\scripts\Start.ps1"
if errorlevel 1 (
  echo.
  echo Khoi dong that bai. Xem .runtime\logs va chay Doctor.ps1 de biet chi tiet.
  pause
  exit /b 1
)
pause
