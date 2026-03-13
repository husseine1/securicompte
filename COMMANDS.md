# 🛠️ Commandes Courantes et Conseils

## 🚀 Démarrage Rapide

### Lancer l'application
```bash
# Linux/Mac
./start.sh

# Windows
start.bat

# Ou manuellement
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

## 🔨 Compilation

### Compiler sans tests
```bash
mvn clean package -DskipTests
```

### Compiler avec tests
```bash
mvn clean package
```

### Compiler un module spécifique
```bash
mvn clean compile -pl :module-name
```

### Mettre à jour les dépendances
```bash
mvn dependency:resolve
```

## 🗄️ Base de Données

### Se connecter à PostgreSQL
```bash
psql -U securicompte_user -d securicompte_db -h localhost
```

### Créer la base de données
```bash
psql -U postgres -f setup-db.sql
```

### Réinitialiser la base de données
```bash
# Linux/Mac
psql -U postgres -c "DROP DATABASE IF EXISTS securicompte_db;" && \
psql -U postgres -f setup-db.sql

# Windows
psql -U postgres -c "DROP DATABASE IF EXISTS securicompte_db;"
psql -U postgres -f setup-db.sql
```

### Voir toutes les bases de données
```bash
psql -U postgres -l
```

### Exécuter une requête SQL
```bash
psql -U securicompte_user -d securicompte_db -c "SELECT COUNT(*) FROM client;"
```

## 📊 Gestion des Logs

### Voir les logs en temps réel
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local" | grep -v "DEBUG"
```

### Augmenter le niveau de log
Éditez `src/main/resources/application-local.properties`:
```properties
logging.level.com.securicompte=DEBUG
logging.level.org.springframework=DEBUG
```

### Désactiver les logs Spring
```properties
logging.level.org.springframework=WARN
logging.level.org.springframework.security=ERROR
```

## 🧹 Nettoyage

### Nettoyer les fichiers compilés
```bash
mvn clean
```

### Nettoyer le cache Maven
```bash
rm -rf ~/.m2/repository  # Linux/Mac
rmdir /s %USERPROFILE%\.m2\repository  # Windows
```

### Supprimer le dossier target
```bash
rm -rf target  # Linux/Mac
rmdir /s target  # Windows
```

### Nettoyer les uploads
```bash
rm -rf uploads/*  # Linux/Mac
rmdir /s uploads  # Windows
```

## 🔍 Dépannage

### Vérifier que le port 8080 est disponible
```bash
# Linux/Mac
lsof -i :8080

# Windows (PowerShell)
Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess

# Windows (Command Prompt)
netstat -ano | findstr :8080
```

### Vérifier la connexion PostgreSQL
```bash
psql -U securicompte_user -d securicompte_db -h localhost -c "SELECT 1;"
```

### Vérifier la version Java
```bash
java -version
echo $JAVA_HOME  # Linux/Mac
echo %JAVA_HOME%  # Windows
```

### Vérifier la version Maven
```bash
mvn -version
```

## 📝 Configuration

### Profils Spring Boot
```bash
# Profil local (défaut)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Profil production (si créé)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=prod"
```

### Passer des propriétés en ligne de commande
```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5432/autre_db"
```

### Éditer les propriétés
- Fichier local: `src/main/resources/application-local.properties`
- Fichier par défaut: `src/main/resources/application.properties`

## 🌐 Accès à l'Application

### URL principale
```
http://localhost:8080
```

### API
```
http://localhost:8080/api/v1/...
```

### Identifiants par défaut
```
Admin:  admin / Admin@2024
Agent:  agent1 / Agent@2024
```

## 📦 Builds et Releases

### Créer un JAR exécutable
```bash
mvn package -DskipTests
java -jar target/securicompte-1.0.0.jar
```

### Créer une release
```bash
# Avec fichier d'exécution
mvn clean package -DskipTests
cp target/securicompte-1.0.0.jar releases/
cp -r src/main/resources/templates releases/
```

## 🐛 Debugging

### Lancer en debug
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

### Activer les logs SQL
Éditez `src/main/resources/application-local.properties`:
```properties
spring.jpa.show-sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

## 📋 Maintenance

### Régénérer la base de données
```bash
# Réinitialiser complètement
psql -U postgres -c "DROP DATABASE IF EXISTS securicompte_db; DROP USER IF EXISTS securicompte_user;"
psql -U postgres -f setup-db.sql
```

### Vérifier l'espace disque PostgreSQL
```bash
psql -U securicompte_user -d securicompte_db -c "SELECT pg_database.datname, pg_size_pretty(pg_database_size(pg_database.datname)) FROM pg_database;"
```

### Sauvegarder la base de données
```bash
pg_dump -U securicompte_user -d securicompte_db -h localhost > backup.sql
```

### Restaurer une sauvegarde
```bash
psql -U securicompte_user -d securicompte_db -h localhost < backup.sql
```

---

## 💡 Conseils et Bonnes Pratiques

1. **Toujours compiler avant de tester**
   ```bash
   mvn clean package -DskipTests
   ```

2. **Gardez PostgreSQL en cours d'exécution**
   - Linux: `systemctl status postgresql`
   - macOS: `brew services list`
   - Windows: Vérifiez dans Services

3. **Utilisez les logs pour déboguer**
   - Augmentez le niveau de log en local
   - Cherchez les `ERROR` et `WARN`

4. **Nettoyez le cache régulièrement**
   ```bash
   rm -rf ~/.m2/repository
   ```

5. **Testez avec les données réelles**
   - Téléchargez un fichier Excel d'exemple
   - Testez l'import et la détection

6. **Sauvegardez votre base de données**
   ```bash
   pg_dump -U securicompte_user -d securicompte_db > backup.sql
   ```

---

## 🎯 Prochaines Étapes

- Consultez le README.md pour les détails métier
- Consultez le SETUP.md pour une installation détaillée
- Explorez le code dans `src/main/java/com/securicompte/`
- Testez les fonctionnalités principales
