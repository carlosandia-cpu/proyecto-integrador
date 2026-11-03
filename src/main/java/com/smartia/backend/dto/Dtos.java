package com.smartia.backend.dto;

import java.util.List;

public final class Dtos {
    private Dtos() {}

    // --- Salida ---
    public record ConcursoDto(Integer idConcurso, String titulo, String area, String textoBase, long totalPreguntas) {}
    public record OpcionDto(Integer idOpcion, String texto) {}   // no expone es_correcta
    public record PreguntaDto(Integer idPregunta, String enunciado, Integer puntaje, List<OpcionDto> opciones) {}
    public record IntentoDto(Integer idIntento, Integer idConcurso, Integer puntajeTotal) {}
    public record ResponderResponse(boolean esCorrecta, int puntajeObtenido, Integer idOpcionCorrecta, int puntajeTotal) {}
    public record DetalleDto(String enunciado, String respuestaElegida, String respuestaCorrecta, boolean esCorrecta) {}
    public record ResultadoDto(Integer idIntento, String concurso, int puntajeTotal, int puntajeMaximo,
                               int aciertos, int totalPreguntas, List<DetalleDto> detalle) {}
    public record FeedbackDto(String feedback) {}

    // --- Entrada ---
    public record EstudianteRequest(String nombre, String grado) {}
    public record IniciarIntentoRequest(Integer idEstudiante, Integer idConcurso) {}
    public record ResponderRequest(Integer idPregunta, Integer idOpcion) {}
}