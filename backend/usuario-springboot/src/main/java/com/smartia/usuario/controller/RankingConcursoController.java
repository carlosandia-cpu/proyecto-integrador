
package com.smartia.usuario.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/intentos/ranking")
public class RankingConcursoController {

    private final JdbcTemplate jdbc;

    public RankingConcursoController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PosicionDTO(
        int posicion,
        Long idEstudiante,
        String estudiante,
        BigDecimal mejorPuntaje,
        int intentosRealizados,
        long tiempoSegundos
    ) {}

    @GetMapping("/{idConcurso}")
    public List<PosicionDTO> obtenerRanking(
            @PathVariable Long idConcurso) {

        String sql = """
            SELECT
                mejores.id_estudiante,
                mejores.estudiante,
                mejores.puntaje_obtenido,
                mejores.tiempo_segundos,
                mejores.intentos_realizados
            FROM (
                SELECT
                    i.id_estudiante,
                    CONCAT(u.nombre, ' ', u.apellidos)
                        AS estudiante,
                    i.puntaje_obtenido,
                    TIMESTAMPDIFF(
                        SECOND,
                        i.inicio_en,
                        i.fin_en
                    ) AS tiempo_segundos,

                    COUNT(*) OVER (
                        PARTITION BY i.id_estudiante
                    ) AS intentos_realizados,

                    ROW_NUMBER() OVER (
                        PARTITION BY i.id_estudiante
                        ORDER BY
                            i.puntaje_obtenido DESC,
                            TIMESTAMPDIFF(
                                SECOND,
                                i.inicio_en,
                                i.fin_en
                            ) ASC,
                            i.id_intento ASC
                    ) AS numero_fila

                FROM intentos_concurso i
                INNER JOIN estudiantes e
                    ON e.id_estudiante = i.id_estudiante
                INNER JOIN usuarios u
                    ON u.id_usuario = e.id_usuario
                WHERE i.id_concurso = ?
                  AND i.estado = 'FINALIZADO'
            ) mejores
            WHERE mejores.numero_fila = 1
            ORDER BY
                mejores.puntaje_obtenido DESC,
                mejores.tiempo_segundos ASC,
                mejores.id_estudiante ASC
            """;

        List<PosicionDTO> participantes = jdbc.query(
            sql,
            (rs, numero) -> new PosicionDTO(
                numero + 1,
                rs.getLong("id_estudiante"),
                rs.getString("estudiante"),
                rs.getBigDecimal("puntaje_obtenido"),
                rs.getInt("intentos_realizados"),
                rs.getLong("tiempo_segundos")
            ),
            idConcurso
        );

        return participantes;
    }
}
