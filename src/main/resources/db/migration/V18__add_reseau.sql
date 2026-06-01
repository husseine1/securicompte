-- ============================================================
-- MIGRATION V18 : Support multi-réseau (BNI / SIB)
-- ============================================================

-- Ajout colonne réseau sur client
ALTER TABLE client ADD COLUMN IF NOT EXISTS reseau VARCHAR(10) NOT NULL DEFAULT 'BNI';

-- Remplacement de la contrainte unique numero_client → (reseau, numero_client)
ALTER TABLE client DROP CONSTRAINT IF EXISTS client_numero_client_key;
ALTER TABLE client ADD CONSTRAINT uq_client_reseau_numero UNIQUE (reseau, numero_client);

-- Ajout colonne réseau sur import_fichier
ALTER TABLE import_fichier ADD COLUMN IF NOT EXISTS reseau VARCHAR(10) NOT NULL DEFAULT 'BNI';

-- Remplacement de la contrainte unique (annee, mois) → (reseau, annee, mois)
ALTER TABLE import_fichier DROP CONSTRAINT IF EXISTS uq_import_mois;
ALTER TABLE import_fichier ADD CONSTRAINT uq_import_reseau_mois UNIQUE (reseau, annee, mois);
