package com.smartia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
public class Pregunta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPregunta;
    private Integer idConcurso;
    private String enunciado;
    private Integer puntaje;
}
