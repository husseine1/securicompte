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

}
