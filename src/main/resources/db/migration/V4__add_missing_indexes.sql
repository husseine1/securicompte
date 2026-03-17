-- ============================================================
-- MIGRATION V4 : Index manquants pour performances
-- ============================================================

-- Index critique pour DELETE par import_fichier_id (suppression et réimport)
CREATE INDEX IF NOT EXISTS idx_souscription_import_fichier ON souscription(import_fichier_id);

-- Index pour accélérer les DELETE bulk sur impaye et stock
CREATE INDEX IF NOT EXISTS idx_stock_import_fichier ON stock_mensuel(import_fichier_id);
