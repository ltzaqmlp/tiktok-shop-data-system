@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo.
echo TikTok Shop 数据平台一键发版
echo 1. 发布测试环境
echo 2. 发布生产环境
echo 3. 先测试，成功后发布生产
echo 0. 退出
echo.
choice /c 1230 /n /m "请选择："

if errorlevel 4 exit /b 0
if errorlevel 3 goto both
if errorlevel 2 goto prod
goto test

:test
call :release test
goto result

:prod
call :confirm-prod
if errorlevel 1 exit /b 0
call :release prod
goto result

:both
call :release test
if errorlevel 1 goto result
call :confirm-prod
if errorlevel 1 exit /b 0
call :release prod
goto result

:confirm-prod
echo.
choice /c YN /n /m "即将发布生产环境，确认继续？[Y/N]："
if errorlevel 2 exit /b 1
exit /b 0

:release
set "ENV=%~1"
set "PROJECT=shop-%ENV%"
set "ENV_FILE=%~dp0.env.%ENV%"
if /i "%ENV%"=="prod" if not exist "%ENV_FILE%" set "ENV_FILE=%~dp0.env"
if /i "%ENV%"=="test" set "URL=http://192.168.100.39:18080/api/v1/system/health"
if /i "%ENV%"=="prod" set "URL=http://127.0.0.1:8080/api/v1/system/health"

echo.
echo ===== 开始发布 %ENV% 环境 =====
echo [1/4] 备份数据库...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0backup.ps1" -Environment "%ENV%"
if errorlevel 1 exit /b 1

echo [2/4] 无缓存构建并重启应用...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0update-no-cache.ps1" -Environment "%ENV%"
if errorlevel 1 exit /b 1

echo [3/4] 检查容器状态...
docker compose -p "%PROJECT%" --env-file "%ENV_FILE%" ps
if errorlevel 1 exit /b 1

echo [4/4] 检查健康接口...
curl.exe --fail --silent --show-error "%URL%"
if errorlevel 1 exit /b 1

echo.
echo %ENV% 环境发布成功：%URL%
exit /b 0

:result
if errorlevel 1 (
  echo.
  echo 发布失败，请查看上面的错误信息。
  pause
  exit /b 1
)
echo.
echo 发布完成。
pause
exit /b 0
