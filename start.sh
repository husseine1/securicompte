#!/bin/bash

# ===================================================
# Script de démarrage - Securicompte (Linux/Mac)
# ===================================================

set -e

echo "╔════════════════════════════════════════════════════╗"
echo "║       SECURICOMPTE - Démarrage local              ║"
echo "╚════════════════════════════════════════════════════╝"
echo ""

# Vérifier les prérequis
echo "📋 Vérification des prérequis..."

# Vérifier Java
if ! command -v java &> /dev/null; then
    echo "❌ ERREUR: Java n'est pas installé. Veuillez installer Java 17 ou supérieur."
    echo "   Téléchargez à: https://www.oracle.com/java/technologies/downloads/"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | grep -oP '(?<=version ")[^"]*' | head -1)
echo "✅ Java détecté: $JAVA_VERSION"

# Vérifier Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ ERREUR: Maven n'est pas installé. Veuillez installer Maven 3.8 ou supérieur."
    echo "   Téléchargez à: https://maven.apache.org/download.cgi"
    exit 1
fi

MVN_VERSION=$(mvn -version | grep -oP '(?<=Apache Maven )[^ ]*' | head -1)
echo "✅ Maven détecté: $MVN_VERSION"

# Vérifier PostgreSQL
if ! command -v psql &> /dev/null; then
    echo "❌ ERREUR: PostgreSQL CLI n'est pas installé."
    echo "   Veuillez installer PostgreSQL 15 ou supérieur."
    echo "   Téléchargez à: https://www.postgresql.org/download/"
    exit 1
fi

PG_VERSION=$(psql --version | grep -oP '(?<=psql \(PostgreSQL\) )[^ ]*')
echo "✅ PostgreSQL CLI détecté: $PG_VERSION"

echo ""
echo "📦 Configuration PostgreSQL..."

# Vérifier si PostgreSQL est en cours d'exécution
if ! pg_isready -h localhost -U postgres &> /dev/null; then
    echo "❌ ERREUR: PostgreSQL n'est pas en cours d'exécution sur localhost:5432"
    echo ""
    echo "🔧 Pour démarrer PostgreSQL:"
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        echo "   brew services start postgresql"
        echo "   ou"
        echo "   brew services start postgresql@15"
    else
        # Linux
        echo "   sudo systemctl start postgresql"
    fi
    exit 1
fi

echo "✅ PostgreSQL est en cours d'exécution"

# Créer la base de données et l'utilisateur
echo ""
echo "🗄️  Création de la base de données..."

psql -U postgres -f setup-db.sql > /dev/null 2>&1 || {
    echo "⚠️  Tentative de création avec credentials par défaut..."
    psql -U postgres -h localhost -f setup-db.sql 2>/dev/null || {
        echo "❌ Impossible de se connecter à PostgreSQL avec l'utilisateur postgres"
        echo "   Vérifiez que PostgreSQL est correctement configuré."
        exit 1
    }
}

echo "✅ Base de données créée/configurée"

echo ""
echo "🔨 Compilation du projet..."
echo "   (cela peut prendre quelques minutes...)"

mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "❌ ERREUR: La compilation a échoué."
    exit 1
fi

echo "✅ Compilation réussie"

echo ""
echo "╔════════════════════════════════════════════════════╗"
echo "║         🚀 Démarrage de l'application             ║"
echo "╚════════════════════════════════════════════════════╝"
echo ""
echo "📍 L'application sera disponible à: http://localhost:8080"
echo ""
echo "👤 Identifiants de connexion:"
echo "   Admin:  admin / Admin@2024"
echo "   Agent:  agent1 / Agent@2024"
echo ""
echo "Appuyez sur Ctrl+C pour arrêter l'application"
echo ""

# Lancer l'application
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
