package com.securicompte.service;

import com.securicompte.entity.Impaye;
import com.securicompte.entity.Notification;
import com.securicompte.enums.StatutImpaye;
import com.securicompte.repository.ImpayeRepository;
import com.securicompte.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service d'alertes automatiques pour les impayés anciens.
 *
 * Règle : un impayé est "ancien" s'il est encore en statut IMPAYÉ
 * et que son mois/année dépasse le seuil configuré (défaut : 3 mois).
 *
 * Le job tourne chaque jour à 8h00. Une seule notification est créée
 * par impayé (anti-doublon via existsByTypeAndImpayeId).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlerteImpayeService {

    @Value("${app.alertes.seuil-impayes-mois:3}")
    private int seuilMois;

    private final ImpayeRepository      impayeRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 8 * * *")   // tous les jours à 8h00
    @Transactional
    public void alerterImpaYesAnciens() {
        LocalDate aujourd = LocalDate.now();
        int limitePeriode = aujourd.getYear() * 12 + aujourd.getMonthValue() - seuilMois;

        List<Impaye> anciens = impayeRepository.findImpaYesAnciens(StatutImpaye.IMPAYE, limitePeriode);
        if (anciens.isEmpty()) {
            log.debug("Alertes impayés anciens : aucun impayé dépassant {} mois.", seuilMois);
            return;
        }

        int nbCrees = 0;
        for (Impaye impaye : anciens) {
            if (notificationRepository.existsByTypeAndImpayeId("IMPAYE_ANCIEN", impaye.getId())) {
                continue; // déjà notifié
            }
            Notification notif = Notification.builder()
                    .type("IMPAYE_ANCIEN")
                    .message(String.format(
                            "Impayé non régularisé depuis plus de %d mois : %s (n° %s) — %s %d",
                            seuilMois,
                            impaye.getClient().getNom(),
                            impaye.getClient().getNumeroClient(),
                            getMoisNom(impaye.getMois()),
                            impaye.getAnnee()))
                    .clientId(impaye.getClient().getId())
                    .clientNom(impaye.getClient().getNom())
                    .impayeId(impaye.getId())
                    .anneeImpaye(impaye.getAnnee())
                    .moisImpaye(impaye.getMois())
                    .creePar("SYSTEME")
                    .build();
            notificationRepository.save(notif);
            nbCrees++;
        }

        if (nbCrees > 0) {
            log.info("Alertes impayés anciens : {} nouvelle(s) notification(s) créée(s) (seuil : {} mois).",
                    nbCrees, seuilMois);
        }
    }

    private String getMoisNom(Integer mois) {
        if (mois == null || mois < 1 || mois > 12) return "";
        String[] noms = {"", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
        return noms[mois];
    }
}
