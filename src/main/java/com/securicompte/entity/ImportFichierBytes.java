package com.securicompte.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "import_fichier_bytes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportFichierBytes {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private ImportFichier importFichier;

    @Column(name = "fichier_bytes", nullable = false)
    private byte[] fichierBytes;

    @Column(name = "taille_octets")
    private Long tailleOctets;
}
