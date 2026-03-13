# 📚 Guide d'Installation Détaillé - Securicompte

## Table des matières
1. [Installation des prérequis](#installation-des-prérequis)
2. [Démarrage automatique](#démarrage-automatique)
3. [Démarrage manuel](#démarrage-manuel)
4. [Dépannage](#dépannage)
5. [Configuration avancée](#configuration-avancée)

---

## Installation des prérequis

### 1️⃣ Java 17

**Windows:**
1. Téléchargez le JDK 17 → https://www.oracle.com/java/technologies/downloads/
2. Lancez l'installer et suivez les instructions
3. Vérifiez l'installation: `java -version`

**macOS (avec Homebrew):**
```bash
brew install openjdk@17
```

**Linux (Debian/Ubuntu):**
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

**Linux (RedHat/Fedora):**
```bash
sudo dnf install java-17-openjdk java-17-openjdk-devel
```

### 2️⃣ Maven 3.8+

**Windows:**
1. Téléchargez Maven → https://maven.apache.org/download.cgi
2. Décompressez dans un dossier (ex: `C:\Program Files\maven`)
3. Ajoutez le dossier `bin` à votre PATH
4. Vérifiez: `mvn -version`

**macOS (avec Homebrew):**
```bash
brew install maven
```

**Linux:**
```bash
# Debian/Ubuntu
sudo apt install maven

# ou télécharger manuellement
wget https://archive.apache.org/dist/maven/maven-3/3.9.0/binaries/apache-maven-3.9.0-bin.tar.gz
tar -xzf apache-maven-3.9.0-bin.tar.gz
# Ajouter le dossier bin à votre PATH
```

### 3️⃣ PostgreSQL 15

**Windows:**
1. Téléchargez PostgreSQL → https://www.postgresql.org/download/windows/
2. Lancez l'installer
3. Définissez le mot de passe pour l'utilisateur `postgres`
4. Acceptez le port par défaut `5432`
5. Vérifiez: `psql --version`

**macOS (avec Homebrew):**
```bash
brew install postgresql@15
brew services start postgresql@15
```

**Linux (Debian/Ubuntu):**
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
```

**Linux (RedHat/Fedora):**
```bash
sudo dnf install postgresql-server postgresql-contrib
sudo systemctl start postgresql
```

---

## ✨ Démarrage Automatique

### Linux / macOS

```bash
chmod +x start.sh
./start.sh
```

### Windows

Double-cliquez sur `start.bat` ou ouvrez Command Prompt dans le dossier et tapez:
```batch
start.bat
```

Le script va:
1. Vérifier la présence de Java, Maven et PostgreSQL
2. Vérifier que PostgreSQL est en cours d'exécution
3. Créer la base de données et l'utilisateur
4. Compiler le projet
5. Démarrer l'application

---

## 📋 Démarrage Manuel

### Étape 1: Vérifier que PostgreSQL fonctionne

**Linux:**
```bash
sudo systemctl status postgresql
# Si arrêté:
sudo systemctl start postgresql
```

**macOS:**
```bash
brew services list | grep postgresql
# Si arrêté:
brew services start postgresql@15
```

**Windows:**
- Ouvrez "Services" (`services.msc`)
- Trouvez "PostgreSQL Server"
- Vérifiez que le statut est "En cours d'exécution"
- Si arrêté, cliquez droit → Démarrer

### Étape 2: Créer la base de données

```bash
psql -U postgres -f setup-db.sql
```

Vous serez invité à entrer le mot de passe de l'utilisateur `postgres`.

#### ✅ Résultat attendu:
```
CREATE DATABASE
CREATE ROLE
GRANT
ALTER DATABASE
GRANT
Base de données et utilisateur créés avec succès!
```

### Étape 3: Compiler le projet

```bash
mvn clean package -DskipTests
```

Cela peut prendre **2-5 minutes** la première fois.

#### ⚠️ En cas d'erreur de compilation:
```bash
# Supprimer le cache Maven
rm -rf ~/.m2/repository

# Réessayer la compilation
mvn clean package -DskipTests
```

### Étape 4: Lancer l'application

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

Vous devriez voir:
```
Started SecuricompteApplication in X.XXX seconds
```

### Étape 5: Accéder à l'application

Ouvrez votre navigateur: **http://localhost:8080**

Identifiants:
- Admin: `admin` / `Admin@2024`
- Agent: `agent1` / `Agent@2024`

---

## 🔧 Dépannage

### "java: command not found"
**Cause**: Java n'est pas installé ou pas dans le PATH

**Solution**:
1. Installez Java 17 (voir section Java)
2. Redémarrez votre terminal après installation
3. Vérifiez: `java -version`

### "mvn: command not found"
**Cause**: Maven n'est pas installé ou pas dans le PATH

**Solution**:
1. Installez Maven (voir section Maven)
2. Redémarrez votre terminal après installation
3. Vérifiez: `mvn -version`

### "psql: could not connect to server"
**Cause**: PostgreSQL n'est pas en cours d'exécution

**Solution**:
```bash
# Linux
sudo systemctl start postgresql

# macOS
brew services start postgresql@15

# Windows: Ouvrez Services et démarrez PostgreSQL Server
```

Puis vérifiez la connexion:
```bash
psql -U postgres -h localhost
```

### "FATAL: role 'securicompte_user' does not exist"
**Cause**: La base de données n'a pas été créée correctement

**Solution**:
```bash
# Supprimer la base existante
psql -U postgres -c "DROP DATABASE IF EXISTS securicompte_db;"
psql -U postgres -c "DROP USER IF EXISTS securicompte_user;"

# Recréer
psql -U postgres -f setup-db.sql
```

### "Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin"
**Cause**: Version de Java incompatible ou problème de compilation

**Solution**:
```bash
# Vérifier la version de Java
java -version

# Doit être Java 17+
# Si besoin, mettez à jour JAVA_HOME

# Sur Linux/Mac:
export JAVA_HOME=/path/to/java17
echo $JAVA_HOME

# Réessayez la compilation
mvn clean package -DskipTests
```

### "Compilation réussie mais l'application ne démarre pas"
**Cause**: Erreur de configuration ou base de données non accessible

**Solution**:
1. Vérifiez que PostgreSQL est en cours d'exécution
2. Vérifiez la connexion: `psql -U securicompte_user -d securicompte_db -h localhost`
3. Consultez les logs: `tail -100 /var/log/postgresql/` (Linux)

---

## ⚙️ Configuration Avancée

### Modifier le port de l'application

Éditez `src/main/resources/application-local.properties`:
```properties
server.port=9090
```

### Modifier les identifiants de base de données

Éditez `src/main/resources/application-local.properties`:
```properties
spring.datasource.username=mon_utilisateur
spring.datasource.password=mon_password
```

Et `setup-db.sql`:
```sql
CREATE USER mon_utilisateur WITH PASSWORD 'mon_password';
```

### Augmenter la limite d'upload

Éditez `src/main/resources/application-local.properties`:
```properties
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
```

### Activer le mode debug

Éditez `src/main/resources/application-local.properties`:
```properties
logging.level.com.securicompte=DEBUG
logging.level.org.springframework.security=DEBUG
```

### Utiliser une autre base de données

Pour MySQL au lieu de PostgreSQL:
1. Installez le driver MySQL
2. Modifiez les propriétés de datasource
3. Modifiez le dialecte Hibernate

⚠️ Vous aurez besoin d'adapter les scripts de migration Flyway (actuellement en PostgreSQL).

---

## 📞 Support

Si vous rencontrez des problèmes:

1. **Consultez les logs**: `mvn spring-boot:run` affiche les erreurs en détail
2. **Vérifiez PostgreSQL**: `psql -U postgres -c "SELECT version();"`
3. **Nettoyez le cache Maven**: `rm -rf ~/.m2/repository`
4. **Redémarrez votre ordinateur**: Parfois, les services ne se lancent pas correctement

---

## ✨ Prochaines étapes

1. Connectez-vous à http://localhost:8080
2. Explorez le dashboard
3. Testez l'import de fichiers Excel
4. Consultez la documentation métier dans README.md
