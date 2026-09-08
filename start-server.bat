@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================================
echo   Dang khoi dong Social Media Platform...
echo ============================================================
echo.

docker info >nul 2>&1
if errorlevel 1 (
    echo [LOI] Docker Desktop chua chay. Hay mo Docker Desktop roi chay lai file nay.
    echo.
    pause
    exit /b 1
)

docker compose up -d
if errorlevel 1 (
    echo.
    echo [LOI] docker compose up that bai. Neu day la lan chay dau tien, hay build jar
    echo       backend truoc bang lenh:  mvn clean package -DskipTests
    echo       roi chay:                 docker compose up -d --build
    echo.
    pause
    exit /b 1
)

echo.
echo Dang cho api-gateway san sang (co the mat 1-2 phut)...

set /a count=0
:waitloop
for /f "delims=" %%c in ('curl -s -o nul -w "%%{http_code}" http://localhost:8080/actuator/health 2^>nul') do set HEALTH=%%c
if "!HEALTH!"=="200" goto ready
set /a count+=1
if !count! GEQ 60 (
    echo.
    echo [CANH BAO] api-gateway chua san sang sau 5 phut.
    echo            Kiem tra log bang lenh: docker compose logs -f api-gateway
    goto end
)
timeout /t 5 /nobreak >nul
goto waitloop

:ready
echo.
echo ============================================================
echo   THANH CONG - He thong da san sang!
echo ============================================================
echo   - Giao dien web (Flutter):   http://localhost:3001
echo   - API Gateway:               http://localhost:8080
echo   - Eureka Dashboard:          http://localhost:8761
echo   - Grafana (log/metrics):     http://localhost:3000
echo ============================================================

:end
echo.
pause
