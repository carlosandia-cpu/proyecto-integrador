package com.smartia.app.data

import com.google.gson.annotations.SerializedName

// Respuestas y cuerpos de la API (api/api.php). Los nombres JSON van en snake_case.
// REGLA GENERAL: PHP manda nombres con guion bajo (id_concurso) y Kotlin usa
// mayúsculas internas (idConcurso). @SerializedName hace la traducción.

// ───────── RESPUESTAS (lo que LLEGA de la API) ─────────

// Un concurso en la lista de la primera pantalla (acción: concursos)
data class ConcursoResumen(
    @SerializedName("id_concurso") val idConcurso: Int,
    val titulo: String,
    val area: String,                                       // materia o área del concurso
    @SerializedName("total_preguntas") val totalPreguntas: Int  // el COUNT del backend
)

// Una alternativa de respuesta. Fíjate: NO tiene es_correcta,
// porque el servidor nunca la envía
data class Opcion(
    @SerializedName("id_opcion") val idOpcion: Int,
    val texto: String
)

// Una pregunta con todas sus opciones adentro
data class Pregunta(
    @SerializedName("id_pregunta") val idPregunta: Int,
    val enunciado: String,                // el texto de la pregunta
    val puntaje: Int,                     // cuántos puntos vale
    val opciones: List<Opcion>            // lista de alternativas
)

// El concurso completo con sus preguntas (acción: preguntas)
data class ConcursoDetalle(
    @SerializedName("id_concurso") val idConcurso: Int,
    val titulo: String,
    val area: String,
    @SerializedName("texto_base") val textoBase: String?,   // el "?" = puede venir vacío (null)
    val preguntas: List<Pregunta>
)

// ───────── PETICIONES (lo que la app ENVÍA) y sus respuestas ─────────

// Cuerpo que se envía para empezar un intento (acción: iniciar)
data class IniciarRequest(
    @SerializedName("id_estudiante") val idEstudiante: Int,
    @SerializedName("id_concurso") val idConcurso: Int
)

// El servidor responde con el id del intento recién creado
data class IniciarResponse(
    @SerializedName("id_intento") val idIntento: Int
)

// Cuerpo que se envía al responder: solo QUÉ opción eligió el estudiante
// (la app no dice si es correcta; eso lo decide el servidor)
data class ResponderRequest(
    @SerializedName("id_intento") val idIntento: Int,
    @SerializedName("id_pregunta") val idPregunta: Int,
    @SerializedName("id_opcion") val idOpcion: Int
)

// El servidor responde si acertó y cuántos puntos ganó
data class ResponderResponse(
    val correcta: Boolean,
    val puntaje: Int
)

// Cuerpo para cerrar el intento (acción: finalizar)
data class FinalizarRequest(
    @SerializedName("id_intento") val idIntento: Int
)

// El servidor responde con la suma de todos los puntos
data class FinalizarResponse(
    @SerializedName("puntaje_total") val puntajeTotal: Int
)

// Datos de la pantalla final: anillo de porcentaje y cajas (acción: resultado)
data class Resultado(
    @SerializedName("id_intento") val idIntento: Int,
    val titulo: String,
    val area: String,
    @SerializedName("puntaje_total") val puntajeTotal: Int,
    val correctas: Int,
    val incorrectas: Int
)

// Si el servidor responde con error (400, 404, 409...), viene como {"error": "mensaje"}
data class ErrorApi(val error: String?)