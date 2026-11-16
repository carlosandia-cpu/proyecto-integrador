
package com.smartia.usuario.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DiagnosticoService {

    private final JdbcTemplate jdbc;

    public DiagnosticoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Resultado del diagnóstico por área educativa
    public record AreaDiagnosticoDTO(
        String area,
        int totalPreguntas,
        int correctas,
        int incorrectas,
        int sinResponder,
        double porcentajeAciertos,
        String diagnostico
    ) {}

    // Resultado general del estudiante
    public record DiagnosticoDTO(
        Long idEstudiante,
        List<AreaDiagnosticoDTO> areas
    ) {}

    public DiagnosticoDTO obtenerDiagnostico(Long idEstudiante) {

        // 1. Comprobar que el estudiante existe
        Integer existe = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM estudiantes
            WHERE id_estudiante = ?
            """, Integer.class, idEstudiante);

        if (existe == null || existe == 0) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Estudiante no encontrado"
            );
        }

        // 2. Unificar resultados de concursos y prácticas
        String sql = """
            SELECT
                resultados.area,
                SUM(resultados.total) AS total_preguntas,
                SUM(resultados.correctas) AS correctas,
                SUM(resultados.incorrectas) AS incorrectas,
                SUM(resultados.sin_responder) AS sin_responder

            FROM (

                -- Resultados de concursos
                SELECT
                    a.id_area,
                    a.nombre AS area,
                    COUNT(*) AS total,

                    SUM(CASE
                        WHEN o.es_correcta = 1
                        THEN 1 ELSE 0
                    END) AS correctas,

                    SUM(CASE
                        WHEN r.id_respuesta IS NOT NULL
                         AND o.es_correcta = 0
                        THEN 1 ELSE 0
                    END) AS incorrectas,

                    SUM(CASE
                        WHEN r.id_respuesta IS NULL
                        THEN 1 ELSE 0
                    END) AS sin_responder

                FROM intentos_concurso i

                JOIN concurso_preguntas cp
                    ON cp.id_concurso = i.id_concurso

                JOIN preguntas p
                    ON p.id_pregunta = cp.id_pregunta

                JOIN areas a
                    ON a.id_area = p.id_area

                LEFT JOIN respuestas_concurso r
                    ON r.id_intento = i.id_intento
                   AND r.id_pregunta = p.id_pregunta

                LEFT JOIN opciones o
                    ON o.id_opcion = r.id_opcion

                WHERE i.id_estudiante = ?
                  AND i.estado = 'FINALIZADO'

                GROUP BY a.id_area, a.nombre

                UNION ALL

                -- Resultados de prácticas educativas
                SELECT
                    a.id_area,
                    a.nombre AS area,
                    COUNT(*) AS total,

                    SUM(CASE
                        WHEN o.es_correcta = 1
                        THEN 1 ELSE 0
                    END) AS correctas,

                    SUM(CASE
                        WHEN r.id_respuesta_actividad IS NOT NULL
                         AND o.es_correcta = 0
                        THEN 1 ELSE 0
                    END) AS incorrectas,

                    SUM(CASE
                        WHEN r.id_respuesta_actividad IS NULL
                        THEN 1 ELSE 0
                    END) AS sin_responder

                FROM intentos_actividad i

                JOIN preguntas p
                    ON p.id_actividad = i.id_actividad

                JOIN areas a
                    ON a.id_area = p.id_area

                LEFT JOIN respuestas_actividad r
                    ON r.id_intento_actividad =
                       i.id_intento_actividad
                   AND r.id_pregunta = p.id_pregunta

                LEFT JOIN opciones o
                    ON o.id_opcion = r.id_opcion

                WHERE i.id_estudiante = ?
                  AND i.estado = 'FINALIZADO'

                GROUP BY a.id_area, a.nombre

            ) resultados

            GROUP BY resultados.id_area, resultados.area
            ORDER BY resultados.area
            """;

        // 3. Ejecutar la consulta y procesar los resultados
        List<AreaDiagnosticoDTO> areas = jdbc.query(
            sql,
            (rs, fila) -> {

                int total = rs.getInt("total_preguntas");
                int correctas = rs.getInt("correctas");
                int incorrectas = rs.getInt("incorrectas");
                int sinResponder = rs.getInt("sin_responder");

                int respondidas = correctas + incorrectas;

                // Calcular porcentaje de aciertos
                double porcentaje = total == 0
                    ? 0
                    : Math.round(
                        correctas * 10000.0 / total
                    ) / 100.0;

                // 4. Clasificar el rendimiento preliminar
                String diagnostico;

                if (respondidas == 0) {
                    diagnostico = "SIN_RESPUESTAS";

                } else if (respondidas < 5) {
                    diagnostico = "DATOS_INSUFICIENTES";

                } else if (porcentaje >= 80) {
                    diagnostico = "FORTALEZA_PRELIMINAR";

                } else if (porcentaje >= 50) {
                    diagnostico = "EN_PROCESO";

                } else {
                    diagnostico = "REQUIERE_REFUERZO";
                }

                return new AreaDiagnosticoDTO(
                    rs.getString("area"),
                    total,
                    correctas,
                    incorrectas,
                    sinResponder,
                    porcentaje,
                    diagnostico
                );
            },
            idEstudiante,
            idEstudiante
        );

        return new DiagnosticoDTO(idEstudiante, areas);
    }
}
