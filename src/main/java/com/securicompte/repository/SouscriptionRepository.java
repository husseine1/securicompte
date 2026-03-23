package com.securicompte.repository;

import com.securicompte.entity.Souscription;
import com.securicompte.enums.TypeSouscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SouscriptionRepository extends JpaRepository<Souscription, Long> {
    List<Souscription> findByClientId(Long clientId);
    List<Souscription> findByClientIdOrderByDatSouscriptionAsc(Long clientId);
    boolean existsByClientIdAndDatSouscriptionAndTypeSouscription(
        Long clientId, LocalDate datSouscription, TypeSouscription type);

    @Query("SELECT CONCAT(CAST(s.client.id AS string), '_', CAST(s.datSouscription AS string), '_', s.typeSouscription) FROM Souscription s WHERE s.typeSouscription = :type AND s.client.id IN :clientIds")
    List<String> findExistingKeysForClients(@Param("type") TypeSouscription type, @Param("clientIds") java.util.Collection<Long> clientIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Souscription s WHERE s.importFichier.id = :importFichierId")
    void deleteByImportFichierId(@Param("importFichierId") Long importFichierId);

    @Query("SELECT DISTINCT s.client.id FROM Souscription s WHERE s.datSouscription <= :date")
    List<Long> findClientIdsWithSouscriptionBefore(@Param("date") LocalDate date);

    /**
     * Charge toutes les souscriptions pour un ensemble de clients (avec client en JOIN FETCH).
     * Utilisé pour la détection de changement de prime lors de l'import.
     */
    @Query("SELECT s FROM Souscription s JOIN FETCH s.client c WHERE c.id IN :clientIds ORDER BY s.datSouscription DESC")
    List<Souscription> findAllByClientIdsOrderByDateDesc(@Param("clientIds") List<Long> clientIds);
}
