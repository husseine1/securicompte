-- ============================================================
-- MIGRATION V1 : Schéma initial Securicompte
-- ============================================================

-- Rôles
CREATE TABLE IF NOT EXISTS role (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(200)
);

-- Utilisateurs
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    email       VARCHAR(150) UNIQUE,
    nom_complet VARCHAR(200),
    role_id     BIGINT REFERENCES role(id),
    actif       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- Clients
CREATE TABLE IF NOT EXISTS client (
    id              BIGSERIAL PRIMARY KEY,
    numero_client   VARCHAR(50)  NOT NULL UNIQUE,
    nom             VARCHAR(200),
    date_naissance  DATE,
    compte          VARCHAR(100),
    zone_lib        VARCHAR(200),
    agence_lib      VARCHAR(200),
    gestionnaire    VARCHAR(200),
    actif           BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Fichiers importés
CREATE TABLE IF NOT EXISTS import_fichier (
    id              BIGSERIAL PRIMARY KEY,
    nom_fichier     VARCHAR(255) NOT NULL,
    annee           INTEGER NOT NULL,
    mois            INTEGER NOT NULL,
    statut          VARCHAR(50)  NOT NULL DEFAULT 'EN_COURS',
    nb_nouvelles    INTEGER DEFAULT 0,
    nb_anciennes    INTEGER DEFAULT 0,
    nb_stock        INTEGER DEFAULT 0,
    nb_erreurs      INTEGER DEFAULT 0,
    message_erreur  TEXT,
    importe_par     BIGINT REFERENCES users(id),
    date_import     TIMESTAMP DEFAULT NOW(),
    date_fin_import TIMESTAMP,
    CONSTRAINT uq_import_mois UNIQUE (annee, mois)
);

-- Souscriptions (nouvelles + anciennes)
CREATE TABLE IF NOT EXISTS souscription (
    id                  BIGSERIAL PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    securicompte        VARCHAR(100),
    commissions         DECIMAL(15,2),
    libel_package       VARCHAR(200),
    option_securicompte VARCHAR(200),
    dat_souscription    DATE NOT NULL,
    dat_ouverture       DATE,
    type_souscription   VARCHAR(50) NOT NULL,
    import_fichier_id   BIGINT REFERENCES import_fichier(id),
    created_at          TIMESTAMP DEFAULT NOW()
);

-- Stock mensuel (présence client par mois)
CREATE TABLE IF NOT EXISTS stock_mensuel (
    id                  BIGSERIAL PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    annee               INTEGER NOT NULL,
    mois                INTEGER NOT NULL,
    securicompte        VARCHAR(100),
    commissions         DECIMAL(15,2),
    libel_package       VARCHAR(200),
    option_securicompte VARCHAR(200),
    dat_souscription    DATE,
    zone_lib            VARCHAR(200),
    agence_lib          VARCHAR(200),
    gestionnaire        VARCHAR(200),
    import_fichier_id   BIGINT REFERENCES import_fichier(id),
    created_at          TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_stock_client_mois UNIQUE (client_id, annee, mois)
);

-- Impayés détectés
CREATE TABLE IF NOT EXISTS impaye (
    id                  BIGSERIAL PRIMARY KEY,
    client_id           BIGINT NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    souscription_id     BIGINT REFERENCES souscription(id),
    annee               INTEGER NOT NULL,
    mois                INTEGER NOT NULL,
    statut              VARCHAR(20) NOT NULL DEFAULT 'IMPAYE',
    montant_du          DECIMAL(15,2),
    date_detection      TIMESTAMP DEFAULT NOW(),
    date_regularisation TIMESTAMP,
    regularise_par      BIGINT REFERENCES users(id),
    commentaire         TEXT,
    agence_lib          VARCHAR(200),
    gestionnaire        VARCHAR(200),
    zone_lib            VARCHAR(200),
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_impaye_client_mois UNIQUE (client_id, annee, mois)
);

-- ── Index de performance ──────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_client_numero       ON client(numero_client);
CREATE INDEX IF NOT EXISTS idx_client_nom          ON client(LOWER(nom));
CREATE INDEX IF NOT EXISTS idx_client_agence       ON client(agence_lib);
CREATE INDEX IF NOT EXISTS idx_client_gestionnaire ON client(gestionnaire);

CREATE INDEX IF NOT EXISTS idx_souscription_client ON souscription(client_id);
CREATE INDEX IF NOT EXISTS idx_souscription_date   ON souscription(dat_souscription);
CREATE INDEX IF NOT EXISTS idx_souscription_type   ON souscription(type_souscription);

CREATE INDEX IF NOT EXISTS idx_stock_client        ON stock_mensuel(client_id);
CREATE INDEX IF NOT EXISTS idx_stock_annee_mois    ON stock_mensuel(annee, mois);
CREATE INDEX IF NOT EXISTS idx_stock_agence        ON stock_mensuel(agence_lib);
CREATE INDEX IF NOT EXISTS idx_stock_gestionnaire  ON stock_mensuel(gestionnaire);

CREATE INDEX IF NOT EXISTS idx_impaye_client        ON impaye(client_id);
CREATE INDEX IF NOT EXISTS idx_impaye_annee_mois    ON impaye(annee, mois);
CREATE INDEX IF NOT EXISTS idx_impaye_statut        ON impaye(statut);
CREATE INDEX IF NOT EXISTS idx_impaye_agence        ON impaye(agence_lib);
CREATE INDEX IF NOT EXISTS idx_impaye_gestionnaire  ON impaye(gestionnaire);

CREATE INDEX IF NOT EXISTS idx_import_annee_mois   ON import_fichier(annee, mois);
CREATE INDEX IF NOT EXISTS idx_import_statut        ON import_fichier(statut);

-- ── Données initiales ─────────────────────────────────────────

INSERT INTO role (name, description) VALUES
    ('ROLE_ADMIN',        'Administrateur système — accès complet'),
    ('ROLE_AGENT',        'Agent — import et consultation'),
    ('ROLE_CONSULTATION', 'Consultation uniquement — lecture seule')
ON CONFLICT (name) DO NOTHING;

-- Admin par défaut (password: admin123 — à changer en production)
INSERT INTO users (username, password, email, nom_complet, role_id, actif)
SELECT 'admin',
       '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/VK.vYHqKm',
       'admin@securicompte.com',
       'Administrateur Système',
       r.id,
       true
FROM role r WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT (username) DO NOTHING;

-- Agent par défaut (password: agent123)
INSERT INTO users (username, password, email, nom_complet, role_id, actif)
SELECT 'agent',
       '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/VK.vYHqKm',
       'agent@securicompte.com',
       'Agent Import',
       r.id,
       true
FROM role r WHERE r.name = 'ROLE_AGENT'
ON CONFLICT (username) DO NOTHING;
