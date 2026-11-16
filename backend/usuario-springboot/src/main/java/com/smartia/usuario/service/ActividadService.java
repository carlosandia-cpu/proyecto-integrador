
package com.smartia.usuario.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.List;

@Service
public class ActividadService {

    private final JdbcTemplate jdbc;

    public ActividadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // DTO: información que puede recibir el frontend
    public record OpcionDTO(Long idOpcion, String texto) {}

    public record PreguntaDTO(
        Long idPregunta,
        String enunciado,
        int puntos,
        List<OpcionDTO> opciones
    ) {}

    public record InicioDTO(
        Long idIntentoActividad,
        Long idEstudiante,
        Long idActividad,
        String estado
    ) {}

    public record ResultadoDTO(
        Long idIntentoActividad,
        String estado,
        BigDecimal puntaje,
        int correctas,
        int incorrectas,
        int sinResponder,
        int totalPreguntas
    ) {}

    private record IntentoInfo(
        Long idActividad,
        String estado
    ) {}

    // 1. Consultar preguntas sin revelar la respuesta correcta
    public List<PreguntaDTO> preguntas(Long actividadId) {

        verificarActividad(actividadId);

        return jdbc.query("""
            SELECT id_pregunta, enunciado, puntos
            FROM preguntas
            WHERE id_actividad = ?
              AND activa = TRUE
            ORDER BY id_pregunta
            """,
            (rs, fila) -> {
                long preguntaId = rs.getLong("id_pregunta");

                List<OpcionDTO> opciones = jdbc.query("""
                    SELECT id_opcion, texto
                    FROM opciones
                    WHERE id_pregunta = ?
                    ORDER BY orden
                    """,
                    (op, i) -> new OpcionDTO(
                        op.getLong("id_opcion"),
                        op.getString("texto")
                    ),
                    preguntaId
                );

                return new PreguntaDTO(
                    preguntaId,
                    rs.getString("enunciado"),
                    rs.getInt("puntos"),
                    opciones
                );
            },
            actividadId
        );
    }

    // 2. Iniciar una actividad
    @Transactional
    public InicioDTO iniciar(Long estudianteId, Long actividadId) {

        // Bloqueo para evitar dos inicios simultáneos
        List<Long> estudiantes = jdbc.query("""
            SELECT id_estudiante
            FROM estudiantes
            WHERE id_estudiante = ?
            FOR UPDATE
            """,
            (rs, i) -> rs.getLong(1),
            estudianteId
        );

        if (estudiantes.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Estudiante no encontrado"
            );
        }

        verificarActividad(actividadId);

