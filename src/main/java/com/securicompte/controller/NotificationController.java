package com.securicompte.controller;

import com.securicompte.entity.Notification;
import com.securicompte.entity.User;
import com.securicompte.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> count() {
        return Map.of("count", notificationService.getNbNonLues());
    }

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public List<Notification> list() {
        return notificationService.getNonLues();
    }

    @PostMapping("/{id}/lire")
    @PreAuthorize("isAuthenticated()")
    public String marquerLue(@PathVariable Long id,
                             @AuthenticationPrincipal User user) {
        notificationService.marquerLue(id, user.getUsername());
        return "OK";
    }

    @PostMapping("/tout-lire")
    @PreAuthorize("isAuthenticated()")
    public String marquerToutesLues(@AuthenticationPrincipal User user) {
        notificationService.marquerToutesLues(user.getUsername());
        return "OK";
    }
}
