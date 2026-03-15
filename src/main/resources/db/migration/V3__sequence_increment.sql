-- Augmenter l'incrément des séquences pour activer le batching Hibernate (allocationSize=500)
ALTER SEQUENCE client_id_seq INCREMENT BY 500;
ALTER SEQUENCE souscription_id_seq INCREMENT BY 500;
ALTER SEQUENCE stock_mensuel_id_seq INCREMENT BY 500;
ALTER SEQUENCE impaye_id_seq INCREMENT BY 500;
