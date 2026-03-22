package com.securicompte.repository;

import com.securicompte.entity.StockMensuel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockMensuelRepository extends JpaRepository<StockMensuel, Long> {
    Optional<StockMensuel> findByClientIdAndAnneeAndMois(Long clientId, Integer annee, Integer mois);
    boolean existsByClientIdAndAnneeAndMois(Long clientId, Integer annee, Integer mois);
    List<StockMensuel> findByAnneeAndMois(Integer annee, Integer mois);
    List<StockMensuel> findByClientIdOrderByAnneeDescMoisDesc(Long clientId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM StockMensuel s WHERE s.annee = :annee AND s.mois = :mois")
    void deleteBulkByAnneeAndMois(@Param("annee") Integer annee, @Param("mois") Integer mois);

    @Query("SELECT DISTINCT s.client.id FROM StockMensuel s WHERE s.annee = :annee AND s.mois = :mois")
    List<Long> findClientIdsPresentsDansMois(@Param("annee") Integer annee, @Param("mois") Integer mois);

    @Query("SELECT DISTINCT s.annee, s.mois FROM StockMensuel s ORDER BY s.annee ASC, s.mois ASC")
    List<Object[]> findAllDistinctAnneesMois();

    /**
     * Charge le stock d'un mois avec le client déjà joint (évite le N+1).
     * Utilisé pour la détection de changement de prime lors de l'import.
     */
    @Query("SELECT s FROM StockMensuel s JOIN FETCH s.client WHERE s.annee = :annee AND s.mois = :mois")
    List<StockMensuel> findByAnneeAndMoisWithClient(@Param("annee") Integer annee, @Param("mois") Integer mois);
}
