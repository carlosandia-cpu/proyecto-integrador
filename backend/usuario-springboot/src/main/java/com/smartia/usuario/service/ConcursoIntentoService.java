
package com.smartia.usuario.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Service
public class ConcursoIntentoService {

    private final JdbcTemplate jdbc;
    private final ResultadoConcursoService resultados;

    public ConcursoIntentoService(
            JdbcTemplate jdbc,
            ResultadoConcursoService resultados) {

        this.jdbc = jdbc;
        this.resultados = resultados;
    }

    public record IntentoDTO(
        Long idIntento,
        Long idEstudiante,
        Long idConcurso,
        int numeroIntento,
        String estado
    ) {}

    private record ConcursoDatos(
        int maxIntentos
    ) {}

    // ========================================
    // 1. INICIAR UN CONCURSO
    // ========================================

    @Transactional
    public IntentoDTO iniciar(
            Long estudianteId,
            Long concursoId) {

        // Bloquear estudiante para evitar
        // solicitudes simultaneas.
        List<Long> estudiantes = jdbc.query("""
            SELECT id_estudiante
            FROM estudiantes
            WHERE id_estudiante = ?
            FOR UPDATE
            """,
            (rs, fila) -> rs.getLong(1),
            estudianteId
        );

        if (estudiantes.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Estudiante no encontrado"
            );
        }

        // ====================================
        // 2. COMPROBAR DISPONIBILIDAD
        // ====================================

        List<ConcursoDatos> concursos = jdbc.query("""
            SELECT max_intentos
            FROM concursos
            WHERE id_concurso = ?
              AND estado = 'PUBLICADO'
              AND (
                  inicia_en IS NULL
                  OR inicia_en <= NOW()
              )
              AND (
                  termina_en IS NULL
                  OR termina_en >= NOW()
              )
            """,
            (rs, fila) -> new ConcursoDatos(
                rs.getInt("max_intentos")
            ),
            concursoId
        );

        if (concursos.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Concurso no disponible"
            );
        }

        // ====================================
        // 3. CERRAR INTENTOS VENCIDOS
        // ====================================

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
            estudianteId,
            concursoId
        );

        for (Long idIntento : vencidos) {
            resultados.cerrarSiVencido(idIntento);
        }

        // ====================================
        // 4. IMPEDIR INTENTOS SIMULTANEOS
        // ====================================

        Integer enCurso = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM intentos_concurso
            WHERE id_estudiante = ?
              AND id_concurso = ?
              AND estado = 'EN_CURSO'
            """,
            Integer.class,
            estudianteId,
            concursoId
        );

        if (enCurso != null && enCurso > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Ya tienes un intento en curso"
            );
        }

        // ====================================
        // 5. COMPROBAR MAXIMO DE INTENTOS
        // ====================================

        Integer utilizados = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM intentos_concurso
            WHERE id_estudiante = ?
              AND id_concurso = ?
            """,
            Integer.class,
            estudianteId,
            concursoId
        );

        int siguiente =
            (utilizados == null ? 0 : utilizados) + 1;

        if (siguiente > concursos.get(0).maxIntentos()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Limite de intentos alcanzado"
            );
        }

        // ====================================
        // 6. CREAR NUEVO INTENTO
        // ====================================

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {

            PreparedStatement ps =
                connection.prepareStatement("""
                    INSERT INTO intentos_concurso
                    (
                        id_estudiante,
                        id_concurso,
                        numero_intento,
                        inicio_en,
                        estado,
                        puntaje_obtenido
                    )
                    VALUES (
                        ?, ?, ?, NOW(),
                        'EN_CURSO', 0
                    )
                    """,
                    Statement.RETURN_GENERATED_KEYS
                );

            ps.setLong(1, estudianteId);
            ps.setLong(2, concursoId);
            ps.setInt(3, siguiente);

            return ps;

        }, keyHolder);

        Number clave = keyHolder.getKey();

        if (clave == null) {
            throw new IllegalStateException(
                "No se genero el ID del intento"
            );
        }

        return new IntentoDTO(
            clave.longValue(),
            estudianteId,
            concursoId,
            siguiente,
            "EN_CURSO"
        );
    }
}
