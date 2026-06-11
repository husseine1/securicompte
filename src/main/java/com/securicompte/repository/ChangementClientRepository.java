package com.securicompte.repository;

import com.securicompte.entity.ChangementClient;
import com.securicompte.enums.Reseau;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT c FROM ChangementClient c JOIN FETCH c.client ORDER BY c.annee DESC, c.mois DESC, c.client.nom, c.champ")
    List<ChangementClient> findAllWithClient();

    @Query("SELECT c FROM ChangementClient c JOIN FETCH c.client WHERE (c.annee * 100 + c.mois) >= :minPeriode ORDER BY c.annee DESC, c.mois DESC, c.client.nom, c.champ")
    List<ChangementClient> findRecentWithClient(@Param("minPeriode") int minPeriode);

    @Query("""
        SELECT c FROM ChangementClient c JOIN FETCH c.client cl
        WHERE (:recherche IS NULL OR LOWER(cl.nom) LIKE :recherche OR LOWER(cl.numeroClient) LIKE :recherche)
          AND (:annee IS NULL OR c.annee = :annee)
          AND (:mois IS NULL OR c.mois = :mois)
          AND (:champ IS NULL OR c.champ = :champ)
          AND (:reseau IS NULL OR cl.reseau = :reseau)
        ORDER BY c.annee DESC, c.mois DESC, cl.nom, c.champ
        """)
    Page<ChangementClient> searchWithFilters(
        @Param("recherche") String recherche,
        @Param("annee") Integer annee,
        @Param("mois") Integer mois,
        @Param("champ") String champ,
        @Param("reseau") Reseau reseau,
        Pageable pageable
    );

    @Query("SELECT c FROM ChangementClient c JOIN FETCH c.client WHERE c.client.id = :clientId ORDER BY c.annee DESC, c.mois DESC, c.champ")
    List<ChangementClient> findByClientId(@Param("clientId") Long clientId);

    @Query("SELECT DISTINCT c.annee FROM ChangementClient c ORDER BY c.annee DESC")
    List<Integer> findDistinctAnnees();

    long countByAnneeAndMois(int annee, int mois);

    @Query("SELECT c.annee, c.mois, COUNT(c) FROM ChangementClient c GROUP BY c.annee, c.mois")
    List<Object[]> countGroupedByMois();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ChangementClient c WHERE c.annee = :annee AND c.mois = :mois")
    void deleteByAnneeAndMois(@Param("annee") int annee, @Param("mois") int mois);
}
