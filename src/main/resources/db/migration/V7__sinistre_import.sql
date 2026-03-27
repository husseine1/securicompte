-- ===================================================
-- V7 : Table pour l'historique des imports sinistres
-- ===================================================

CREATE TABLE sinistre_import (
    id              BIGSERIAL       PRIMARY KEY,
    nom_fichier     VARCHAR(255)    NOT NULL,
    date_import     TIMESTAMP       NOT NULL DEFAULT NOW(),
    date_fin_import TIMESTAMP,
    nb_sinistres    INTEGER         NOT NULL DEFAULT 0,
    nb_non_trouves  INTEGER         NOT NULL DEFAULT 0,
    nb_erreurs      INTEGER         NOT NULL DEFAULT 0,
    statut          VARCHAR(20)     NOT NULL DEFAULT 'EN_COURS',
    message_erreur  TEXT,
    importe_par     VARCHAR(100)
);

CREATE INDEX idx_sinistre_import_statut ON sinistre_import(statut);
CREATE INDEX idx_sinistre_import_date   ON sinistre_import(date_import DESC);
