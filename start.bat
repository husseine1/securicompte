@echo off
REM ===================================================
REM Script de démarrage - Securicompte (Windows)
REM ===================================================

setlocal enabledelayedexpansion

title Securicompte - Demarrage local

echo.
echo ╔════════════════════════════════════════════════════╗
echo ║       SECURICOMPTE - Démarrage local              ║
echo ╚════════════════════════════════════════════════════╝
echo.

echo 📋 Vérification des prérequis...

REM Vérifier Java
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ ERREUR: Java n'est pas installé. Veuillez installer Java 17 ou supérieur.
    echo.
    echo Téléchargez à: https://www.oracle.com/java/technologies/downloads/
    echo.
    pause
    exit /b 1
)

for /f "tokens=*" %%i in ('java -version 2^>^&1 ^| findstr /R "version"') do set JAVA_VERSION=%%i
echo ✅ Java détecté: %JAVA_VERSION%

REM Vérifier Maven
mvn -version >nul 2>&1
if errorlevel 1 (
    echo ❌ ERREUR: Maven n'est pas installé. Veuillez installer Maven 3.8 ou supérieur.
    echo.
    echo Téléchargez à: https://maven.apache.org/download.cgi
    echo.
    pause
    exit /b 1
)

for /f "tokens=3" %%i in ('mvn -version ^| findstr "Apache Maven"') do set MVN_VERSION=%%i
echo ✅ Maven détecté: %MVN_VERSION%

REM Vérifier PostgreSQL
psql --version >nul 2>&1
if errorlevel 1 (
    echo ❌ ERREUR: PostgreSQL CLI n'est pas installé.
    echo.
    echo Veuillez installer PostgreSQL 15 ou supérieur.
    echo Téléchargez à: https://www.postgresql.org/download/
    echo.
    pause
    exit /b 1
)

for /f "tokens=3" %%i in ('psql --version') do set PG_VERSION=%%i
echo ✅ PostgreSQL CLI détecté: %PG_VERSION%

echo.
echo 📦 Configuration PostgreSQL...

REM Vérifier si PostgreSQL est en cours d'exécution
psql -h localhost -U postgres -c "SELECT version();" >nul 2>&1
if errorlevel 1 (
    echo ❌ ERREUR: PostgreSQL n'est pas en cours d'exécution sur localhost:5432
    echo.
    echo 🔧 Pour démarrer PostgreSQL sous Windows:
    echo.
    echo   - Ouvrez "Services" (services.msc)
    echo   - Trouvez "PostgreSQL Server"
    echo   - Cliquez droit et sélectionnez "Démarrer"
    echo.
    echo   ou utilisez Command Prompt en administrateur:
    echo.
    echo   net start postgresql-x64-15
    echo.
    pause
    exit /b 1
)

echo ✅ PostgreSQL est en cours d'exécution

echo.
echo 🗄️  Création de la base de données...

psql -U postgres -f setup-db.sql >nul 2>&1
if errorlevel 1 (
    echo ⚠️  Tentative de création avec credentials alternatifs...
    REM Essayer avec la connection locale
    psql -h localhost -U postgres -f setup-db.sql >nul 2>&1
    if errorlevel 1 (
        echo ❌ Impossible de se connecter à PostgreSQL
        echo.
        echo Vérifiez que:
        echo   1. PostgreSQL est en cours d'exécution
        echo   2. L'utilisateur 'postgres' peut se connecter
        echo.
        pause
        exit /b 1
    )
)

echo ✅ Base de données créée/configurée

echo.
echo 🔨 Compilation du projet...
echo.    (cela peut prendre quelques minutes...)
echo.

call mvn clean package -DskipTests -q

if errorlevel 1 (
    echo ❌ ERREUR: La compilation a échoué.
    echo.
    pause
    exit /b 1
)

echo ✅ Compilation réussie

echo.
echo ╔════════════════════════════════════════════════════╗
echo ║         🚀 Démarrage de l'application             ║
echo ╚════════════════════════════════════════════════════╝
echo.
echo 📍 L'application sera disponible à: http://localhost:8080
echo.
echo 👤 Identifiants de connexion:
echo    Admin:  admin / Admin@2024
echo    Agent:  agent1 / Agent@2024
echo.
echo Appuyez sur Ctrl+C pour arrêter l'application
echo.

REM Lancer l'application
call mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

pause
