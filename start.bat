@echo off
setlocal enabledelayedexpansion

title Securicompte - Demarrage

echo.
echo ====================================================
echo        SECURICOMPTE - Demarrage local
echo ====================================================
echo.

REM ─── Java ────────────────────────────────────────────
echo [1/5] Verification Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo ERREUR: Java introuvable. Installez Java 17+.
    echo Telechargez : https://www.oracle.com/java/technologies/downloads/
    pause & exit /b 1
)
for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr "version"') do set JAVA_VER=%%i
echo [OK] Java %JAVA_VER%

REM ─── Maven ───────────────────────────────────────────
echo [2/5] Verification Maven...
mvn -version >nul 2>&1
if errorlevel 1 (
    echo ERREUR: Maven introuvable. Installez Maven 3.8+.
    echo Telechargez : https://maven.apache.org/download.cgi
    pause & exit /b 1
)
for /f "tokens=3" %%i in ('mvn -version ^| findstr "Apache Maven"') do set MVN_VER=%%i
echo [OK] Maven %MVN_VER%

REM ─── Chercher psql (PATH ou installation standard) ───
echo [3/5] Verification PostgreSQL...
set PSQL=psql
psql --version >nul 2>&1
if errorlevel 1 (
    for /d %%d in ("C:\Program Files\PostgreSQL\*") do (
        if exist "%%d\bin\psql.exe" set PSQL=%%d\bin\psql.exe
    )
)
"%PSQL%" --version >nul 2>&1
if errorlevel 1 (
    echo ERREUR: psql introuvable. Ajoutez le dossier bin de PostgreSQL au PATH.
    pause & exit /b 1
)

set PGPASSWORD=postgres
"%PSQL%" -h localhost -U postgres -c "SELECT 1;" >nul 2>&1
if errorlevel 1 (
    echo ERREUR: PostgreSQL ne repond pas sur localhost:5432.
    echo Demarrez le service : net start postgresql-x64-18
    echo ou ouvrez services.msc et demarrez le service PostgreSQL.
    pause & exit /b 1
)
echo [OK] PostgreSQL actif

REM ─── Creer la base si elle n'existe pas ──────────────
"%PSQL%" -h localhost -U postgres -tc "SELECT 1 FROM pg_database WHERE datname='securicompte_db';" 2>nul | findstr /r "^ *1" >nul 2>&1
if errorlevel 1 (
    echo     Creation de la base securicompte_db...
    "%PSQL%" -h localhost -U postgres -c "CREATE DATABASE securicompte_db ENCODING='UTF8';" >nul 2>&1
    echo [OK] Base de donnees creee
) else (
    echo [OK] Base de donnees existante
)

REM ─── Port 8080 ───────────────────────────────────────
echo [4/5] Verification port 8080...
set PID_8080=
for /f "tokens=5" %%p in ('netstat -ano 2^>nul ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    if not defined PID_8080 set PID_8080=%%p
)
if defined PID_8080 (
    echo     Port 8080 occupe par le processus PID=%PID_8080%.
    set /p KILL_IT=    Voulez-vous l'arreter ? (O/N) :
    if /i "!KILL_IT!"=="O" (
        taskkill /PID %PID_8080% /F >nul 2>&1
        echo [OK] Processus arrete
    ) else (
        echo Abandon : arretez manuellement le processus sur le port 8080.
        pause & exit /b 1
    )
) else (
    echo [OK] Port 8080 disponible
)

REM ─── Compilation ─────────────────────────────────────
echo [5/5] Compilation...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo ERREUR: La compilation a echoue.
    echo Lancez "mvn compile" pour voir le detail des erreurs.
    pause & exit /b 1
)
echo [OK] Compilation reussie

REM ─── Demarrage ───────────────────────────────────────
echo.
echo ====================================================
echo         Application prete
echo ====================================================
echo.
echo   URL     : http://localhost:8080
echo   Admin   : admin  / admin123
echo   Agent   : agent  / agent123
echo.
echo   Ctrl+C pour arreter l'application.
echo.

call mvn spring-boot:run

pause
