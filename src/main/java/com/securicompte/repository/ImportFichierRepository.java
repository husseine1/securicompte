package com.securicompte.repository;

import com.securicompte.entity.ImportFichier;
import com.securicompte.enums.StatutImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImportFichierRepository extends JpaRepository<ImportFichier, Long> {
    Optional<ImportFichier> findByAnneeAndMois(Integer annee, Integer mois);
    boolean existsByAnneeAndMois(Integer annee, Integer mois);
    List<ImportFichier> findAllByOrderByAnneeDescMoisDesc();
    List<ImportFichier> findTop5ByOrderByDateImportDesc();
    List<ImportFichier> findByAnneeOrderByMoisDesc(Integer annee);

    @Query("SELECT DISTINCT f.annee FROM ImportFichier f ORDER BY f.annee DESC")
    List<Integer> findDistinctAnnees();

    @Query("SELECT f.annee, f.mois FROM ImportFichier f")
    List<Object[]> findAllAnneesMois();

    /** Mise à jour directe du statut — évite un SELECT+UPDATE en deux temps. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ImportFichier f SET f.statut = :statut WHERE f.id = :id")
    void updateStatut(@Param("id") Long id, @Param("statut") StatutImport statut);

    /** Suppression directe par id — évite le reload de l'entité avant DELETE. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ImportFichier f WHERE f.id = :id")
    void deleteDirectById(@Param("id") Long id);

    long countByStatut(StatutImport statut);
}
