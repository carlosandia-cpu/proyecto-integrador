package com.smartia.app.data

import com.google.gson.Gson
import retrofit2.HttpException                      // error que devuelve el servidor (400, 404, 500...)
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body                          // para enviar un objeto como JSON
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query                         // para agregar parámetros a la URL
import java.io.IOException                          // error de red (sin conexión, timeout)

// ───────── PARTE 1: LAS 6 ACCIONES ─────────
// Las 6 acciones de la API PHP: api.php?accion=...
// Es una interfaz: solo DECLARA las llamadas. Retrofit crea el código real.
interface SmartiaApi {

    // 1. Lista de concursos. URL: api.php?accion=concursos
    //    No envía datos; recibe una LISTA de ConcursoResumen
    @GET("api.php")
    suspend fun concursos(@Query("accion") accion: String = "concursos"): List<ConcursoResumen>

    // 2. Preguntas de un concurso. URL: api.php?id_concurso=1&accion=preguntas
    //    Recibe el concurso completo con sus preguntas y opciones
    @GET("api.php")
    suspend fun preguntas(
        @Query("id_concurso") idConcurso: Int,
        @Query("accion") accion: String = "preguntas"
    ): ConcursoDetalle

    // 3. Empezar un intento. POST porque envía datos.
    //    @Body: el objeto IniciarRequest se convierte en JSON y va en el cuerpo
    @POST("api.php")
    suspend fun iniciar(
        @Body cuerpo: IniciarRequest,
        @Query("accion") accion: String = "iniciar"
    ): IniciarResponse                       // devuelve el id_intento creado

    // 4. Enviar la opción elegida. El servidor valida y guarda
    @POST("api.php")
    suspend fun responder(
        @Body cuerpo: ResponderRequest,
        @Query("accion") accion: String = "responder"
    ): ResponderResponse                     // devuelve si acertó y los puntos

    // 5. Cerrar el intento y sumar el puntaje
    @POST("api.php")
    suspend fun finalizar(
        @Body cuerpo: FinalizarRequest,
        @Query("accion") accion: String = "finalizar"
    ): FinalizarResponse                     // devuelve el puntaje total

    // 6. Datos de la pantalla final. URL: api.php?id_intento=5&accion=resultado
    @GET("api.php")
    suspend fun resultado(
        @Query("id_intento") idIntento: Int,
        @Query("accion") accion: String = "resultado"
    ): Resultado
}

// ───────── PARTE 2: EL CLIENTE ─────────
// "object" = existe una sola instancia en toda la app
object RetrofitClient {
    // Emulador de Android Studio: 10.0.2.2 apunta al localhost de tu PC.
    // Celular real: usa la IP de tu PC en la red WiFi, por ejemplo "http://192.168.1.15/smartia/api/".
    private const val BASE_URL = "http://10.0.2.2/smartia/api/"

    // "by lazy": el cliente se crea la primera vez que se usa, no antes
    val api: SmartiaApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)                                   // dirección del servidor
            .addConverterFactory(GsonConverterFactory.create())  // convierte JSON <-> objetos Kotlin
            .build()                                             // termina de configurar
            .create(SmartiaApi::class.java)                      // genera el código de la interfaz
    }
}

// ───────── PARTE 3: MENSAJES DE ERROR ─────────
// Convierte un error de red o de la API en un mensaje para el estudiante.
fun mensajeDeError(e: Throwable): String = when (e) {

    // El servidor respondió, pero con error (400, 404, 409, 500...)
    is HttpException -> try {
        val cuerpo = e.response()?.errorBody()?.string()   // lee el texto del error
        // Lo convierte a ErrorApi y saca el mensaje; si no hay, muestra el código
        Gson().fromJson(cuerpo, ErrorApi::class.java)?.error ?: "Error del servidor (${e.code()})"
    } catch (x: Exception) {
        "Error del servidor (${e.code()})"                  // si no se pudo leer el cuerpo
    }

    // Ni siquiera llegó al servidor (XAMPP apagado, sin red, timeout)
    is IOException -> "No se pudo conectar con el servidor. (${e.javaClass.simpleName}: ${e.message})"

    // Cualquier otro error
    else -> e.message ?: "Ocurrió un error inesperado."
}