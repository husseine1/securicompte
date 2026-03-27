package com.securicompte.repository;

import com.securicompte.entity.SinistreImport;
import com.securicompte.enums.StatutImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SinistreImportRepository extends JpaRepository<SinistreImport, Long> {
    List<SinistreImport> findAllByOrderByDateImportDesc();
    long countByStatut(StatutImport statut);
}
