#!/bin/bash
# ===================================================
# Script de démarrage - Securicompte (Linux/Mac)
# ===================================================

echo ""
echo "===================================================="
echo "       SECURICOMPTE - Démarrage local"
echo "===================================================="
echo ""

# ─── Java ────────────────────────────────────────────
echo "[1/5] Vérification Java..."
if ! command -v java &>/dev/null; then
    echo "ERREUR: Java introuvable. Installez Java 17+."
    echo "  https://www.oracle.com/java/technologies/downloads/"
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | grep -oP '(?<=version ")[^"]*' | head -1)
echo "[OK] Java $JAVA_VER"

# ─── Maven ───────────────────────────────────────────
echo "[2/5] Vérification Maven..."
if ! command -v mvn &>/dev/null; then
    echo "ERREUR: Maven introuvable. Installez Maven 3.8+."
    echo "  https://maven.apache.org/download.cgi"
    exit 1
fi
MVN_VER=$(mvn -version 2>&1 | grep -oP '(?<=Apache Maven )[^ ]*')
echo "[OK] Maven $MVN_VER"

# ─── PostgreSQL ───────────────────────────────────────
echo "[3/5] Vérification PostgreSQL..."
if ! command -v psql &>/dev/null; then
    echo "ERREUR: psql introuvable. Installez PostgreSQL 15+."
    exit 1
fi

export PGPASSWORD=postgres
if ! psql -h localhost -U postgres -c "SELECT 1;" &>/dev/null; then
    echo "ERREUR: PostgreSQL ne répond pas sur localhost:5432."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "  Démarrez avec : brew services start postgresql"
    else
        echo "  Démarrez avec : sudo systemctl start postgresql"
    fi
    exit 1
fi
echo "[OK] PostgreSQL actif"

# Créer la base si elle n'existe pas
if psql -h localhost -U postgres -tc "SELECT 1 FROM pg_database WHERE datname='securicompte_db';" 2>/dev/null | grep -q 1; then
    echo "[OK] Base de données existante"
else
    psql -h localhost -U postgres -c "CREATE DATABASE securicompte_db ENCODING='UTF8';" &>/dev/null
    echo "[OK] Base de données créée"
fi

# ─── Port 8080 ───────────────────────────────────────
echo "[4/5] Vérification port 8080..."
PID_8080=""
if command -v lsof &>/dev/null; then
    PID_8080=$(lsof -ti:8080 2>/dev/null | head -1)
elif command -v ss &>/dev/null; then
    PID_8080=$(ss -tlnp 2>/dev/null | awk '/:8080 /{match($NF,/pid=([0-9]+)/,a); if(a[1]) print a[1]}' | head -1)
fi

if [ -n "$PID_8080" ]; then
    read -r -p "    Port 8080 occupé (PID $PID_8080). L'arrêter ? (o/N) : " REP
    if [[ "$REP" =~ ^[Oo]$ ]]; then
        kill -9 "$PID_8080" 2>/dev/null
        echo "[OK] Processus $PID_8080 arrêté"
    else
        echo "Abandon : arrêtez manuellement le processus sur le port 8080."
        exit 1
    fi
else
    echo "[OK] Port 8080 disponible"
fi

# ─── Compilation ─────────────────────────────────────
echo "[5/5] Compilation..."
if ! mvn clean package -DskipTests -q; then
    echo "ERREUR: La compilation a échoué."
    echo "Lancez 'mvn compile' pour voir le détail des erreurs."
    exit 1
fi
echo "[OK] Compilation réussie"

# ─── Démarrage ───────────────────────────────────────
echo ""
echo "===================================================="
echo "        Application prête"
echo "===================================================="
echo ""
echo "  URL     : http://localhost:8080"
echo "  Admin   : admin  / admin123"
echo "  Agent   : agent  / agent123"
echo ""
echo "  Ctrl+C pour arrêter l'application."
echo ""

mvn spring-boot:run
