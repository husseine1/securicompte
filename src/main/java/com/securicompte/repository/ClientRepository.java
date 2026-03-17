package com.securicompte.repository;

import com.securicompte.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByNumeroClient(String numeroClient);
    boolean existsByNumeroClient(String numeroClient);
    List<Client> findByNumeroClientIn(Collection<String> numeroClients);
    List<Client> findByNomIn(Collection<String> noms);

    @Query("""
        SELECT c FROM Client c
        WHERE LOWER(c.numeroClient) LIKE LOWER(CONCAT('%',:q,'%'))
           OR LOWER(c.nom)          LIKE LOWER(CONCAT('%',:q,'%'))
        """)
    Page<Client> rechercherClients(@Param("q") String q, Pageable pageable);

    @Query("SELECT DISTINCT c.agenceLib FROM Client c WHERE c.agenceLib IS NOT NULL ORDER BY c.agenceLib")
    List<String> findDistinctAgences();

    @Query("SELECT DISTINCT c.gestionnaire FROM Client c WHERE c.gestionnaire IS NOT NULL ORDER BY c.gestionnaire")
    List<String> findDistinctGestionnaires();
}
