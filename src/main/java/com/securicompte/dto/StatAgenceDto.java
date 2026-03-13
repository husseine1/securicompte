package com.securicompte.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StatAgenceDto {
    private String agence;
    private long nbImpayes;
}
