package com.securicompte.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "description", length = 200)
    private String description;
}
