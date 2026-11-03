package com.smartia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
public class Concurso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idConcurso;
    private String titulo;
    private String area;       // LECTURA | MATEMATICAS
    private String textoBase;
    private String estado;     // ACTIVO | INACTIVO
}
