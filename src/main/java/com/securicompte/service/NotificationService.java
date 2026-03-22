package com.securicompte.service;

import com.securicompte.entity.Impaye;
import com.securicompte.entity.Notification;
import com.securicompte.entity.StockMensuel;
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

    /**
     * Crée une notification de changement de prime lors d'une régularisation.
     *
     * @param impaye         l'impayé régularisé
     * @param stockOriginal  stock du mois de l'impayé (peut être null si non importé)
     * @param stockActuel    stock du mois de régularisation
     * @param creePar        username de l'agent/admin qui régularise
     */
    @Transactional
    public void creerNotificationChangementPrime(
            Impaye impaye,
            StockMensuel stockOriginal,
            StockMensuel stockActuel,
            String creePar) {

        String moisNomOriginal = getMoisNom(impaye.getMois());
        int anneeActuel   = stockActuel != null ? stockActuel.getAnnee() : LocalDateTime.now().getYear();
        int moisActuel    = stockActuel != null ? stockActuel.getMois()  : LocalDateTime.now().getMonthValue();
        String moisNomActuel = getMoisNom(moisActuel);

        String scAvant   = stockOriginal != null ? nvl(stockOriginal.getSecuricompte()) : "N/A";
        String scApres   = stockActuel   != null ? nvl(stockActuel.getSecuricompte())   : "N/A";
        String commAvant = stockOriginal != null && stockOriginal.getCommissions() != null
                ? stockOriginal.getCommissions().toPlainString() : "N/A";
        String commApres = stockActuel   != null && stockActuel.getCommissions()   != null
                ? stockActuel.getCommissions().toPlainString()   : "N/A";

        String message = String.format(
                "Changement de prime détecté pour %s (n° %s). " +
                "Impayé: %s %d → Régularisation: %s %d. " +
                "SecuriCompte: [%s → %s] | Commission: [%s → %s]",
                impaye.getClient().getNom(),
                impaye.getClient().getNumeroClient(),
                moisNomOriginal, impaye.getAnnee(),
                moisNomActuel,   anneeActuel,
                scAvant, scApres,
                commAvant, commApres
        );

        Notification notif = Notification.builder()
                .type("CHANGEMENT_PRIME")
                .message(message)
                .clientId(impaye.getClient().getId())
                .clientNom(impaye.getClient().getNom())
                .impayeId(impaye.getId())
                .anneeImpaye(impaye.getAnnee())
                .moisImpaye(impaye.getMois())
                .securicompteAvant(stockOriginal != null ? stockOriginal.getSecuricompte() : null)
                .securicompteApres(stockActuel   != null ? stockActuel.getSecuricompte()   : null)
                .commissionsAvant(stockOriginal  != null ? stockOriginal.getCommissions()  : null)
                .commissionsApres(stockActuel    != null ? stockActuel.getCommissions()    : null)
                .creePar(creePar)
                .build();

        notificationRepository.save(notif);
        log.info("Notification changement de prime créée — impayé {} client {}",
                impaye.getId(), impaye.getClient().getNumeroClient());
    }

    /**
     * Crée une notification de synthèse pour les changements de prime détectés
     * lors d'un import mensuel.
     *
     * @param annee          année importée
     * @param mois           mois importé
     * @param nbChangements  nombre total de clients avec écart de prime
     * @param details        texte descriptif des premiers exemples
     * @param importePar     username de l'agent/admin qui a importé
     */
    @Transactional
    public void creerNotificationChangementPrimeImport(
            int annee, int mois, int nbChangements, String details, String importePar) {

        String moisNom = getMoisNom(mois);
        String message = String.format(
                "Import %s %d : %d client(s) avec changement de prime détecté(s). %s",
                moisNom, annee, nbChangements, details
        );

        Notification notif = Notification.builder()
                .type("CHANGEMENT_PRIME_IMPORT")
                .message(message)
                .anneeImpaye(annee)
                .moisImpaye(mois)
                .creePar(importePar)
                .build();

        notificationRepository.save(notif);
        log.info("Notification import créée : {} changement(s) de prime pour {}/{}",
                nbChangements, mois, annee);
    }

    @Transactional(readOnly = true)
    public List<Notification> getNonLues() {
        return notificationRepository.findByLuFalseOrderByCreatedAtDesc();
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
