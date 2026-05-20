package com.securicompte.repository;

import com.securicompte.entity.ChangementPrime;
import com.securicompte.enums.StatutChangement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChangementPrimeRepository extends JpaRepository<ChangementPrime, Long> {

    @Query("SELECT c FROM ChangementPrime c JOIN FETCH c.client WHERE c.annee = :annee AND c.mois = :mois ORDER BY c.client.nom")
    List<ChangementPrime> findByAnneeAndMoisWithClient(@Param("annee") int annee, @Param("mois") int mois);

    @Query("SELECT c FROM ChangementPrime c JOIN FETCH c.client WHERE c.statut = :statut ORDER BY c.annee DESC, c.mois DESC, c.client.nom")
    List<ChangementPrime> findByStatutWithClient(@Param("statut") StatutChangement statut);

    @Query("SELECT c FROM ChangementPrime c JOIN FETCH c.client WHERE c.annee = :annee AND c.mois = :mois AND c.statut = :statut ORDER BY c.client.nom")
    List<ChangementPrime> findByAnneeAndMoisAndStatutWithClient(@Param("annee") int annee, @Param("mois") int mois, @Param("statut") StatutChangement statut);

    long countByAnneeAndMoisAndStatut(int annee, int mois, StatutChangement statut);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ChangementPrime c WHERE c.annee = :annee AND c.mois = :mois")
    void deleteByAnneeAndMois(@Param("annee") int annee, @Param("mois") int mois);
}
