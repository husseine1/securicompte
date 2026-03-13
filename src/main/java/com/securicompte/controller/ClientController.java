package com.securicompte.controller;

import com.securicompte.dto.ClientDetailDto;
import com.securicompte.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping("/recherche")
    @PreAuthorize("isAuthenticated()")
    public String rechercheClient(
            @RequestParam(value = "q", required = false, defaultValue = "") String recherche,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        if (!recherche.isEmpty()) {
            var clients = clientService.rechercherClients(recherche, page, 20);
            model.addAttribute("clients", clients);
            model.addAttribute("totalPages", clients.getTotalPages());
            model.addAttribute("totalElements", clients.getTotalElements());
        }
        model.addAttribute("recherche", recherche);
        model.addAttribute("page", page);
        return "client/recherche";
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String detailClient(@PathVariable Long id, Model model) {
        ClientDetailDto client = clientService.getClientDetail(id);
        model.addAttribute("client", client);
        return "client/detail";
    }
}