        Integer cantidad = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM preguntas
            WHERE id_actividad = ? AND activa = TRUE
            """, Integer.class, actividadId);

        if (cantidad == null || cantidad == 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Actividad sin preguntas"
            );
        }

        Integer activos = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM intentos_actividad
            WHERE id_estudiante = ?
              AND id_actividad = ?
              AND estado = 'EN_CURSO'
            """, Integer.class, estudianteId, actividadId);

        if (activos != null && activos > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Ya tienes esta actividad en curso"
            );
        }

        GeneratedKeyHolder key = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                INSERT INTO intentos_actividad
                (id_estudiante, id_actividad, estado)
                VALUES (?, ?, 'EN_CURSO')
                """, Statement.RETURN_GENERATED_KEYS);

            ps.setLong(1, estudianteId);
            ps.setLong(2, actividadId);
            return ps;
        }, key);

        if (key.getKey() == null) {
            throw new IllegalStateException(
                "No se pudo obtener el ID del intento"
            );
        }

        return new InicioDTO(
            key.getKey().longValue(),
            estudianteId,
            actividadId,
            "EN_CURSO"
        );
    }

    // 3. Registrar o modificar una respuesta
    @Transactional
    public void responder(Long intentoId, Long preguntaId,
                          Long opcionId) {

        IntentoInfo intento = buscarIntento(intentoId, true);

        if (!"EN_CURSO".equals(intento.estado())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Actividad finalizada"
            );
        }

        Integer valida = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM preguntas p
            JOIN opciones o
                ON o.id_pregunta = p.id_pregunta
            WHERE p.id_actividad = ?
              AND p.id_pregunta = ?
              AND o.id_opcion = ?
              AND p.activa = TRUE
            """, Integer.class,
            intento.idActividad(), preguntaId, opcionId);

        if (valida == null || valida == 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Pregunta u opcion no valida"
            );
        }

        jdbc.update("""
            INSERT INTO respuestas_actividad
            (id_intento_actividad, id_pregunta, id_opcion)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                id_opcion = VALUES(id_opcion),
                respondida_en = CURRENT_TIMESTAMP
            """, intentoId, preguntaId, opcionId);
    }

    // 4. Finalizar y almacenar puntaje
    @Transactional
    public ResultadoDTO finalizar(Long intentoId) {

        IntentoInfo intento = buscarIntento(intentoId, true);

        if (!"EN_CURSO".equals(intento.estado())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Actividad ya finalizada"
            );
        }

        ResultadoDTO resultado = calcular(intentoId);

        jdbc.update("""
            UPDATE intentos_actividad
            SET estado = 'FINALIZADO',
                fin_en = NOW(),
                puntaje_obtenido = ?
            WHERE id_intento_actividad = ?
            """, resultado.puntaje(), intentoId);

        return resultado;
    }

    // 5. Recuperar el resultado posteriormente
    public ResultadoDTO resultado(Long intentoId) {

        IntentoInfo intento = buscarIntento(intentoId, false);

        if (!"FINALIZADO".equals(intento.estado())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Actividad sin finalizar"
            );
        }

        return calcular(intentoId);
    }

    // Calcular utilizando las respuestas de MariaDB
    private ResultadoDTO calcular(Long intentoId) {

        return jdbc.queryForObject("""
            SELECT
                COALESCE(SUM(
                    CASE WHEN o.es_correcta = 1
                    THEN p.puntos ELSE 0 END
                ), 0) AS puntaje,

                COALESCE(SUM(
                    CASE WHEN o.es_correcta = 1
                    THEN 1 ELSE 0 END
                ), 0) AS correctas,

                COALESCE(SUM(
                    CASE WHEN r.id_respuesta_actividad IS NOT NULL
                         AND o.es_correcta = 0
                    THEN 1 ELSE 0 END
                ), 0) AS incorrectas,

                COALESCE(SUM(
                    CASE WHEN r.id_respuesta_actividad IS NULL
                    THEN 1 ELSE 0 END
                ), 0) AS sin_responder,

                COUNT(*) AS total

            FROM intentos_actividad i
            JOIN preguntas p
                ON p.id_actividad = i.id_actividad
               AND p.activa = TRUE
            LEFT JOIN respuestas_actividad r
                ON r.id_intento_actividad =
                   i.id_intento_actividad
               AND r.id_pregunta = p.id_pregunta
            LEFT JOIN opciones o
                ON o.id_opcion = r.id_opcion

            WHERE i.id_intento_actividad = ?
            """,
            (rs, fila) -> new ResultadoDTO(
                intentoId,
                "FINALIZADO",
                rs.getBigDecimal("puntaje"),
                rs.getInt("correctas"),
                rs.getInt("incorrectas"),
                rs.getInt("sin_responder"),
                rs.getInt("total")
            ),
            intentoId
        );
    }

    private IntentoInfo buscarIntento(Long id, boolean bloquear) {

        String sql = """
            SELECT id_actividad, estado
            FROM intentos_actividad
            WHERE id_intento_actividad = ?
            """ + (bloquear ? " FOR UPDATE" : "");

        List<IntentoInfo> datos = jdbc.query(
            sql,
            (rs, fila) -> new IntentoInfo(
                rs.getLong("id_actividad"),
                rs.getString("estado")
            ),
            id
        );

        if (datos.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Intento no encontrado"
            );
        }

        return datos.get(0);
    }

    private void verificarActividad(Long id) {

        Integer existe = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM actividades
            WHERE id_actividad = ?
              AND publicada = TRUE
            """, Integer.class, id);

        if (existe == null || existe == 0) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Actividad no disponible"
            );
        }
    }
}
