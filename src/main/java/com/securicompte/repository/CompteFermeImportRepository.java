package com.securicompte.repository;

import com.securicompte.entity.CompteFermeImport;
import com.securicompte.enums.StatutImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CompteFermeImportRepository extends JpaRepository<CompteFermeImport, Long> {
    List<CompteFermeImport> findAllByOrderByDateImportDesc();
    long countByStatut(StatutImport statut);
}
