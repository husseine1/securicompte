-- ===================================================
-- V6 : Ajout table notifications (changement de prime)
-- ===================================================

CREATE TABLE notification (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(50)     NOT NULL DEFAULT 'CHANGEMENT_PRIME',
    message         TEXT            NOT NULL,
    client_id       BIGINT          REFERENCES client(id),
    client_nom      VARCHAR(200),
    impaye_id       BIGINT          REFERENCES impaye(id),
    annee_impaye    INTEGER,
    mois_impaye     INTEGER,
    securicompte_avant  VARCHAR(100),
    securicompte_apres  VARCHAR(100),
    commissions_avant   NUMERIC(15, 2),
    commissions_apres   NUMERIC(15, 2),
    cree_par        VARCHAR(100),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    lu              BOOLEAN         NOT NULL DEFAULT FALSE,
    lu_par          VARCHAR(100),
    date_lu         TIMESTAMP
);

CREATE INDEX idx_notification_lu         ON notification(lu);
CREATE INDEX idx_notification_created_at ON notification(created_at DESC);
CREATE INDEX idx_notification_client     ON notification(client_id);
