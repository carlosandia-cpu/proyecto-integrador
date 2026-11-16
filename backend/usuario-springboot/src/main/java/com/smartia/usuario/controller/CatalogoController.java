
package com.smartia.usuario.controller;

import com.smartia.usuario.service.AccesoEstudianteService;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogoController {

    private final JdbcTemplate jdbc;
    private final AccesoEstudianteService acceso;

    public CatalogoController(
            JdbcTemplate jdbc,
            AccesoEstudianteService acceso) {
        this.jdbc = jdbc;
        this.acceso = acceso;
    }

    public record ActividadDTO(
        Long idActividad,
        String titulo,
        String descripcion,
        String area,
        String dificultad,
        String contenidoLectura
    ) {}

    public record ConcursoDTO(
        Long idConcurso,
        String titulo,
        String descripcion,
        int duracionMinutos,
        int maxIntentos
    ) {}

    public record OpcionDTO(
        Long idOpcion,
        String texto
    ) {}

    public record PreguntaDTO(
        Long idPregunta,
        String enunciado,
        int puntos,
        List<OpcionDTO> opciones
    ) {}

    // ===========================================
    // ACTIVIDADES DE PRACTICA
    // ===========================================

    @GetMapping("/actividades")
    public List<ActividadDTO> listarActividades() {

        String sql = """
            SELECT
                a.id_actividad,
                a.titulo,
                a.descripcion,
                ar.nombre AS area,
                a.dificultad,
                a.contenido_lectura
            FROM actividades a
            JOIN areas ar
                ON a.id_area = ar.id_area
            WHERE a.publicada = TRUE
            ORDER BY a.id_actividad
            """;

        return jdbc.query(sql, (rs, rowNum) ->
            new ActividadDTO(
                rs.getLong("id_actividad"),
                rs.getString("titulo"),
                rs.getString("descripcion"),
                rs.getString("area"),
                rs.getString("dificultad"),
                rs.getString("contenido_lectura")
            )
        );
    }

    // ===========================================
    // CATALOGO PUBLICO DE CONCURSOS
    // NO DEVUELVE PREGUNTAS
    // ===========================================

    @GetMapping("/concursos")
    public List<ConcursoDTO> listarConcursos() {

        String sql = """
            SELECT
                id_concurso,
                titulo,
                descripcion,
                duracion_minutos,
                max_intentos
            FROM concursos
            WHERE estado = 'PUBLICADO'
              AND (
                  inicia_en IS NULL
                  OR inicia_en <= NOW()
              )
              AND (
                  termina_en IS NULL
                  OR termina_en >= NOW()
              )
            ORDER BY id_concurso
            """;

        return jdbc.query(sql, (rs, rowNum) ->
            new ConcursoDTO(
                rs.getLong("id_concurso"),
                rs.getString("titulo"),
                rs.getString("descripcion"),
                rs.getInt("duracion_minutos"),
                rs.getInt("max_intentos")
            )
        );
    }

    // ===========================================
    // PREGUNTAS DE UN INTENTO ACTIVO
    // GET /api/intentos/{id}/preguntas
    // ===========================================

    @GetMapping("/intentos/{id}/preguntas")
    public List<PreguntaDTO> preguntasDelIntento(
            @PathVariable Long id,
            Authentication authentication) {

        // Verificar que el intento pertenece
        // al estudiante autenticado.
        acceso.verificarIntentoConcurso(
            authentication, id
        );

        // El intento debe seguir EN_CURSO
        // y no haber agotado su duracion.
        Integer activo = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM intentos_concurso i
            JOIN concursos c
                ON c.id_concurso = i.id_concurso
            WHERE i.id_intento = ?
              AND i.estado = 'EN_CURSO'
              AND NOW() < DATE_ADD(
                  i.inicio_en,
                  INTERVAL c.duracion_minutos MINUTE
              )
            """,
            Integer.class,
            id
        );

        if (activo == null || activo == 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El intento no esta activo o el tiempo termino"
            );
        }

        // Obtener solamente las preguntas
        // relacionadas con este intento.
        List<PreguntaDTO> preguntas = jdbc.query("""
            SELECT
                p.id_pregunta,
                p.enunciado,
                p.puntos
            FROM intentos_concurso i
            JOIN concursos c
                ON c.id_concurso = i.id_concurso
            JOIN concurso_preguntas cp
                ON cp.id_concurso = i.id_concurso
            JOIN preguntas p
                ON p.id_pregunta = cp.id_pregunta
            WHERE i.id_intento = ?
              AND i.estado = 'EN_CURSO'
              AND NOW() < DATE_ADD(
                  i.inicio_en,
                  INTERVAL c.duracion_minutos MINUTE
              )
              AND p.activa = TRUE
            ORDER BY cp.orden
            """,
            (rs, rowNum) -> {

                Long idPregunta =
                    rs.getLong("id_pregunta");

                List<OpcionDTO> opciones = jdbc.query("""
                    SELECT
                        id_opcion,
                        texto
                    FROM opciones
                    WHERE id_pregunta = ?
                    ORDER BY orden
                    """,
                    (op, index) -> new OpcionDTO(
                        op.getLong("id_opcion"),
                        op.getString("texto")
                    ),
                    idPregunta
                );

                return new PreguntaDTO(
                    idPregunta,
                    rs.getString("enunciado"),
                    rs.getInt("puntos"),
                    opciones
                );
            },
            id
        );

        return preguntas;
    }
}
