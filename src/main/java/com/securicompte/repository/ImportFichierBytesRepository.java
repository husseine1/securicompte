package com.securicompte.repository;

import com.securicompte.entity.ImportFichierBytes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportFichierBytesRepository extends JpaRepository<ImportFichierBytes, Long> {

    @Query("SELECT f.id FROM ImportFichierBytes f")
    List<Long> findAllIds();
}
