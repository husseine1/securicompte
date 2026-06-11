package com.securicompte.service;

import com.securicompte.entity.Notification;
import com.securicompte.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void creerNotificationChangementClientImport(int annee, int mois, long nbChangements, String importePar) {
        String message = String.format(
                "Import %s %d : %d modification(s) de données client détectée(s).",
                getMoisNom(mois), annee, nbChangements
        );
        Notification notif = Notification.builder()
                .type("CHANGEMENT_CLIENT_IMPORT")
                .message(message)
                .anneeImpaye(annee)
                .moisImpaye(mois)
                .creePar(importePar)
                .build();
        notificationRepository.save(notif);
        log.info("Notification créée : {} modification(s) de données client pour {}/{}", nbChangements, mois, annee);
    }

    @Transactional(readOnly = true)
    public List<Notification> getNonLues() {
        return notificationRepository.findByLuFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Notification> getAll(
            org.springframework.data.domain.Pageable pageable) {
        return notificationRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public long getNbNonLues() {
        return notificationRepository.countByLuFalse();
    }

    @Transactional
    public void marquerLue(Long id, String user) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setLu(true);
            n.setLuPar(user);
            n.setDateLu(LocalDateTime.now());
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void marquerToutesLues(String user) {
        List<Notification> nonLues = notificationRepository.findByLuFalseOrderByCreatedAtDesc();
        LocalDateTime now = LocalDateTime.now();
        nonLues.forEach(n -> {
            n.setLu(true);
            n.setLuPar(user);
            n.setDateLu(now);
        });
        notificationRepository.saveAll(nonLues);
    }

    private String getMoisNom(Integer mois) {
        if (mois == null || mois < 1 || mois > 12) return "";
        String[] noms = {"", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
        return noms[mois];
    }

    private String nvl(String s) {
        return s != null ? s : "N/A";
    }
}
