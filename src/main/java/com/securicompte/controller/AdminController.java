package com.securicompte.controller;

import com.securicompte.entity.Role;
import com.securicompte.entity.User;
import com.securicompte.repository.RoleRepository;
import com.securicompte.repository.UserRepository;
import com.securicompte.service.ImpayeDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserRepository           userRepository;
    private final RoleRepository           roleRepository;
    private final PasswordEncoder          passwordEncoder;
    private final ImpayeDetectionService   impayeDetectionService;

    // ── Gestion des utilisateurs ──────────────────────────────────

    @GetMapping("/utilisateurs")
    public String listeUtilisateurs(Model model) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("newUser", new User());
        return "admin/utilisateurs";
    }

    @PostMapping("/utilisateurs/creer")
    public String creerUtilisateur(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String email,
            @RequestParam String nomComplet,
            @RequestParam Long  roleId,
            RedirectAttributes ra) {

        if (userRepository.existsByUsername(username)) {
            ra.addFlashAttribute("erreur", "L'identifiant '" + username + "' est déjà utilisé.");
            return "redirect:/admin/utilisateurs";
        }

        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new RuntimeException("Rôle introuvable"));

        userRepository.save(User.builder()
            .username(username)
            .password(passwordEncoder.encode(password))
            .email(email)
            .nomComplet(nomComplet)
            .role(role)
            .actif(true)
            .build());

        ra.addFlashAttribute("succes", "Utilisateur '" + username + "' créé avec succès.");
        return "redirect:/admin/utilisateurs";
    }

    @PostMapping("/utilisateurs/{id}/toggle")
    public String toggleActif(@PathVariable Long id, RedirectAttributes ra) {
        userRepository.findById(id).ifPresent(user -> {
            user.setActif(!Boolean.TRUE.equals(user.getActif()));
            userRepository.save(user);
        });
        ra.addFlashAttribute("succes", "Statut mis à jour.");
        return "redirect:/admin/utilisateurs";
    }

    @PostMapping("/utilisateurs/{id}/reinitialiser-mdp")
    public String reinitialiserMotDePasse(
            @PathVariable Long id,
            @RequestParam String nouveauMdp,
            RedirectAttributes ra) {

        userRepository.findById(id).ifPresent(user -> {
            user.setPassword(passwordEncoder.encode(nouveauMdp));
            userRepository.save(user);
        });
        ra.addFlashAttribute("succes", "Mot de passe réinitialisé.");
        return "redirect:/admin/utilisateurs";
    }

    @PostMapping("/utilisateurs/{id}/supprimer")
    public String supprimerUtilisateur(@PathVariable Long id, RedirectAttributes ra) {
        userRepository.deleteById(id);
        ra.addFlashAttribute("succes", "Utilisateur supprimé.");
        return "redirect:/admin/utilisateurs";
    }

    // ── Outils système ────────────────────────────────────────────

    @PostMapping("/recalculer-impayes")
    public String recalculerImpayes(RedirectAttributes ra) {
        try {
            impayeDetectionService.recalculerTout();
            ra.addFlashAttribute("succes", "Recalcul des impayés terminé avec succès.");
        } catch (Exception e) {
            log.error("Erreur recalcul impayés", e);
            ra.addFlashAttribute("erreur", "Erreur lors du recalcul : " + e.getMessage());
        }
        return "redirect:/admin/utilisateurs";
    }
}
