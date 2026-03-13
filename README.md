# Securicompte - Système de détection des impayés

## Description
Application web Spring Boot pour la gestion et la détection automatique des impayés du produit financier Securicompte.

## Stack technique
- **Backend**: Java 17, Spring Boot 3.2, Spring Security, JPA/Hibernate
- **Base de données**: PostgreSQL + Flyway (migrations)
- **Frontend**: Thymeleaf + Bootstrap 5 + Chart.js
- **Excel**: Apache POI (import/export)
- **Sécurité**: Spring Security (sessions), BCrypt

## 🚀 Démarrage Rapide (Sans Docker)

### Prérequis
- **Java 17 ou supérieur** → [Télécharger](https://www.oracle.com/java/technologies/downloads/)
- **Maven 3.8 ou supérieur** → [Télécharger](https://maven.apache.org/download.cgi)
- **PostgreSQL 15 ou supérieur** → [Télécharger](https://www.postgresql.org/download/)

### Installation & Démarrage

#### ✨ Méthode Automatique (Recommandée)

**Linux / Mac:**
```bash
chmod +x start.sh
./start.sh
```

**Windows (Command Prompt ou PowerShell):**
```batch
start.bat
```

Le script va:
1. ✅ Vérifier les prérequis (Java, Maven, PostgreSQL)
2. ✅ Créer la base de données et l'utilisateur
3. ✅ Compiler le projet
4. ✅ Démarrer l'application

#### 📋 Méthode Manuelle

**Étape 1: Assurer que PostgreSQL est en cours d'exécution**

Sur Linux:
```bash
sudo systemctl start postgresql
```

Sur macOS (avec Homebrew):
```bash
brew services start postgresql
```

Sur Windows:
- Ouvrez "Services" et démarrez le service PostgreSQL
- Ou en Command Prompt (Admin): `net start postgresql-x64-15`

**Étape 2: Créer la base de données**

```bash
psql -U postgres -f setup-db.sql
```

**Étape 3: Compiler et lancer l'application**

```bash
mvn clean package -DskipTests
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

### 4. Accéder à l'application

🌐 **URL**: http://localhost:8080

👤 **Identifiants de connexion**:
- **Admin**: `admin` / `Admin@2024`
- **Agent**: `agent1` / `Agent@2024`

### ⚙️ Configuration

La configuration par défaut est dans `src/main/resources/application-local.properties`:
- **Base de données**: `localhost:5432/securicompte_db`
- **Utilisateur DB**: `securicompte_user`
- **Port application**: `8080`

Pour modifier la configuration, éditez ce fichier avant de lancer l'application.

## Structure du projet
```
src/main/java/com/securicompte/
├── config/          # SecurityConfig
├── controller/      # DashboardController, ImportController, ClientController, ImpayeController
├── dto/             # ExcelRowDto, ImpayeFilterDto, ImpayeResponseDto
├── entity/          # Client, Souscription, StockMensuel, Impaye, ImportFichier, Utilisateur
├── enums/           # Role, StatutImpaye, StatutImport, TypeSouscription
├── exception/       # ImportException, ResourceNotFoundException, GlobalExceptionHandler
├── repository/      # ClientRepository, ImpaieRepository, StockMensuelRepository, etc.
└── service/
    ├── impl/        # ImportServiceImpl, ImpayeDetectionServiceImpl, ExcelExportServiceImpl
    └── I*.java      # Interfaces de service
```

## Règles métier - Détection des impayés

1. À chaque import mensuel, le système compare le stock du mois avec les souscriptions actives
2. Si un client souscrit n'est PAS dans le stock → **IMPAYÉ** créé
3. Si un client réapparaît dans le stock → ses impayés précédents passent en **RÉGULARISÉ** (historique conservé)
4. Les mois d'absence restent IMPAYÉ dans l'historique même après régularisation

## Feuilles Excel attendues
| Feuille | Description |
|---------|-------------|
| `Nouvelle souscription` | Nouveaux clients ce mois |
| `Ancienne souscription` | Clients existants |
| `Stock du mois` | Clients ayant payé ce mois |

## Colonnes Excel (ordre)
CLIENT, SECURICOMPTE, COMMISSIONS, COMPTE, LIBELL_PACKAGE, OPTION SECURICOMPTE, DATSOUSCRIPTION, DATNAISSANCE, ZONELIB, AGENCELIB, NOM, DATOUV, GESTIONNAIRE

## Rôles utilisateurs
| Rôle | Permissions |
|------|------------|
| ADMIN | Tout (import, gestion utilisateurs, suppression) |
| AGENT | Import fichiers, consultation |
| CONSULTATION | Lecture seule |
