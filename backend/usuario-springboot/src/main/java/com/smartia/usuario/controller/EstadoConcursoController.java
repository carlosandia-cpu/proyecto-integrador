
package com.smartia.usuario.controller;

import com.smartia.usuario.service.ResultadoConcursoService;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/intentos")
public class EstadoConcursoController {

    private final JdbcTemplate jdbc;
    private final ResultadoConcursoService resultados;

    public EstadoConcursoController(
            JdbcTemplate jdbc,
            ResultadoConcursoService resultados) {
        this.jdbc = jdbc;
        this.resultados = resultados;
    }

    public record EstadoDTO(
        Long idConcurso,
        int maxIntentos,
        int intentosUtilizados,
        int intentosDisponibles,
        boolean puedeParticipar,
        Long idIntentoActivo,
        Integer numeroIntento,
        int segundosRestantes,
        Map<Long, Long> respuestasGuardadas
    ) {}

    @GetMapping("/estado/{idConcurso}")
    public EstadoDTO consultarEstado(
            @PathVariable Long idConcurso,
            Authentication authentication) {

        if (authentication == null ||
            !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Debes iniciar sesion"
            );
        }

        // Obtener el estudiante a partir de la cuenta
        // autenticada. Nunca confiar en un ID enviado
        // libremente por el navegador.
        List<Long> estudiantes = jdbc.query("""
            SELECT e.id_estudiante
            FROM estudiantes e
            JOIN usuarios u
                ON u.id_usuario = e.id_usuario
            WHERE u.correo = ?
              AND u.rol = 'ESTUDIANTE'
              AND u.activo = TRUE
            """,
            (rs, fila) -> rs.getLong("id_estudiante"),
            authentication.getName()
        );

        if (estudiantes.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "No existe un estudiante asociado"
            );
        }

        Long idEstudiante = estudiantes.get(0);

        // Información del concurso.
        List<Integer> limites = jdbc.query("""
            SELECT max_intentos
            FROM concursos
            WHERE id_concurso = ?
            """,
            (rs, fila) -> rs.getInt("max_intentos"),
            idConcurso
        );

        if (limites.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Concurso no encontrado"
            );
        }

        int maxIntentos = limites.get(0);

        // Buscar intentos vencidos que todavía
        // figuran como EN_CURSO.
        List<Long> vencidos = jdbc.query("""
            SELECT i.id_intento
            FROM intentos_concurso i
            JOIN concursos c
                ON c.id_concurso = i.id_concurso
            WHERE i.id_estudiante = ?
              AND i.id_concurso = ?
              AND i.estado = 'EN_CURSO'
              AND NOW() >= DATE_ADD(
                  i.inicio_en,
                  INTERVAL c.duracion_minutos MINUTE
              )
            """,
            (rs, fila) -> rs.getLong("id_intento"),
            idEstudiante,
            idConcurso
        );

        // Utilizar la lógica de calificación existente
        // para cerrar los intentos que hayan vencido.
        for (Long idVencido : vencidos) {
            resultados.cerrarSiVencido(idVencido);
        }

        // Contar todos los intentos, igual que hace
        // ConcursoIntentoService.iniciar().
        Integer total = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM intentos_concurso
            WHERE id_estudiante = ?
              AND id_concurso = ?
            """,
            Integer.class,
            idEstudiante,
            idConcurso
        );

        int utilizados = total == null ? 0 : total;
        int disponibles = Math.max(0, maxIntentos - utilizados);

        // Buscar un intento que todavía esté activo.
        List<IntentoActivo> activos = jdbc.query("""
            SELECT
                i.id_intento,
                i.numero_intento,
                GREATEST(
                    0,
                    TIMESTAMPDIFF(
                        SECOND,
                        NOW(),
                        DATE_ADD(
                            i.inicio_en,
                            INTERVAL c.duracion_minutos MINUTE
                        )
                    )
                ) AS segundos_restantes
            FROM intentos_concurso i
            JOIN concursos c
                ON c.id_concurso = i.id_concurso
            WHERE i.id_estudiante = ?
              AND i.id_concurso = ?
              AND i.estado = 'EN_CURSO'
              AND NOW() < DATE_ADD(
                  i.inicio_en,
                  INTERVAL c.duracion_minutos MINUTE
              )
            ORDER BY i.id_intento DESC
            LIMIT 1
            """,
            (rs, fila) -> new IntentoActivo(
                rs.getLong("id_intento"),
                rs.getInt("numero_intento"),
                rs.getInt("segundos_restantes")
            ),
            idEstudiante,
            idConcurso
        );

        Long idIntentoActivo = null;
        Integer numeroIntento = null;
        int segundosRestantes = 0;

        Map<Long, Long> respuestas = new LinkedHashMap<>();

        if (!activos.isEmpty()) {
            IntentoActivo activo = activos.get(0);

            idIntentoActivo = activo.idIntento();
            numeroIntento = activo.numeroIntento();
            segundosRestantes = activo.segundosRestantes();

            // Recuperar las opciones previamente guardadas.
            jdbc.query("""
                SELECT id_pregunta, id_opcion
                FROM respuestas_concurso
                WHERE id_intento = ?
                ORDER BY id_pregunta
                """,
                rs -> {
                    respuestas.put(
                        rs.getLong("id_pregunta"),
                        rs.getLong("id_opcion")
                    );
                },
                idIntentoActivo
            );
        }

        // Comprobar también la disponibilidad temporal
        // del concurso antes de habilitar Participar.
        Integer disponibleAhora = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM concursos
            WHERE id_concurso = ?
              AND estado = 'PUBLICADO'
              AND (inicia_en IS NULL OR inicia_en <= NOW())
              AND (termina_en IS NULL OR termina_en >= NOW())
            """,
            Integer.class,
            idConcurso
        );

        boolean puedeParticipar =
            disponibles > 0
            && idIntentoActivo == null
            && disponibleAhora != null
            && disponibleAhora > 0;

        return new EstadoDTO(
            idConcurso,
            maxIntentos,
            utilizados,
            disponibles,
            puedeParticipar,
            idIntentoActivo,
            numeroIntento,
            segundosRestantes,
            respuestas
        );
    }

    private record IntentoActivo(
        Long idIntento,
        int numeroIntento,
        int segundosRestantes
    ) {}
}
