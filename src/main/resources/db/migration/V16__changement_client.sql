CREATE TABLE changement_client (
    id              BIGSERIAL PRIMARY KEY,
    client_id       BIGINT NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    annee           INTEGER NOT NULL,
    mois            INTEGER NOT NULL,
    champ           VARCHAR(50) NOT NULL,
    valeur_avant    TEXT,
    valeur_apres    TEXT,
    statut          VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    date_detection  TIMESTAMP NOT NULL,
    date_decision   TIMESTAMP,
    decide_par      VARCHAR(100),
    UNIQUE (client_id, annee, mois, champ)
);

CREATE INDEX idx_changement_client_periode ON changement_client(annee, mois);
CREATE INDEX idx_changement_client_statut  ON changement_client(statut);
CREATE INDEX idx_changement_client_client  ON changement_client(client_id);
