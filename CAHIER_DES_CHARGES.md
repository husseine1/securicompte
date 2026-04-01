# CAHIER DES CHARGES — APPLICATION SECURICOMPTE
## Système de Gestion des Impayés Bancaires

---

**Référence** : SECURICOMPTE-CDC-v9
**Version** : 9.0
**Date** : 01 avril 2026
**Statut** : En production

---

## TABLE DES MATIÈRES

1. [Présentation générale](#1-présentation-générale)
2. [Contexte et objectifs](#2-contexte-et-objectifs)
3. [Périmètre fonctionnel](#3-périmètre-fonctionnel)
4. [Architecture technique](#4-architecture-technique)
5. [Modèle de données](#5-modèle-de-données)
6. [Fonctionnalités détaillées](#6-fonctionnalités-détaillées)
7. [Gestion des droits et sécurité](#7-gestion-des-droits-et-sécurité)
8. [Interfaces et intégrations](#8-interfaces-et-intégrations)
9. [Exigences non-fonctionnelles](#9-exigences-non-fonctionnelles)
10. [Contraintes et règles métier](#10-contraintes-et-règles-métier)
11. [Gestion des erreurs et journalisation](#11-gestion-des-erreurs-et-journalisation)
12. [Glossaire](#12-glossaire)

---

## 1. PRÉSENTATION GÉNÉRALE

### 1.1 Identification du projet

| Champ          | Valeur                              |
|----------------|-------------------------------------|
| Nom projet     | SecuriCompte                        |
| Nom applicatif | Gestion des Impayés                 |
| Repository     | husseine1/securicompte (GitHub)     |
| Branche stable | main                                |
| Version courante | v9                                |
| URL locale     | http://localhost:8080               |

### 1.2 Description sommaire

SecuriCompte est une application web intranet développée en Java (Spring Boot), destinée à la gestion et au suivi des impayés bancaires liés aux souscriptions SecuriCompte. Elle permet de détecter automatiquement les clients en situation d'impayé, de suivre leur régularisation et de produire des rapports statistiques.

---

## 2. CONTEXTE ET OBJECTIFS

### 2.1 Contexte métier

Dans le cadre de la gestion des comptes bancaires, les clients souscrivent à des produits de type "SecuriCompte" (assurance/protection de compte). Chaque mois, un fichier Excel est produit par le système central bancaire contenant :

- Les **nouvelles souscriptions** du mois
- Les **anciennes souscriptions** actives
- Le **stock mensuel** des clients ayant réglé leur prime

Un client est en situation d'**impayé** lorsqu'il figure dans les souscriptions actives mais n'apparaît pas dans le stock mensuel (la prime du mois n'a pas été prélevée).

### 2.2 Problèmes résolus

- Détection manuelle fastidieuse et sujette aux erreurs
- Absence de traçabilité des régularisations
- Pas de vision consolidée par agence/gestionnaire
- Difficulté d'identifier les changements de tarification

### 2.3 Objectifs principaux

| # | Objectif |
|---|----------|
| O1 | Importer automatiquement les fichiers Excel mensuels du système central |
| O2 | Détecter automatiquement les clients en impayé pour chaque mois |
| O3 | Permettre le suivi et la régularisation manuelle des impayés |
| O4 | Alerter les gestionnaires des impayés anciens et des changements de prime |
| O5 | Produire des statistiques et des exports pour le reporting |
| O6 | Gérer les cas particuliers (sinistres, litiges, trop-perçus) |

---

## 3. PÉRIMÈTRE FONCTIONNEL

### 3.1 Modules inclus

| Module | Description |
|--------|-------------|
| **Import fichiers** | Upload et traitement des fichiers Excel mensuels |
| **Détection impayés** | Algorithme automatique de détection |
| **Gestion impayés** | Consultation, régularisation, filtrage, export |
| **Gestion clients** | Fiche client, historique, sinistres |
| **Import sinistres** | Import en masse des clients sinistrés |
| **Notifications** | Alertes changements de prime et anciens impayés |
| **Dashboard** | Statistiques et indicateurs de performance |
| **Administration** | Gestion des utilisateurs, recalcul |
| **Export** | Export Excel et PDF des données |
| **Monitoring** | Endpoints Actuator pour la supervision |

### 3.2 Modules exclus

- Système de messagerie interne
- Module de prélèvement automatique
- Intégration temps réel avec le système central bancaire
- Application mobile

---

## 4. ARCHITECTURE TECHNIQUE

### 4.1 Stack technologique

| Couche | Technologie | Version |
|--------|-------------|---------|
| Langage | Java | 17 |
| Framework backend | Spring Boot | 3.2.3 |
| ORM | Hibernate / Spring Data JPA | 3.x |
| Sécurité | Spring Security | 6.x |
| Templating | Thymeleaf | 3.x |
| Base de données | PostgreSQL | 14+ |
| Migration DB | Flyway | 9.x |
| Parsing Excel | Apache POI | 5.2.5 |
| Export PDF | OpenPDF | 1.3.30 |
| Mapping DTO | MapStruct | 1.5.5 |
| Réduction boilerplate | Lombok | latest |
| Monitoring | Spring Actuator | 3.x |
| Build | Maven | 3.x |

### 4.2 Pattern architectural

```
┌──────────────────────────────────────────────────────┐
│                   NAVIGATEUR WEB                      │
│              (Thymeleaf + Bootstrap 5)               │
└─────────────────────┬────────────────────────────────┘
                      │ HTTP / AJAX
┌─────────────────────▼────────────────────────────────┐
│              CONTROLLERS (Spring MVC)                 │
│  Auth | Client | Impayé | Import | Sinistre |        │
│  Dashboard | Notification | Admin                     │
└─────────────────────┬────────────────────────────────┘
                      │
┌─────────────────────▼────────────────────────────────┐
│                 SERVICES MÉTIER                       │
│  ImportService | ImpayeService | ImpayeDetection     │
│  ClientService | NotificationService                 │
│  ExcelParserService | ExportService | AlerteService  │
└─────────────────────┬────────────────────────────────┘
                      │
┌─────────────────────▼────────────────────────────────┐
│              REPOSITORIES (Spring Data JPA)           │
│  ClientRepo | ImpayeRepo | SouscriptionRepo          │
│  StockMensuelRepo | ImportFichierRepo | ...           │
└─────────────────────┬────────────────────────────────┘
                      │
┌─────────────────────▼────────────────────────────────┐
│              BASE DE DONNÉES PostgreSQL               │
│              securicompte_db @ localhost:5432        │
└──────────────────────────────────────────────────────┘
```

### 4.3 Traitements asynchrones

Les opérations longues s'exécutent via `@Async` :
- Import de fichier Excel (peut traiter des milliers de lignes)
- Suppression d'un import (rollback des impayés associés)
- Import de fichier sinistres

### 4.4 Transactions

Utilisation de `TransactionTemplate` pour des imports multi-étapes avec atomicité partielle :
1. Créer l'enregistrement d'import (statut EN_COURS)
2. Parser et persister les données
3. Déclencher la détection d'impayés
4. Détecter les changements de prime (non-bloquant)

### 4.5 Performance

- **Bulk inserts** : taille de batch 500 lignes
- **JOIN FETCH** : évite les problèmes N+1
- **Indexes** : définis sur tous les champs de filtre/recherche
- **Set lookups** : construction d'historique optimisée (2 requêtes au lieu de 2N)
- **Cache clients** : map en mémoire lors de l'import pour éviter les requêtes unitaires

---

## 5. MODÈLE DE DONNÉES

### 5.1 Schéma des tables principales

```
CLIENT (1) ──< SOUSCRIPTION
CLIENT (1) ──< STOCK_MENSUEL
CLIENT (1) ──< IMPAYE
IMPAYE >── USER (regularisePar)
SOUSCRIPTION >── IMPORT_FICHIER
STOCK_MENSUEL >── IMPORT_FICHIER
IMPORT_FICHIER >── USER (importePar)
USER >── ROLE
```

### 5.2 Table CLIENT

| Colonne | Type | Contraintes | Description |
|---------|------|-------------|-------------|
| id | BIGINT | PK, auto-incrémenté | Identifiant interne |
| numero_client | VARCHAR(50) | UNIQUE, NOT NULL | Identifiant bancaire |
| nom | VARCHAR(200) | NOT NULL | Nom complet |
| date_naissance | DATE | | Date de naissance |
| compte | VARCHAR(100) | | Numéro de compte |
| zone_lib | VARCHAR(200) | | Libellé zone |
| agence_lib | VARCHAR(200) | | Libellé agence |
| gestionnaire | VARCHAR(200) | | Nom du gestionnaire |
| date_sinistre | DATE | | Date du sinistre (si applicable) |
| actif | BOOLEAN | DEFAULT true | Statut actif/inactif |
| created_at | TIMESTAMP | NOT NULL | Date de création |
| updated_at | TIMESTAMP | | Date de mise à jour |

**Indexes** : `idx_client_numero`, `idx_client_nom`, `idx_client_agence`, `idx_client_gestionnaire`

### 5.3 Table IMPAYE

| Colonne | Type | Contraintes | Description |
|---------|------|-------------|-------------|
| id | BIGINT | PK | Identifiant |
| client_id | BIGINT | FK → client | Client concerné |
| souscription_id | BIGINT | FK → souscription | Souscription liée (optionnel) |
| annee | INTEGER | NOT NULL | Année de l'impayé |
| mois | INTEGER | NOT NULL | Mois de l'impayé (1-12) |
| statut | VARCHAR(20) | NOT NULL | IMPAYE / REGULARISE / LITIGE |
| montant_du | DECIMAL(15,2) | | Montant impayé |
| date_detection | TIMESTAMP | | Date de détection automatique |
| date_regularisation | TIMESTAMP | | Date de régularisation |
| regularise_par_id | BIGINT | FK → users | Utilisateur ayant régularisé |
| commentaire | TEXT | | Commentaire libre |
| agence_lib | VARCHAR(200) | | Dénormalisé pour filtrage rapide |
| gestionnaire | VARCHAR(200) | | Dénormalisé pour filtrage rapide |
| zone_lib | VARCHAR(200) | | Dénormalisé pour filtrage rapide |
| created_at | TIMESTAMP | | |
| updated_at | TIMESTAMP | | |

**Contrainte unique** : `(client_id, annee, mois)` — 1 impayé par client par mois
**Indexes** : `idx_impaye_client`, `idx_impaye_annee_mois`, `idx_impaye_statut`, `idx_impaye_agence`, `idx_impaye_gestionnaire`

### 5.4 Table SOUSCRIPTION

| Colonne | Type | Contraintes | Description |
|---------|------|-------------|-------------|
| id | BIGINT | PK | Identifiant |
| client_id | BIGINT | FK → client | Client |
| securicompte | VARCHAR(100) | | Numéro SecuriCompte |
| commissions | DECIMAL(15,2) | | Montant des commissions |
| libel_package | VARCHAR(200) | | Libellé du package souscrit |
| option_securicompte | VARCHAR(200) | | Options souscrites |
| dat_souscription | DATE | NOT NULL | Date de signature |
| dat_ouverture | DATE | | Date d'ouverture du compte |
| type_souscription | VARCHAR(20) | | NOUVELLE / ANCIENNE |
| import_fichier_id | BIGINT | FK → import_fichier | Import source |
| created_at | TIMESTAMP | | |

### 5.5 Table STOCK_MENSUEL

| Colonne | Type | Contraintes | Description |
|---------|------|-------------|-------------|
| id | BIGINT | PK | Identifiant |
| client_id | BIGINT | FK → client | Client |
| annee | INTEGER | NOT NULL | Année |
| mois | INTEGER | NOT NULL | Mois |
| securicompte | VARCHAR(100) | | N° SecuriCompte ce mois |
| commissions | DECIMAL(15,2) | | Prime ce mois |
| libel_package | VARCHAR(200) | | |
| option_securicompte | VARCHAR(200) | | |
| dat_souscription | DATE | | |
| zone_lib | VARCHAR(200) | | |
| agence_lib | VARCHAR(200) | | |
| gestionnaire | VARCHAR(200) | | |
| import_fichier_id | BIGINT | FK → import_fichier | |
| created_at | TIMESTAMP | | |

**Contrainte unique** : `(client_id, annee, mois)` — présence unique par mois

### 5.6 Table IMPORT_FICHIER

| Colonne | Type | Contraintes | Description |
|---------|------|-------------|-------------|
| id | BIGINT | PK | Identifiant |
| nom_fichier | VARCHAR(255) | | Nom du fichier importé |
| annee | INTEGER | NOT NULL | Année de la période |
| mois | INTEGER | NOT NULL | Mois de la période |
| statut | VARCHAR(20) | | EN_COURS / SUCCES / ECHEC / PARTIEL |
| nb_nouvelles | INTEGER | DEFAULT 0 | Nouvelles souscriptions |
| nb_anciennes | INTEGER | DEFAULT 0 | Anciennes souscriptions |
| nb_stock | INTEGER | DEFAULT 0 | Entrées de stock |
| nb_erreurs | INTEGER | DEFAULT 0 | Nombre d'erreurs de parsing |
| message_erreur | TEXT | | Détail des erreurs |
| importe_par_id | BIGINT | FK → users | Utilisateur importateur |
| date_import | TIMESTAMP | | Début d'import |
| date_fin_import | TIMESTAMP | | Fin d'import |

**Contrainte unique** : `(annee, mois)` — 1 import par période

### 5.7 Table NOTIFICATION

| Colonne | Type | Description |
|---------|------|-------------|
| id | BIGINT | Identifiant |
| type | VARCHAR(50) | CHANGEMENT_PRIME / IMPAYE_ANCIEN |
| message | TEXT | Message affiché |
| client_id | BIGINT | Client concerné |
| client_nom | VARCHAR(200) | Nom dénormalisé |
| impaye_id | BIGINT | Impayé concerné |
| annee_impaye | INTEGER | Période concernée |
| mois_impaye | INTEGER | |
| securicompte_avant | VARCHAR(100) | Ancienne valeur |
| securicompte_apres | VARCHAR(100) | Nouvelle valeur |
| commissions_avant | DECIMAL(15,2) | |
| commissions_apres | DECIMAL(15,2) | |
| cree_par | VARCHAR(100) | Auteur |
| created_at | TIMESTAMP | |
| lu | BOOLEAN | DEFAULT false |
| lu_par | VARCHAR(100) | |
| date_lu | TIMESTAMP | |

### 5.8 Table SINISTRE_IMPORT

| Colonne | Type | Description |
|---------|------|-------------|
| id | BIGINT | Identifiant |
| nom_fichier | VARCHAR(255) | Nom du fichier |
| date_import | TIMESTAMP | Début |
| date_fin_import | TIMESTAMP | Fin |
| nb_sinistres | INTEGER | Enregistrements traités |
| nb_non_trouves | INTEGER | Clients non trouvés |
| nb_erreurs | INTEGER | Erreurs |
| statut | VARCHAR(20) | EN_COURS / SUCCES / ECHEC / PARTIEL |
| message_erreur | TEXT | Détail erreurs |
| importe_par | VARCHAR(100) | Utilisateur |

### 5.9 Versioning Flyway

| Version | Description |
|---------|-------------|
| V1 | Schéma initial (toutes tables, rôles, utilisateurs par défaut) |
| V2 | Réinitialisation des mots de passe (BCrypt) |
| V3 | Ajustement allocation séquences |
| V4 | Indexes de performance supplémentaires |
| V5 | Colonne `date_sinistre` sur client |
| V6 | Table notification |
| V7 | Table sinistre_import |

---

## 6. FONCTIONNALITÉS DÉTAILLÉES

### 6.1 Authentification

**Description** : Connexion via formulaire HTML (intranet). Pas de JWT actif en production.

**Comportement** :
- Formulaire `/login` avec username + password
- Redirection vers `/dashboard` après succès
- Redirection vers `/login?error=true` en cas d'échec
- Session HTTP (JSESSIONID), max 5 sessions simultanées par utilisateur
- Déconnexion : `/logout` → suppression cookie JSESSIONID

**Comptes par défaut** :
- `admin` / `admin123` (rôle ADMIN)
- `agent` / `admin123` (rôle AGENT)

---

### 6.2 Dashboard

**URL** : `GET /` ou `GET /dashboard`
**Accès** : Tous les utilisateurs authentifiés

**Données affichées** :

| Indicateur | Description |
|------------|-------------|
| Total clients | Nombre total de clients en base |
| Total impayés | Impayés au statut IMPAYE |
| Total régularisés | Impayés au statut REGULARISE |
| Taux de régularisation | (Régularisés / (Impayés + Régularisés)) × 100 |
| Clients avec impayés | Nombre de clients distincts en impayé |
| Imports réalisés | Nombre total de fichiers importés avec succès |
| Top 10 clients | Clients avec le plus d'impayés |
| Stats par mois | Graphique d'évolution mensuelle |
| Stats par agence | Répartition par agence |
| Derniers imports | 5 derniers fichiers importés |

---

### 6.3 Import de fichier Excel mensuel

**URL** : `GET/POST /import`
**Accès** : ADMIN, AGENT

#### 6.3.1 Format du fichier

Le fichier Excel doit contenir **3 feuilles** :

| Feuille | Contenu | Colonnes requises |
|---------|---------|-------------------|
| Nouvelle | Nouvelles souscriptions du mois | CLIENT, NOM, DATE_SOUSCRIPTION, SECURICOMPTE, COMMISSIONS, LIBEL_PACKAGE, OPTION, COMPTE, ZONE_LIB, AGENCE_LIB, GESTIONNAIRE |
| Ancienne | Souscriptions antérieures actives | (idem) |
| Stock | Clients ayant réglé la prime | CLIENT, NOM, SECURICOMPTE, COMMISSIONS, LIBEL_PACKAGE, OPTION, COMPTE, ZONE_LIB, AGENCE_LIB, GESTIONNAIRE |

**Formats supportés** : `.xlsx`, `.xls`, `.xlsb`
**Taille maximale** : 200 MB
**Formats de dates acceptés** : `dd/MM/yyyy`, `dd.MM.yyyy`, `dd-MM-yyyy`, numérique Excel, 2 chiffres (ex: "25" → 2025)

#### 6.3.2 Processus d'import

```
ETAPE 1 : Validation
  - Vérification extension fichier
  - Vérification présence des 3 feuilles
  - Lecture des en-têtes

ETAPE 2 : Traitement (asynchrone)
  - Créer enregistrement ImportFichier (statut EN_COURS)
  - Parser les 3 feuilles
  - Supprimer l'ancien stock du mois (si réimport)
  - Insérer souscriptions (NOUVELLE et ANCIENNE) en bulk
  - Insérer stock mensuel en bulk
  - Créer/mettre à jour les clients manquants

ETAPE 3 : Détection impayés
  - Algorithme de détection (cf. §10.1)
  - Création en masse des impayés manquants

ETAPE 4 : Analyse prime (non-bloquant)
  - Détecter changements de prime entre mois importé et stock actuel
  - Créer notification de synthèse si changements détectés

ETAPE 5 : Finalisation
  - Statut → SUCCES (ou PARTIEL si des erreurs non-bloquantes)
  - Renseigner compteurs (nouvelles, anciennes, stock, erreurs)
```

#### 6.3.3 Réimport

Un import peut être relancé pour une même période (annee/mois). L'ancien stock est effacé et les impayés recalculés.

#### 6.3.4 Suppression d'un import

- Accessible uniquement à l'ADMIN
- Supprime le stock et les souscriptions du mois
- Recalcule les impayés impactés
- Opération asynchrone avec transaction

---

### 6.4 Détection automatique des impayés

**Déclenchement** : Après chaque import de fichier ou via recalcul admin

**Algorithme** :
```
POUR le mois M de l'année A :
  1. Charger tous les clients ayant une souscription datée
     avant ou pendant le dernier jour de M
  2. Exclure les clients dont la date_sinistre <= dernier jour de M
  3. Charger l'ensemble des clients présents dans le stock de M
     → "clients payés"
  4. Clients (liste 1 - exclus) NON PRÉSENTS dans (liste payés)
     → IMPAYÉS
  5. Pour chaque impayé :
     - Si enregistrement absent en base : créer avec statut IMPAYE
     - Si déjà REGULARISE ou LITIGE : ne pas écraser
```

**Note** : Chaque mois est indépendant. La régularisation d'un mois n'impacte pas les autres.

---

### 6.5 Liste et filtrage des impayés

**URL** : `GET /impayes`
**Accès** : Tous les utilisateurs authentifiés

**Filtres disponibles** :

| Filtre | Type | Description |
|--------|------|-------------|
| annee | Integer | Année de l'impayé |
| mois | Integer | Mois (1-12) |
| agence | String | Libellé agence |
| gestionnaire | String | Nom du gestionnaire |
| statut | Enum | IMPAYE / REGULARISE / LITIGE |
| page | Integer | Page (défaut : 0) |
| size | Integer | Taille (défaut : 20) |

**Actions disponibles** :
- Régulariser un impayé (ADMIN, AGENT)
- Remarquer comme impayé (ADMIN, AGENT)
- Exporter en Excel
- Exporter en PDF

---

### 6.6 Régularisation d'un impayé

**URL** : `POST /impayes/{id}/regulariser`
**Accès** : ADMIN, AGENT

**Processus** :
1. Changer le statut de IMPAYE → REGULARISE
2. Enregistrer date_regularisation, utilisateur ayant régularisé, commentaire
3. Détecter un éventuel changement de prime (compare le stock du mois de l'impayé avec le stock du mois courant)
4. Si changement détecté → créer une notification individuelle

**Retour** : JSON `{statut, message, dateRegularisation, regularisePar}`

---

### 6.7 Fiche client

**URL** : `GET /clients/{id}`
**Accès** : Tous les utilisateurs authentifiés

**Données affichées** :

| Section | Contenu |
|---------|---------|
| Informations générales | Numéro client, nom, agence, gestionnaire, zone, sinistre |
| Souscriptions | Tableau des souscriptions (date, type, package, prime) |
| Stock mensuel | Historique de présence dans le stock |
| Historique paiements | Calendrier mois par mois (PAYÉ / IMPAYÉ / ABSENT) |
| Impayés actifs | Liste des impayés en cours |

**Actions** :
- Déclarer un sinistre (ADMIN, AGENT) : enregistre la date de sinistre → exclut le client des détections futures
- Annuler un sinistre : supprime la date → relance le recalcul des impayés depuis ce mois

---

### 6.8 Recherche clients

**URL** : `GET /clients/recherche?q={terme}&page={n}`
**Accès** : Tous les utilisateurs authentifiés

**Recherche** : Sur numéro client OU nom (ILIKE), paginée.

---

### 6.9 Import de sinistres

**URL** : `GET/POST /sinistre/import`
**Accès** : ADMIN, AGENT

**Format** : Fichier Excel avec colonnes `CLIENT` et `DATE_SINISTRE`
**Processus** :
1. Parser le fichier
2. Pour chaque ligne : rechercher le client par numéro
3. Mettre à jour `date_sinistre` sur le client
4. Recalculer les impayés impactés (à partir du mois du sinistre)
5. Stocker le résultat dans `sinistre_import`

**Résultat** : Compteurs (traités / non trouvés / erreurs)

---

### 6.10 Notifications

**URL API** :
- `GET /notifications/count` — Nombre de notifications non lues
- `GET /notifications/list` — Liste des notifications non lues (JSON)
- `POST /notifications/{id}/lire` — Marquer comme lue
- `POST /notifications/tout-lire` — Marquer toutes comme lues
- `GET /notifications/historique` — Page d'historique paginée

**Affichage** : Cloche Bootstrap dans la barre de navigation, badge rouge avec compteur, rafraîchissement automatique toutes les 60 secondes (AJAX).

#### Types de notifications

| Type | Déclencheur | Contenu |
|------|-------------|---------|
| CHANGEMENT_PRIME | Régularisation manuelle | Détail du changement (avant/après) pour 1 client |
| CHANGEMENT_PRIME (synthèse) | Import de fichier | Synthèse avec compteur + 5 exemples |
| IMPAYE_ANCIEN | Job quotidien 8h00 | Impayé de plus de 3 mois non régularisé |

**Anti-doublon** : 1 notification IMPAYE_ANCIEN maximum par impayé (vérification en base).

---

### 6.11 Exports

#### Export Excel

**URL** : `GET /impayes/export`
**Format** : `.xlsx`
**Colonnes** : Numéro client, Nom, Agence, Gestionnaire, Zone, Année, Mois, Statut, Montant, Date détection, Date régularisation, Régularisé par, Commentaire
**Style** : En-têtes gris, impayés en rouge, régularisés en vert
**Données** : Feuille principale + feuille résumé statistique

#### Export PDF

**URL** : `GET /impayes/export-pdf`
**Format** : `.pdf`
**Contenu** : Tableau avec les mêmes colonnes

---

### 6.12 Administration

**URL** : `/admin/**`
**Accès** : ADMIN uniquement

#### Gestion des utilisateurs

| Action | Endpoint |
|--------|----------|
| Liste | `GET /admin/utilisateurs` |
| Créer | `POST /admin/utilisateurs/creer` |
| Modifier | `POST /admin/utilisateurs/{id}/modifier` |
| Activer/désactiver | `POST /admin/utilisateurs/{id}/toggle` |
| Réinitialiser MDP | `POST /admin/utilisateurs/{id}/reinitialiser-mdp` |
| Supprimer | `POST /admin/utilisateurs/{id}/supprimer` |

**Champs utilisateur** : username, password (BCrypt), email, nomComplet, rôle, actif

#### Recalcul des impayés

**Endpoint** : `POST /admin/recalculer-impayes`
**Fonction** : Parcourt tous les mois importés avec succès et recalcule les impayés en appliquant l'algorithme complet. Utile après modification des données de souscription ou de sinistre.

---

## 7. GESTION DES DROITS ET SÉCURITÉ

### 7.1 Rôles

| Rôle | Code | Description |
|------|------|-------------|
| Administrateur | ROLE_ADMIN | Accès complet |
| Agent | ROLE_AGENT | Import, régularisation, sinistres |
| Consultation | ROLE_CONSULTATION | Lecture seule |

### 7.2 Matrice des droits

| Fonctionnalité | ADMIN | AGENT | CONSULTATION |
|----------------|-------|-------|--------------|
| Dashboard | ✓ | ✓ | ✓ |
| Liste impayés | ✓ | ✓ | ✓ |
| Recherche clients | ✓ | ✓ | ✓ |
| Fiche client | ✓ | ✓ | ✓ |
| Historique notifications | ✓ | ✓ | ✓ |
| Régulariser impayé | ✓ | ✓ | ✗ |
| Importer fichier | ✓ | ✓ | ✗ |
| Importer sinistres | ✓ | ✓ | ✗ |
| Déclarer sinistre client | ✓ | ✓ | ✗ |
| Exporter Excel/PDF | ✓ | ✓ | ✓ |
| Supprimer import | ✓ | ✗ | ✗ |
| Supprimer import sinistre | ✓ | ✗ | ✗ |
| Gestion utilisateurs | ✓ | ✗ | ✗ |
| Recalcul impayés | ✓ | ✗ | ✗ |
| Accès Actuator | ✓ | ✗ | ✗ |

### 7.3 Sécurité technique

- **Mots de passe** : BCrypt (cost 12)
- **CSRF** : Actif (désactivé uniquement sur `/api/**`)
- **Session** : Maximum 5 sessions actives par utilisateur
- **JWT** : Disponible mais désactivé (intranet)
- **Method Security** : `@PreAuthorize` sur les endpoints sensibles

---

## 8. INTERFACES ET INTÉGRATIONS

### 8.1 Interface utilisateur

**Framework CSS** : Bootstrap 5
**Style** : Thème sombre (sidebar noire), responsive
**Navigation** : Sidebar avec menu, topbar avec cloche notifications
**AJAX** : Appels REST pour notifications, compteurs en-cours, régularisation

### 8.2 Endpoints REST (usage interne)

| Méthode | URL | Description | Retour |
|---------|-----|-------------|--------|
| GET | `/notifications/count` | Nb non-lues | `{"count": N}` |
| GET | `/notifications/list` | Liste non-lues | `[{id, type, message, createdAt, ...}]` |
| POST | `/notifications/{id}/lire` | Marquer lue | `{"succes": true}` |
| POST | `/notifications/tout-lire` | Tout lire | `{"succes": true}` |
| GET | `/import/en-cours` | Nb imports actifs | `{"count": N}` |
| GET | `/sinistre/import/en-cours` | Nb sinistres actifs | `{"count": N}` |
| POST | `/impayes/{id}/regulariser` | Régularisation | `{statut, message, ...}` |
| POST | `/impayes/{id}/marquer-impaye` | Re-marquer impayé | `{statut, ...}` |

### 8.3 Monitoring (Actuator)

| Endpoint | Accès | Description |
|----------|-------|-------------|
| `/actuator/health` | Public | État de l'application |
| `/actuator/info` | ADMIN | Informations application |
| `/actuator/metrics` | ADMIN | Métriques (JVM, DB, etc.) |

---

## 9. EXIGENCES NON-FONCTIONNELLES

### 9.1 Performance

| Critère | Cible |
|---------|-------|
| Import fichier (10 000 lignes) | < 30 secondes |
| Chargement liste impayés (paginée) | < 2 secondes |
| Chargement fiche client | < 2 secondes |
| Export Excel (5 000 lignes) | < 10 secondes |
| Réponse dashboard | < 3 secondes |

### 9.2 Disponibilité

- Application intranet : disponibilité en heures ouvrées (8h-18h)
- Maintenance possible la nuit et le week-end
- Pas d'exigence haute disponibilité (pas de cluster)

### 9.3 Scalabilité

- Base de données : pool Hikari, min 3 / max 10 connexions
- Batch size : 500 inserts simultanés
- Pas de scalabilité horizontale requise

### 9.4 Sécurité

- Application intranet uniquement (pas exposée sur Internet)
- Mots de passe hashés (BCrypt)
- Pas de données en clair dans les logs
- Sessions limitées à 5 par utilisateur

### 9.5 Compatibilité

- Navigateurs : Chrome, Edge (dernières versions)
- Résolution : min 1366×768
- Formats fichiers : .xlsx, .xls, .xlsb

---

## 10. CONTRAINTES ET RÈGLES MÉTIER

### 10.1 Règles de détection des impayés

1. Un client est éligible à la détection si sa souscription est datée **avant ou au dernier jour du mois traité**
2. Un client est **exclu** de la détection si `date_sinistre <= dernier jour du mois`
3. Un client est **payé** s'il figure dans le stock mensuel du mois traité
4. Un client est **impayé** s'il est éligible mais absent du stock
5. Si un impayé existe déjà avec statut REGULARISE ou LITIGE, il **n'est pas réinitialisé**
6. **Chaque mois est indépendant** : la régularisation d'un mois n'impacte pas les autres mois

### 10.2 Règles d'import

1. Un seul import actif par période (annee/mois)
2. Un réimport supprime le stock précédent et recalcule les impayés
3. La colonne `CLIENT` est obligatoire dans le fichier (colonne `NOM` seule non acceptée)
4. Les dates de souscription à 2 chiffres sont automatiquement converties en 4 chiffres (25 → 2025)
5. Les dates numériques Excel (format sériel) sont correctement converties

### 10.3 Règles des sinistres

1. Un sinistre est lié à une date : `date_sinistre`
2. Tout impayé dont le mois est **postérieur ou égal au mois du sinistre** doit être recalculé
3. Les impayés **antérieurs** au sinistre sont conservés
4. L'annulation d'un sinistre déclenche un recalcul automatique

### 10.4 Règles des notifications

1. Maximum 1 notification IMPAYE_ANCIEN par impayé (anti-doublon)
2. La notification de changement de prime à l'import est une **synthèse** (pas une par client)
3. Le seuil d'alerte impayé ancien est configurable : `app.alertes.seuil-impayes-mois=3`

### 10.5 Règles des statuts impayé

| Transition | Autorisé | Déclencheur |
|------------|----------|-------------|
| IMPAYE → REGULARISE | Oui | Manuel (ADMIN/AGENT) |
| IMPAYE → LITIGE | Oui | Manuel (ADMIN/AGENT) |
| REGULARISE → IMPAYE | Oui | Manuel (re-marquer) |
| LITIGE → REGULARISE | Oui | Manuel |
| LITIGE → IMPAYE | Oui | Manuel |
| Tout statut → IMPAYE | Non (réimport) | Recalcul ne touche pas REGULARISE/LITIGE |

---

## 11. GESTION DES ERREURS ET JOURNALISATION

### 11.1 Erreurs d'import

- Les erreurs **non-bloquantes** (lignes malformées) sont comptées et stockées dans `message_erreur`
- Une erreur bloquante fait passer le statut à ECHEC
- Des erreurs partielles donnent le statut PARTIEL
- Le détail des erreurs est visible dans la page d'historique d'import

### 11.2 Journalisation (SLF4J / Logback)

- Niveau INFO : démarrage application, imports réussis, regularisations
- Niveau WARN : données manquantes, clients non trouvés
- Niveau ERROR : exceptions non gérées, erreurs BD

### 11.3 Spring Actuator

- `/actuator/health` : état datasource, état applicatif
- Détails exposés aux utilisateurs authentifiés uniquement

---

## 12. GLOSSAIRE

| Terme | Définition |
|-------|------------|
| SecuriCompte | Produit bancaire de protection de compte (assurance) |
| Impayé | Situation d'un client dont la prime mensuelle n'a pas été prélevée |
| Régularisation | Action de marquer un impayé comme résolu/payé |
| Stock mensuel | Ensemble des clients ayant réglé leur prime sur un mois donné |
| Souscription | Contrat client pour le produit SecuriCompte |
| Sinistre | Événement déclaré sur un client l'excluant de la détection d'impayés |
| Litige | Statut intermédiaire pour un impayé contesté |
| Feuille Nouvelle | Onglet Excel des nouvelles souscriptions du mois |
| Feuille Ancienne | Onglet Excel des souscriptions antérieures actives |
| Feuille Stock | Onglet Excel des clients ayant réglé la prime |
| Taux de régularisation | Pourcentage d'impayés régularisés par rapport au total |
| Changement de prime | Différence de tarif SecuriCompte/commissions entre deux périodes |
| Import partiel | Import avec des erreurs non-bloquantes (statut PARTIEL) |
| Recalcul | Réexécution de l'algorithme de détection sur tous les mois importés |

---

*Document généré le 01/04/2026 — SecuriCompte v9*
