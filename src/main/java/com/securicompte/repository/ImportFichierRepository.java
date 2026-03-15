package com.securicompte.repository;

import com.securicompte.entity.ImportFichier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
