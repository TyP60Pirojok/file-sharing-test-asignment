@echo off
echo Building project...
echo.

if exist gradlew.bat (
    call gradlew.bat build
    echo Build completed!
) else (
    echo Gradle Wrapper not found. Please run: gradle wrapper
    pause
)
echo Starting File Server and opening browser...
echo.

REM Запускаем сервер в фоновом режиме
echo Starting server...
start "File Server" /B gradlew.bat run

REM Ждем 5 секунд чтобы сервер успел запуститься
echo Waiting for server to start...
timeout /t 5 /nobreak > nul

REM Открываем браузер с нужной страницей
echo Opening browser...
start http://localhost:8080

echo.
echo Server is running in the background.
echo Browser should open automatically.
echo If not, manually open: http://localhost:8080
echo.
echo Press any key to stop the server...
pause > nul

REM Останавливаем сервер (находим процесс Java и убиваем его)
echo Stopping server...
taskkill /f /im java.exe > nul 2>&1

echo Server stopped.