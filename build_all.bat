@echo off
cd /d "%~dp0"

echo ======================
echo Building Docker images
echo ======================

docker compose -f docker-compose-prod.yaml build
if %errorlevel% neq 0 (
    echo ERROR: Docker build failed
    pause
    exit /b %errorlevel%
)

echo Docker build completed successfully

echo.
echo ==========================
echo Building EXE using PyInstaller
echo ==========================

call app\.venv\Scripts\activate

cd app

pyinstaller --noconfirm --onefile --name CamRelay src/app.py

if %errorlevel% neq 0 (
    echo ERROR: PyInstaller build failed
    pause
    exit /b %errorlevel%
)
echo.
echo ================================
echo ALL DONE - Docker + EXE ready!
echo ================================
pause
