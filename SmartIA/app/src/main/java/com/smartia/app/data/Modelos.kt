package com.smartia.app.data

// Modelos para la API Spring Boot (/api/...).
// Spring envia el JSON en camelCase (idConcurso), igual que Kotlin,
// por eso ya no hace falta @SerializedName.

// ───────── RESPUESTAS (lo que LLEGA de la API) ─────────

// Un concurso de la lista (GET /concursos)
data class ConcursoResumen(
    val idConcurso: Int,
    val titulo: String,
    val area: String,              // "LECTURA" | "MATEMATICAS"
    val textoBase: String?,        // solo los concursos de lectura lo traen
    val totalPreguntas: Int
)

// Una alternativa. El servidor NUNCA envia cual es la correcta
data class Opcion(
    val idOpcion: Int,
    val texto: String
)

// Una pregunta con sus opciones (GET /concursos/{id}/preguntas)
data class Pregunta(
    val idPregunta: Int,
    val enunciado: String,
    val puntaje: Int,
    val opciones: List<Opcion>
)

// Concurso completo. La app lo arma juntando ConcursoResumen + lista de Pregunta
data class ConcursoDetalle(
    val idConcurso: Int,
    val titulo: String,
    val area: String,
    val textoBase: String?,
    val preguntas: List<Pregunta>
)

// ───────── PETICIONES (lo que la app ENVIA) y sus respuestas ─────────

// POST /intentos
data class IniciarRequest(
    val idEstudiante: Int,
    val idConcurso: Int
)

data class IniciarResponse(
    val idIntento: Int
)

// POST /intentos/{id}/respuestas  (el id del intento va en la URL)
data class ResponderRequest(
    val idPregunta: Int,
    val idOpcion: Int
)

data class ResponderResponse(
    val esCorrecta: Boolean,
    val puntajeObtenido: Int,
    val idOpcionCorrecta: Int?,
    val puntajeTotal: Int
)

// Una fila del detalle del resultado
data class DetalleRespuesta(
    val enunciado: String,
    val respuestaElegida: String,
    val respuestaCorrecta: String,
    val esCorrecta: Boolean
)

// POST /intentos/{id}/finalizar  y  GET /intentos/{id}/resultado
data class Resultado(
    val idIntento: Int,
    val concurso: String,
    val puntajeTotal: Int,
    val puntajeMaximo: Int,
    val aciertos: Int,
    val totalPreguntas: Int,
    val detalle: List<DetalleRespuesta>
)

// POST /intentos/{id}/feedback  (texto generado por la IA)
data class FeedbackResponse(
    val feedback: String
)

// Spring devuelve los errores como {"status":404,"error":"Not Found","message":"..."}
data class ErrorApi(
    val message: String?,
    val error: String?
)