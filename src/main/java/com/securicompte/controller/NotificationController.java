package com.securicompte.controller;

import com.securicompte.entity.Notification;
import com.securicompte.entity.User;
import com.securicompte.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/count")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> count() {
        return Map.of("count", notificationService.getNbNonLues());
    }

    @GetMapping("/list")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public List<Notification> list() {
        return notificationService.getNonLues();
    }

    @PostMapping("/{id}/lire")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public String marquerLue(@PathVariable Long id,
                             @AuthenticationPrincipal User user) {
        notificationService.marquerLue(id, user.getUsername());
        return "OK";
    }

    @PostMapping("/tout-lire")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public String marquerToutesLues(@AuthenticationPrincipal User user) {
        notificationService.marquerToutesLues(user.getUsername());
        return "OK";
    }

    /** Historique paginé de toutes les notifications (lues + non lues). */
    @GetMapping("/historique")
    @PreAuthorize("isAuthenticated()")
    public String historique(@RequestParam(defaultValue = "0") int page,
                             Model model) {
        Page<Notification> notifications = notificationService.getAll(PageRequest.of(page, 20));
        model.addAttribute("notifications", notifications);
        model.addAttribute("page", page);
        return "notifications/historique";
    }
}
