
package com.smartia.usuario.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ResultadoConcursoService {

    private final JdbcTemplate jdbc;

    public ResultadoConcursoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ResultadoDTO(
        Long idIntento,
        String estado,
        BigDecimal puntaje,
        int respuestasCorrectas,
        int totalPreguntas
    ) {}

    private record IntentoInfo(
        Long idConcurso,
        String estado,
        boolean vencido
    ) {}

    // ========================================
    // 1. BUSCAR INTENTO Y COMPROBAR TIEMPO
    // ========================================

    private IntentoInfo buscarIntento(
            Long id,
            boolean bloquear) {

        String sql = """
            SELECT
                i.id_concurso,
                i.estado,
                CASE
                    WHEN NOW() >= DATE_ADD(
                        i.inicio_en,
                        INTERVAL c.duracion_minutos MINUTE
                    )
                    THEN 1
                    ELSE 0
                END AS vencido
            FROM intentos_concurso i
            JOIN concursos c
                ON c.id_concurso = i.id_concurso
            WHERE i.id_intento = ?
            """ + (bloquear ? " FOR UPDATE" : "");

        List<IntentoInfo> datos = jdbc.query(
            sql,
            (rs, fila) -> new IntentoInfo(
                rs.getLong("id_concurso"),
                rs.getString("estado"),
                rs.getBoolean("vencido")
            ),
            id
        );

        if (datos.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Intento inexistente"
            );
        }

        return datos.get(0);
    }

    // ========================================
    // 2. CALCULAR PUNTAJE
    // ========================================

    private ResultadoDTO calcular(Long intentoId) {

        return jdbc.queryForObject("""
            SELECT
                COALESCE(SUM(
                    CASE
                        WHEN o.es_correcta = 1
                        THEN p.puntos
                        ELSE 0
                    END
                ), 0) AS puntaje,

                COALESCE(SUM(
                    CASE
                        WHEN o.es_correcta = 1
                        THEN 1
                        ELSE 0
                    END
                ), 0) AS correctas,

                COUNT(*) AS total

            FROM intentos_concurso i

            JOIN concurso_preguntas cp
                ON cp.id_concurso = i.id_concurso

            JOIN preguntas p
                ON p.id_pregunta = cp.id_pregunta

            LEFT JOIN respuestas_concurso r
                ON r.id_intento = i.id_intento
               AND r.id_pregunta = p.id_pregunta

            LEFT JOIN opciones o
                ON o.id_opcion = r.id_opcion

            WHERE i.id_intento = ?
            """,
            (rs, fila) -> new ResultadoDTO(
                intentoId,
                "FINALIZADO",
                rs.getBigDecimal("puntaje"),
                rs.getInt("correctas"),
                rs.getInt("total")
            ),
            intentoId
        );
    }

    // ========================================
    // 3. CERRAR Y GUARDAR RESULTADO
    // ========================================

    private ResultadoDTO cerrarIntento(Long intentoId) {

        ResultadoDTO resultado = calcular(intentoId);

        jdbc.update("""
            UPDATE intentos_concurso
            SET estado = 'FINALIZADO',
                fin_en = NOW(),
                puntaje_obtenido = ?
            WHERE id_intento = ?
              AND estado = 'EN_CURSO'
            """,
            resultado.puntaje(),
            intentoId
        );

        return resultado;
    }

    // ========================================
    // 4. CERRAR SI EL TIEMPO VENCIÓ
    // ========================================

    @Transactional
    public boolean cerrarSiVencido(Long intentoId) {

        IntentoInfo intento = buscarIntento(
            intentoId, true
        );

        if (!"EN_CURSO".equals(intento.estado())) {
            return false;
        }

        if (intento.vencido()) {
            cerrarIntento(intentoId);
            return true;
        }

        return false;
    }

    // ========================================
    // 5. GUARDAR RESPUESTA
    // ========================================

    @Transactional(
        noRollbackFor = ResponseStatusException.class
    )
    public void responder(
            Long intentoId,
            Long preguntaId,
            Long opcionId) {

        IntentoInfo intento = buscarIntento(
            intentoId, true
        );

        if (!"EN_CURSO".equals(intento.estado())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El intento ya esta cerrado"
            );
        }

        if (intento.vencido()) {

            cerrarIntento(intentoId);

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Tiempo agotado. Concurso finalizado"
            );
        }

        Integer opcionValida = jdbc.queryForObject("""
            SELECT COUNT(*)

            FROM concurso_preguntas cp

            JOIN preguntas p
                ON p.id_pregunta = cp.id_pregunta

            JOIN opciones o
                ON o.id_pregunta = p.id_pregunta

            WHERE cp.id_concurso = ?
              AND cp.id_pregunta = ?
              AND o.id_opcion = ?
              AND p.activa = TRUE
            """,
            Integer.class,
            intento.idConcurso(),
            preguntaId,
            opcionId
        );

        if (opcionValida == null || opcionValida == 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Pregunta u opcion no valida"
            );
        }

        // Segunda comprobacion del plazo
        // antes de registrar la respuesta.
        Integer guardada = jdbc.update("""
            INSERT INTO respuestas_concurso
                (id_intento, id_pregunta, id_opcion)
            SELECT ?, ?, ?
            FROM intentos_concurso i
            JOIN concursos c
                ON c.id_concurso = i.id_concurso
            WHERE i.id_intento = ?
              AND i.estado = 'EN_CURSO'
              AND NOW() < DATE_ADD(
                  i.inicio_en,
                  INTERVAL c.duracion_minutos MINUTE
              )
            ON DUPLICATE KEY UPDATE
                id_opcion = VALUES(id_opcion),
                respondida_en = CURRENT_TIMESTAMP
            """,
            intentoId,
            preguntaId,
            opcionId,
            intentoId
        );

        if (guardada == 0) {
            // Puede ser una respuesta sin cambios,
            // o que el plazo haya vencido.
            IntentoInfo actual = buscarIntento(
                intentoId, false
            );

            if (actual.vencido()) {
                cerrarIntento(intentoId);

                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tiempo agotado. Concurso finalizado"
                );
            }
        }
    }

    // ========================================
    // 6. FINALIZAR MANUALMENTE
    // ========================================

    @Transactional
    public ResultadoDTO finalizar(Long intentoId) {

        IntentoInfo intento = buscarIntento(
            intentoId, true
        );

        if (!"EN_CURSO".equals(intento.estado())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El intento ya esta cerrado"
            );
        }

        return cerrarIntento(intentoId);
    }

    // ========================================
    // 7. CONSULTAR RESULTADO
    // ========================================

    @Transactional
    public ResultadoDTO resultado(Long intentoId) {

        IntentoInfo intento = buscarIntento(
            intentoId, true
        );

        if ("EN_CURSO".equals(intento.estado())) {

            if (intento.vencido()) {
                return cerrarIntento(intentoId);
            }

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El concurso aun no ha terminado"
            );
        }

        if (!"FINALIZADO".equals(intento.estado())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Intento anulado"
            );
        }

        return calcular(intentoId);
    }
}
