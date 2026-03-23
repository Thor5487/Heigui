@echo off
echo ==============================================
echo  🚀 Starting Heigui Dual-Build Process...
echo ==============================================

echo.
echo [1/2] Building FREE version...
call gradlew clean build -Pauth=false

echo.
echo [2/2] Building AUTH version...
call gradlew clean build -Pauth=true

echo.
echo ==============================================
echo  ✅ All Builds Finished Successfully!
echo  Check your build/libs folder for the jars.
echo ==============================================
pause