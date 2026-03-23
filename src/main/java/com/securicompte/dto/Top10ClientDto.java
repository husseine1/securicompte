package com.securicompte.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Top10ClientDto {
    private String nom;
    private String numeroClient;
    private long   nbImpayes;
}
