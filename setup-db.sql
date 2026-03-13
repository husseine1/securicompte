-- ===================================================
-- SCRIPT DE SETUP POSTGRESQL POUR SECURICOMPTE
-- ===================================================

-- Créer la base de données
CREATE DATABASE securicompte_db
    ENCODING = 'UTF8'
    LC_COLLATE = 'C'
    LC_CTYPE = 'C'
    TEMPLATE = template0;

-- Créer l'utilisateur
CREATE USER securicompte_user WITH PASSWORD 'securicompte_pass';

-- Donner les permissions
GRANT ALL PRIVILEGES ON DATABASE securicompte_db TO securicompte_user;

-- Connexion à la base de données
\c securicompte_db

-- Donner les permissions du schéma public
GRANT ALL ON SCHEMA public TO securicompte_user;

-- Message de confirmation
\echo 'Base de données et utilisateur créés avec succès!'
\echo 'Database: securicompte_db'
\echo 'User: securicompte_user'
\echo 'Password: securicompte_pass'
