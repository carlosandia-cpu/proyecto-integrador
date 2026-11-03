package com.smartia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
public class Respuesta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idRespuesta;
    private Integer idIntento;
    private Integer idPregunta;
    private Integer idOpcion;
    private Boolean esCorrecta;
    private Integer puntajeObtenido;
}
