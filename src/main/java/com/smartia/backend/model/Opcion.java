package com.smartia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
public class Opcion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idOpcion;
    private Integer idPregunta;
    private String texto;
    private Boolean esCorrecta;
}
