package com.securicompte.dto;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClientDetailDto {
    private Long id;
    private String numeroClient;
    private String nom;
    private LocalDate dateNaissance;
    private String compte;
    private String zoneLib;
    private String agenceLib;
    private String gestionnaire;
    private List<SouscriptionDto> souscriptions;
    private List<HistoriquePaiementDto> historiquePaiements;
    private List<ImpayeDto> impayes;
    private int nbImpayes;
    private BigDecimal montantTotalDu;
}
