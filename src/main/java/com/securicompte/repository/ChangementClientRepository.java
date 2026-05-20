package com.securicompte.repository;

import com.securicompte.entity.ChangementClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChangementClientRepository extends JpaRepository<ChangementClient, Long> {

    @Query("SELECT c FROM ChangementClient c JOIN FETCH c.client WHERE c.annee = :annee AND c.mois = :mois ORDER BY c.client.nom, c.champ")
    List<ChangementClient> findByAnneeAndMoisWithClient(@Param("annee") int annee, @Param("mois") int mois);

    long countByAnneeAndMois(int annee, int mois);

    @Query("SELECT c.annee, c.mois, COUNT(c) FROM ChangementClient c GROUP BY c.annee, c.mois")
    List<Object[]> countGroupedByMois();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ChangementClient c WHERE c.annee = :annee AND c.mois = :mois")
    void deleteByAnneeAndMois(@Param("annee") int annee, @Param("mois") int mois);
}
