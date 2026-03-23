package com.securicompte.repository;

import com.securicompte.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByLuFalseOrderByCreatedAtDesc();

    long countByLuFalse();

    /** Vérifie si une alerte du même type existe déjà pour cet impayé (anti-doublon). */
    boolean existsByTypeAndImpayeId(String type, Long impayeId);

    /** Toutes les notifications triées par date (pour l'historique paginé). */
    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
