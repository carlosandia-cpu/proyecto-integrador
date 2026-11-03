package com.smartia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor
public class Intento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idIntento;
    private Integer idEstudiante;
    private Integer idConcurso;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Integer puntajeTotal = 0;
}
