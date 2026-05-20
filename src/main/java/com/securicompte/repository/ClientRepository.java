package com.securicompte.repository;

import com.securicompte.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByNumeroClient(String numeroClient);
    boolean existsByNumeroClient(String numeroClient);
    List<Client> findByNumeroClientIn(Collection<String> numeroClients);
    List<Client> findByNomIn(Collection<String> noms);

    @Query("""
        SELECT c FROM Client c
        WHERE (:q = '' OR LOWER(c.numeroClient) LIKE LOWER(CONCAT('%',:q,'%'))
                       OR LOWER(c.nom)          LIKE LOWER(CONCAT('%',:q,'%')))
          AND (:agence = '' OR c.agenceLib = :agence)
          AND (:gestionnaire = '' OR c.gestionnaire = :gestionnaire)
          AND (:sinistre = false OR c.dateSinistre IS NOT NULL)
          AND (:compteFerme = false OR c.dateCompteFerme IS NOT NULL)
          AND (:annee = 0 OR EXISTS (
                SELECT 1 FROM Impaye i WHERE i.client = c
                  AND i.annee = :annee
                  AND (:mois = 0 OR i.mois = :mois)))
        """)
    Page<Client> rechercherClients(@Param("q") String q,
                                   @Param("agence") String agence,
                                   @Param("gestionnaire") String gestionnaire,
                                   @Param("sinistre") boolean sinistre,
                                   @Param("compteFerme") boolean compteFerme,
                                   @Param("annee") int annee,
                                   @Param("mois") int mois,
                                   Pageable pageable);

    @Query("SELECT DISTINCT c.agenceLib FROM Client c WHERE c.agenceLib IS NOT NULL ORDER BY c.agenceLib")
    List<String> findDistinctAgences();

    @Query("SELECT DISTINCT c.gestionnaire FROM Client c WHERE c.gestionnaire IS NOT NULL ORDER BY c.gestionnaire")
    List<String> findDistinctGestionnaires();

    @Query("SELECT c.id FROM Client c WHERE c.dateSinistre IS NOT NULL AND c.dateSinistre <= :date")
    List<Long> findClientIdsWithSinistreInOrBefore(@Param("date") LocalDate date);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        DELETE FROM Client c
        WHERE NOT EXISTS (SELECT 1 FROM Souscription s WHERE s.client = c)
          AND NOT EXISTS (SELECT 1 FROM StockMensuel sm WHERE sm.client = c)
          AND NOT EXISTS (SELECT 1 FROM Impaye i WHERE i.client = c)
        """)
    int deleteOrphanClients();
}
