package com.smartia.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartia.app.data.ConcursoDetalle
import com.smartia.app.data.ConcursoResumen
import com.smartia.app.data.IniciarRequest
import com.smartia.app.data.ResponderRequest
import com.smartia.app.data.Resultado
import com.smartia.app.data.RetrofitClient
import com.smartia.app.data.mensajeDeError
import kotlinx.coroutines.launch
import retrofit2.HttpException

// ───────── PARTE 1: LAS 3 PANTALLAS POSIBLES ─────────
enum class Pantalla { CONCURSOS, PREGUNTA, RESULTADO }

// ───────── PARTE 2: EL ESTADO ─────────
data class EstadoUi(
    val pantalla: Pantalla = Pantalla.CONCURSOS,
    val cargando: Boolean = false,
    val error: String? = null,
    val concursos: List<ConcursoResumen> = emptyList(),
    val concurso: ConcursoDetalle? = null,
    val indice: Int = 0,
    val idIntento: Int = 0,
    val opcionElegida: Int? = null,
    val respondidaActual: Boolean = false,
    val resultado: Resultado? = null,
    // Retroalimentacion de la IA (pantalla final)
    val feedback: String? = null,
    val cargandoFeedback: Boolean = false,
    val errorFeedback: String? = null
)

// ───────── PARTE 3: EL VIEWMODEL ─────────
class ConcursoViewModel : ViewModel() {

    companion object {
        const val ID_ESTUDIANTE = 1   // "Estudiante Demo" de smartia.sql
    }

    private val api = RetrofitClient.api

    var estado by mutableStateOf(EstadoUi())
        private set

    init { cargarConcursos() }

    /** Pantalla 1: lista de concursos activos. */
    fun cargarConcursos() {
        viewModelScope.launch {
            estado = estado.copy(pantalla = Pantalla.CONCURSOS, cargando = true, error = null)
            try {
                estado = estado.copy(concursos = api.concursos(), cargando = false)
            } catch (e: Exception) {
                estado = estado.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }

    /** Carga las preguntas, crea el intento y pasa a la primera pregunta. */
    fun iniciar(idConcurso: Int) {
        viewModelScope.launch {
            estado = estado.copy(cargando = true, error = null)
            try {
                // Los datos del concurso (titulo, area, texto base) ya vienen en la lista
                val resumen = estado.concursos.firstOrNull { it.idConcurso == idConcurso }
                if (resumen == null) {
                    estado = estado.copy(cargando = false, error = "Concurso no encontrado.")
                    return@launch
                }
                val preguntas = api.preguntas(idConcurso)
                if (preguntas.isEmpty()) {
                    estado = estado.copy(cargando = false, error = "Este concurso aun no tiene preguntas.")
                    return@launch
                }
                val concurso = ConcursoDetalle(
                    idConcurso = resumen.idConcurso,
                    titulo = resumen.titulo,
                    area = resumen.area,
                    textoBase = resumen.textoBase,
                    preguntas = preguntas
                )
                val intento = api.iniciar(IniciarRequest(ID_ESTUDIANTE, idConcurso))
                estado = estado.copy(
                    pantalla = Pantalla.PREGUNTA,
                    concurso = concurso,
                    indice = 0,
                    idIntento = intento.idIntento,
                    opcionElegida = null,
                    respondidaActual = false,
                    resultado = null,
                    feedback = null,
                    errorFeedback = null,
                    cargandoFeedback = false,
                    cargando = false
                )
            } catch (e: Exception) {
                estado = estado.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }

    fun elegirOpcion(idOpcion: Int) {
        estado = estado.copy(opcionElegida = idOpcion)
    }

    /** Pantalla 2: guarda la respuesta y avanza. En la ultima pregunta cierra el intento. */
    fun responder() {
        val concurso = estado.concurso ?: return
        val opcion = estado.opcionElegida ?: return
        val pregunta = concurso.preguntas[estado.indice]

        viewModelScope.launch {
            estado = estado.copy(cargando = true, error = null)
            try {
                if (!estado.respondidaActual) {
                    try {
                        api.responder(estado.idIntento, ResponderRequest(pregunta.idPregunta, opcion))
                    } catch (e: HttpException) {
                        // 409 = el servidor ya la tenia guardada (reintento tras fallo de red): se ignora
                        if (e.code() != 409) throw e
                    }
                    estado = estado.copy(respondidaActual = true)
                }
                if (estado.indice + 1 < concurso.preguntas.size) {
                    estado = estado.copy(
                        indice = estado.indice + 1,
                        opcionElegida = null,
                        respondidaActual = false,
                        cargando = false
                    )
                } else {
                    // Era la ultima: cierra el intento; el servidor devuelve el resultado
                    val res = api.finalizar(estado.idIntento)
                    estado = estado.copy(pantalla = Pantalla.RESULTADO, resultado = res, cargando = false)
                    cargarFeedback()
                }
            } catch (e: Exception) {
                estado = estado.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }

    /** Pantalla 3: vuelve a pedir el resultado (boton Reintentar). */
    fun cargarResultado() {
        viewModelScope.launch {
            estado = estado.copy(cargando = true, error = null)
            try {
                estado = estado.copy(resultado = api.resultado(estado.idIntento), cargando = false)
                if (estado.feedback == null && !estado.cargandoFeedback) cargarFeedback()
            } catch (e: Exception) {
                estado = estado.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }

    /** IA: pide a Gemini la retroalimentacion del desempeno. */
    fun cargarFeedback() {
        viewModelScope.launch {
            estado = estado.copy(cargandoFeedback = true, errorFeedback = null)
            try {
                val f = api.feedback(estado.idIntento)
                estado = estado.copy(feedback = f.feedback, cargandoFeedback = false)
            } catch (e: Exception) {
                estado = estado.copy(cargandoFeedback = false, errorFeedback = mensajeDeError(e))
            }
        }
    }

    fun volverAlInicio() {
        estado = EstadoUi()
        cargarConcursos()
    }
}