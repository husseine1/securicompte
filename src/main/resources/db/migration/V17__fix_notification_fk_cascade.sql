-- Correction FK notification.impaye_id et notification.client_id :
-- ON DELETE SET NULL pour éviter une violation de contrainte lors de la suppression d'un impayé ou d'un client

ALTER TABLE notification
    DROP CONSTRAINT IF EXISTS notification_impaye_id_fkey,
    DROP CONSTRAINT IF EXISTS notification_client_id_fkey;

ALTER TABLE notification
    ADD CONSTRAINT notification_impaye_id_fkey
        FOREIGN KEY (impaye_id) REFERENCES impaye(id) ON DELETE SET NULL,
    ADD CONSTRAINT notification_client_id_fkey
        FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE SET NULL;
