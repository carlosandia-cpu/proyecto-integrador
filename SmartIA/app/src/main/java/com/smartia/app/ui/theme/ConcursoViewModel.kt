package com.smartia.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel                 // clase base: sobrevive a giros de pantalla
import androidx.lifecycle.viewModelScope            // para lanzar tareas en segundo plano
import com.smartia.app.data.ConcursoDetalle
import com.smartia.app.data.ConcursoResumen
import com.smartia.app.data.FinalizarRequest
import com.smartia.app.data.IniciarRequest
import com.smartia.app.data.Resultado
import com.smartia.app.data.ResponderRequest
import com.smartia.app.data.RetrofitClient
import com.smartia.app.data.mensajeDeError
import kotlinx.coroutines.launch                    // inicia una corrutina

// ───────── PARTE 1: LAS 3 PANTALLAS POSIBLES ─────────
enum class Pantalla { CONCURSOS, PREGUNTA, RESULTADO }

// ───────── PARTE 2: EL ESTADO (todo lo que la app necesita saber) ─────────
data class EstadoUi(
    val pantalla: Pantalla = Pantalla.CONCURSOS,    // en qué pantalla estamos (empieza en la lista)
    val cargando: Boolean = false,                  // true = mostrar el círculo de carga
    val error: String? = null,                      // mensaje de error (null = no hay error)
    val concursos: List<ConcursoResumen> = emptyList(), // lista de la pantalla 1
    val concurso: ConcursoDetalle? = null,          // concurso elegido, con sus preguntas
    val indice: Int = 0,                            // número de la pregunta actual (0 = primera)
    val idIntento: Int = 0,                         // id del intento creado en el servidor
    val opcionElegida: Int? = null,                 // opción tocada (null = ninguna aún)
    val respondidaActual: Boolean = false,          // evita responder dos veces la misma pregunta al reintentar
    val resultado: Resultado? = null                // datos de la pantalla final
)

// ───────── PARTE 3: EL VIEWMODEL (la lógica) ─────────
class ConcursoViewModel : ViewModel() {

    companion object {
        const val ID_ESTUDIANTE = 1   // Estudiante de prueba (el inicio de sesión no es parte de HU-4)
    }

    // Cliente de la API (viene de ApiService.kt)
    private val api = RetrofitClient.api

    // El estado único. mutableStateOf: si cambia, Compose redibuja la pantalla.
    // private set: solo el ViewModel puede cambiarlo; las pantallas solo lo leen
    var estado by mutableStateOf(EstadoUi())
        private set

    // Al crearse el ViewModel, carga la lista de concursos automáticamente
    init { cargarConcursos() }

    /** Pantalla 1: lista de concursos activos. */
    fun cargarConcursos() {
        // launch: ejecuta en segundo plano para no congelar la pantalla
        viewModelScope.launch {
            // copy(): crea un estado nuevo cambiando solo lo indicado
            estado = estado.copy(pantalla = Pantalla.CONCURSOS, cargando = true, error = null)
            try {
                // Llama a la API (accion=concursos) y guarda la lista
                estado = estado.copy(concursos = api.concursos(), cargando = false)
            } catch (e: Exception) {
                // Si falla, guarda el mensaje de error para mostrarlo
                estado = estado.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }

    /** Carga las preguntas, crea el intento y pasa a la primera pregunta. */
    fun iniciar(idConcurso: Int) {
        viewModelScope.launch {
            estado = estado.copy(cargando = true, error = null)
            try {
                // Paso 1: pide las preguntas (accion=preguntas)
                val concurso = api.preguntas(idConcurso)
                // Si no tiene preguntas, avisa y se detiene (return@launch sale de la corrutina)
                if (concurso.preguntas.isEmpty()) {
                    estado = estado.copy(cargando = false, error = "Este concurso aún no tiene preguntas.")
                    return@launch
                }
                // Paso 2: crea el intento en el servidor (accion=iniciar)
                val intento = api.iniciar(IniciarRequest(ID_ESTUDIANTE, idConcurso))
                // Paso 3: guarda todo y cambia a la pantalla de preguntas
                estado = estado.copy(
                    pantalla = Pantalla.PREGUNTA,       // ← esto cambia la pantalla
                    concurso = concurso,
                    indice = 0,                         // empieza en la primera pregunta
                    idIntento = intento.idIntento,      // el id que devolvió el servidor
                    opcionElegida = null,
                    respondidaActual = false,
                    resultado = null,
                    cargando = false
                )
            } catch (e: Exception) {
                estado = estado.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }

    // Se llama cuando el estudiante toca una opción: solo la guarda en el estado
    // (esto habilita el botón "Siguiente")
    fun elegirOpcion(idOpcion: Int) {
        estado = estado.copy(opcionElegida = idOpcion)
    }

    /** Pantalla 2: guarda la respuesta y avanza. En la última pregunta cierra el intento. */
    fun responder() {
        // Si no hay concurso u opción elegida, no hace nada (?: return)
        val concurso = estado.concurso ?: return
        val opcion = estado.opcionElegida ?: return
        // La pregunta actual según el índice
        val pregunta = concurso.preguntas[estado.indice]

        viewModelScope.launch {
            estado = estado.copy(cargando = true, error = null)
            try {
                // Envía la respuesta SOLO si no se envió antes.
                // Si falló la red y el estudiante reintenta, no se guarda dos veces.
                if (!estado.respondidaActual) {
                    api.responder(ResponderRequest(estado.idIntento, pregunta.idPregunta, opcion))
                    estado = estado.copy(respondidaActual = true)
                }
                // ¿Quedan más preguntas?
                if (estado.indice + 1 < concurso.preguntas.size) {
                    // SÍ: avanza a la siguiente y limpia la opción elegida
                    estado = estado.copy(
                        indice = estado.indice + 1,
                        opcionElegida = null,
                        respondidaActual = false,
                        cargando = false
                    )
                } else {
                    // NO (era la última): cierra el intento y va al resultado
                    api.finalizar(FinalizarRequest(estado.idIntento))
                    estado = estado.copy(pantalla = Pantalla.RESULTADO, cargando = false)
                    cargarResultado()
                }
            } catch (e: Exception) {
                estado = estado.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }

    /** Pantalla 3: puntaje final del intento. */
    fun cargarResultado() {
        viewModelScope.launch {
            estado = estado.copy(cargando = true, error = null)
            try {
                // Pide el puntaje, correctas e incorrectas (accion=resultado)
                estado = estado.copy(resultado = api.resultado(estado.idIntento), cargando = false)
            } catch (e: Exception) {
                estado = estado.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }

    // Reinicia todo el estado y vuelve a cargar la lista de concursos
    fun volverAlInicio() {
        estado = EstadoUi()
        cargarConcursos()
    }
}