package com.smartia.app.data

import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.util.concurrent.TimeUnit

// ───────── PARTE 1: LOS ENDPOINTS DE SPRING BOOT ─────────
interface SmartiaApi {

    // 1. Lista de concursos activos
    @GET("concursos")
    suspend fun concursos(): List<ConcursoResumen>

    // 2. Preguntas (con opciones) de un concurso
    @GET("concursos/{id}/preguntas")
    suspend fun preguntas(@Path("id") idConcurso: Int): List<Pregunta>

    // 3. Empezar un intento
    @POST("intentos")
    suspend fun iniciar(@Body cuerpo: IniciarRequest): IniciarResponse

    // 4. Enviar la opcion elegida (el servidor valida y suma el puntaje)
    @POST("intentos/{id}/respuestas")
    suspend fun responder(
        @Path("id") idIntento: Int,
        @Body cuerpo: ResponderRequest
    ): ResponderResponse

    // 5. Cerrar el intento; devuelve el resultado completo
    @POST("intentos/{id}/finalizar")
    suspend fun finalizar(@Path("id") idIntento: Int): Resultado

    // 6. Volver a pedir el resultado
    @GET("intentos/{id}/resultado")
    suspend fun resultado(@Path("id") idIntento: Int): Resultado

    // 7. Retroalimentacion generada por la IA (Gemini)
    @POST("intentos/{id}/feedback")
    suspend fun feedback(@Path("id") idIntento: Int): FeedbackResponse
}

// ───────── PARTE 2: EL CLIENTE ─────────
object RetrofitClient {
    // Emulador: 10.0.2.2 apunta al localhost de tu PC. Puerto 8081 (el que usa el backend).
    // Celular real: usa la IP de tu PC, por ejemplo "http://192.168.1.15:8081/api/"
    private const val BASE_URL = "http://10.0.2.2:8081/api/"

    // La IA puede tardar varios segundos, por eso el timeout es de 60 s
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: SmartiaApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SmartiaApi::class.java)
    }
}

// ───────── PARTE 3: MENSAJES DE ERROR ─────────
fun mensajeDeError(e: Throwable): String = when (e) {

    // El servidor respondio con error (400, 404, 409, 502...)
    is HttpException -> try {
        val cuerpo = e.response()?.errorBody()?.string()
        val err = Gson().fromJson(cuerpo, ErrorApi::class.java)
        err?.message?.takeIf { it.isNotBlank() }
            ?: err?.error
            ?: "Error del servidor (${e.code()})"
    } catch (x: Exception) {
        "Error del servidor (${e.code()})"
    }

    // No llego al servidor (backend apagado, sin red, timeout)
    is IOException -> "No se pudo conectar con el servidor. (${e.javaClass.simpleName}: ${e.message})"

    else -> e.message ?: "Ocurrio un error inesperado."
}