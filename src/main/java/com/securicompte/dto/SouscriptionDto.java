package com.securicompte.dto;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SouscriptionDto {
    private Long id;
    private String securicompte;
    private BigDecimal commissions;
    private String libelPackage;
    private String optionSecuricompte;
    private LocalDate datSouscription;
    private LocalDate datOuverture;
    private String typeSouscription;
}
