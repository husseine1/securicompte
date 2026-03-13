package com.securicompte.repository;

import com.securicompte.entity.Impaye;
import com.securicompte.enums.StatutImpaye;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImpayeRepository extends JpaRepository<Impaye, Long> {

    Optional<Impaye> findByClientIdAndAnneeAndMois(Long clientId, Integer annee, Integer mois);

    boolean existsByClientIdAndAnneeAndMois(Long clientId, Integer annee, Integer mois);

    List<Impaye> findByClientIdOrderByAnneeDescMoisDesc(Long clientId);

    @Query("""
        SELECT i FROM Impaye i JOIN i.client c
        WHERE (:annee IS NULL OR i.annee = :annee)
          AND (:mois  IS NULL OR i.mois  = :mois)
          AND (:agence IS NULL OR c.agenceLib = :agence)
          AND (:gestionnaire IS NULL OR c.gestionnaire = :gestionnaire)
          AND (:statut IS NULL OR i.statut = :statut)
        ORDER BY i.annee DESC, i.mois DESC, c.nom ASC
        """)
    Page<Impaye> findByFilters(
        @Param("annee") Integer annee,
        @Param("mois")  Integer mois,
        @Param("agence") String agence,
        @Param("gestionnaire") String gestionnaire,
        @Param("statut") StatutImpaye statut,
        Pageable pageable
    );

    @Query("""
        SELECT i FROM Impaye i JOIN i.client c
        WHERE (:annee IS NULL OR i.annee = :annee)
          AND (:mois  IS NULL OR i.mois  = :mois)
          AND (:agence IS NULL OR c.agenceLib = :agence)
          AND (:gestionnaire IS NULL OR c.gestionnaire = :gestionnaire)
          AND (:statut IS NULL OR i.statut = :statut)
        ORDER BY i.annee DESC, i.mois DESC, c.nom ASC
        """)
    List<Impaye> findByFiltersForExport(
        @Param("annee") Integer annee,
        @Param("mois")  Integer mois,
        @Param("agence") String agence,
        @Param("gestionnaire") String gestionnaire,
        @Param("statut") StatutImpaye statut
    );

    @Query("SELECT COUNT(i) FROM Impaye i WHERE i.statut = 'IMPAYE'")
    long countImpayes();

    @Query("SELECT COUNT(DISTINCT i.client.id) FROM Impaye i WHERE i.statut = 'IMPAYE'")
    long countClientsAvecImpayes();

    @Query("""
        SELECT i.annee, i.mois, COUNT(i) FROM Impaye i
        WHERE i.statut = 'IMPAYE'
        GROUP BY i.annee, i.mois
        ORDER BY i.annee DESC, i.mois DESC
        """)
    List<Object[]> countImpaYesParMois();

    @Query("""
        SELECT c.agenceLib, COUNT(i) FROM Impaye i JOIN i.client c
        WHERE i.statut = 'IMPAYE'
          AND (:annee IS NULL OR i.annee = :annee)
        GROUP BY c.agenceLib
        ORDER BY COUNT(i) DESC
        """)
    List<Object[]> countImpaYesParAgence(@Param("annee") Integer annee);

    @Query("SELECT DISTINCT i.annee FROM Impaye i ORDER BY i.annee DESC")
    List<Integer> findDistinctAnnees();
}
