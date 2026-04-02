-- ================================================================
-- V9 : Fermeture de compte client
-- ================================================================

ALTER TABLE client
    ADD COLUMN IF NOT EXISTS date_compte_ferme DATE;

CREATE INDEX IF NOT EXISTS idx_client_compte_ferme
    ON client(date_compte_ferme)
    WHERE date_compte_ferme IS NOT NULL;

CREATE TABLE IF NOT EXISTS compte_ferme_import (
    id              BIGSERIAL       PRIMARY KEY,
    nom_fichier     VARCHAR(255)    NOT NULL,
    date_import     TIMESTAMP       NOT NULL DEFAULT NOW(),
    date_fin_import TIMESTAMP,
    nb_fermes       INTEGER         NOT NULL DEFAULT 0,
    nb_non_trouves  INTEGER         NOT NULL DEFAULT 0,
    nb_erreurs      INTEGER         NOT NULL DEFAULT 0,
    statut          VARCHAR(20)     NOT NULL DEFAULT 'EN_COURS',
    message_erreur  TEXT,
    importe_par     VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_compte_ferme_import_statut ON compte_ferme_import(statut);
CREATE INDEX IF NOT EXISTS idx_compte_ferme_import_date   ON compte_ferme_import(date_import DESC);
