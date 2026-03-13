package com.securicompte.repository;

import com.securicompte.entity.Souscription;
import com.securicompte.enums.TypeSouscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SouscriptionRepository extends JpaRepository<Souscription, Long> {
    List<Souscription> findByClientId(Long clientId);
    List<Souscription> findByClientIdOrderByDatSouscriptionAsc(Long clientId);
    boolean existsByClientIdAndDatSouscriptionAndTypeSouscription(
        Long clientId, LocalDate datSouscription, TypeSouscription type);
}
