package com.securicompte.dto;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardStatsDto {
    private long totalClients;
    private long totalImpayes;
    private long totalRegularises;
    private double tauxRegularisation;
    private long totalClientsAvecImpayes;
    private long totalImportsFaits;
    private long clientsBni;
    private long clientsSib;
    private List<ImportResultDto> derniersImports;
    private List<Top10ClientDto> top10Clients;
}
