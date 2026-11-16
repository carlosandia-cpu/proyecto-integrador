
package com.smartia.usuario.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocenteService {

    private final JdbcTemplate jdbc;

    public DocenteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record EstudianteDTO(
        Long idEstudiante,
        String nombre,
        String apellidos,
        int grado,
        String seccion
    ) {}

    public record ResultadoDTO(
        Long idIntento,
        String concurso,
        int numeroIntento,
        BigDecimal puntaje,
        String estado,
        LocalDateTime fechaFinalizacion
    ) {}

    public List<EstudianteDTO> estudiantes(Long idDocente) {

        verificarDocente(idDocente);

        return jdbc.query("""
            SELECT e.id_estudiante, u.nombre,
                   u.apellidos, e.grado, e.seccion
            FROM docente_estudiante de
            JOIN estudiantes e
                ON e.id_estudiante = de.id_estudiante
            JOIN usuarios u
                ON u.id_usuario = e.id_usuario
            WHERE de.id_docente = ?
            ORDER BY u.apellidos, u.nombre
            """,
            (rs, fila) -> new EstudianteDTO(
                rs.getLong("id_estudiante"),
                rs.getString("nombre"),
                rs.getString("apellidos"),
                rs.getInt("grado"),
                rs.getString("seccion")
            ),
            idDocente
        );
    }

    public List<ResultadoDTO> resultados(
            Long idDocente, Long idEstudiante) {

        verificarDocente(idDocente);

        Integer asignado = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM docente_estudiante
            WHERE id_docente = ?
              AND id_estudiante = ?
            """,
            Integer.class, idDocente, idEstudiante
        );

        if (asignado == null || asignado == 0) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Estudiante no asignado al docente"
            );
        }

        return jdbc.query("""
            SELECT i.id_intento, c.titulo,
                   i.numero_intento,
                   i.puntaje_obtenido,
                   i.estado, i.fin_en
            FROM intentos_concurso i
            JOIN concursos c
                ON c.id_concurso = i.id_concurso
            WHERE i.id_estudiante = ?
              AND i.estado = 'FINALIZADO'
            ORDER BY i.fin_en DESC, i.id_intento DESC
            """,
            (rs, fila) -> new ResultadoDTO(
                rs.getLong("id_intento"),
                rs.getString("titulo"),
                rs.getInt("numero_intento"),
                rs.getBigDecimal("puntaje_obtenido"),
                rs.getString("estado"),
                rs.getTimestamp("fin_en") == null
                    ? null
                    : rs.getTimestamp("fin_en").toLocalDateTime()
            ),
            idEstudiante
        );
    }

    private void verificarDocente(Long idDocente) {
        Integer existe = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM docentes
            WHERE id_docente = ?
            """,
            Integer.class, idDocente
        );

        if (existe == null || existe == 0) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Docente no encontrado"
            );
        }
    }

    // DTO para el historial académico unificado
    public record HistorialDTO(
        Long idIntento,
        String tipo,
        String actividad,
        BigDecimal puntaje,
        String estado,
        LocalDateTime fechaFinalizacion
    ) {}

    // Consulta concursos y prácticas educativas
    public List<HistorialDTO> historialCompleto(
            Long idDocente, Long idEstudiante) {

        verificarDocente(idDocente);

        Integer asignado = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM docente_estudiante
            WHERE id_docente = ?
              AND id_estudiante = ?
            """,
            Integer.class,
            idDocente, idEstudiante
        );

        if (asignado == null || asignado == 0) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Estudiante no asignado al docente"
            );
        }

        String sql = """
            SELECT
                i.id_intento AS id_intento,
                'CONCURSO' AS tipo,
                c.titulo AS actividad,
                i.puntaje_obtenido AS puntaje,
                i.estado,
                i.fin_en AS fecha_finalizacion

            FROM intentos_concurso i

            JOIN concursos c
                ON c.id_concurso = i.id_concurso

            WHERE i.id_estudiante = ?
              AND i.estado = 'FINALIZADO'

            UNION ALL

            SELECT
                ia.id_intento_actividad AS id_intento,
                'PRACTICA' AS tipo,
                a.titulo AS actividad,
                ia.puntaje_obtenido AS puntaje,
                ia.estado,
                ia.fin_en AS fecha_finalizacion

            FROM intentos_actividad ia

            JOIN actividades a
                ON a.id_actividad = ia.id_actividad

            WHERE ia.id_estudiante = ?
              AND ia.estado = 'FINALIZADO'

            ORDER BY fecha_finalizacion DESC,
                     tipo, id_intento DESC
            """;

        return jdbc.query(
            sql,
            (rs, fila) -> new HistorialDTO(
                rs.getLong("id_intento"),
                rs.getString("tipo"),
                rs.getString("actividad"),
                rs.getBigDecimal("puntaje"),
                rs.getString("estado"),
                rs.getTimestamp("fecha_finalizacion") == null
                    ? null
                    : rs.getTimestamp("fecha_finalizacion")
                        .toLocalDateTime()
            ),
            idEstudiante,
            idEstudiante
        );
    }
}
