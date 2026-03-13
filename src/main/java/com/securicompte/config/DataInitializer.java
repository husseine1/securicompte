package com.securicompte.config;

import com.securicompte.entity.Role;
import com.securicompte.entity.User;
import com.securicompte.repository.RoleRepository;
import com.securicompte.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Les rôles et utilisateurs initiaux sont créés par Flyway (V1__init_schema.sql).
        // Ce runner ne fait que vérifier / corriger en cas de besoin.

        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            log.warn("Rôle ROLE_ADMIN absent — création en cours");
            return roleRepository.save(Role.builder()
                .name("ROLE_ADMIN").description("Administrateur système").build());
        });
        roleRepository.findByName("ROLE_AGENT").orElseGet(() ->
            roleRepository.save(Role.builder()
                .name("ROLE_AGENT").description("Agent — import et gestion").build()));
        roleRepository.findByName("ROLE_CONSULTATION").orElseGet(() ->
            roleRepository.save(Role.builder()
                .name("ROLE_CONSULTATION").description("Consultation uniquement").build()));

        if (!userRepository.existsByUsername("admin")) {
            log.warn("Utilisateur admin absent — création avec password=admin123");
            userRepository.save(User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .email("admin@securicompte.com")
                .nomComplet("Administrateur Système")
                .actif(true)
                .role(adminRole)
                .build());
        }
    }
}
