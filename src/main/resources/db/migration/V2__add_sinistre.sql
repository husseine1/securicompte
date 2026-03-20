-- ============================================================
-- MIGRATION V2 : Gestion du sinistre client
-- ============================================================

ALTER TABLE client ADD COLUMN IF NOT EXISTS date_sinistre DATE;

CREATE INDEX IF NOT EXISTS idx_client_sinistre ON client(date_sinistre)
    WHERE date_sinistre IS NOT NULL;
