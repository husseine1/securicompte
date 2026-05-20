-- Changements de prime détectés à l'import (avec workflow approbation/refus)
CREATE TABLE changement_prime (
    id               BIGSERIAL PRIMARY KEY,
    client_id        BIGINT        NOT NULL REFERENCES client(id),
    annee            INTEGER       NOT NULL,
    mois             INTEGER       NOT NULL,
    securicompte_avant   VARCHAR(100),
    securicompte_apres   VARCHAR(100),
    commissions_avant    NUMERIC(15,2),
    commissions_apres    NUMERIC(15,2),
    dat_souscription DATE,
    statut           VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    date_detection   TIMESTAMP     NOT NULL,
    date_decision    TIMESTAMP,
    decide_par       VARCHAR(100),
    UNIQUE (client_id, annee, mois)
);

CREATE INDEX idx_changement_prime_statut  ON changement_prime(statut);
CREATE INDEX idx_changement_prime_periode ON changement_prime(annee, mois);
CREATE INDEX idx_changement_prime_client  ON changement_prime(client_id);

-- Stockage des fichiers Excel (table séparée pour ne pas charger les bytes à chaque listage)
CREATE TABLE import_fichier_bytes (
    id             BIGINT  PRIMARY KEY REFERENCES import_fichier(id) ON DELETE CASCADE,
    fichier_bytes  BYTEA   NOT NULL,
    taille_octets  BIGINT
);
