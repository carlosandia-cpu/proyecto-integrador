
package com.smartia.usuario.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccesoDocenteService {

    private final JdbcTemplate jdbc;

    public AccesoDocenteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Comprobar que la cuenta pertenece al docente
    public void verificarDocente(
            Authentication auth,
            Long idDocente) {

        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Debes iniciar sesion"
            );
        }

        boolean esDocente = auth.getAuthorities()
            .stream()
            .anyMatch(a ->
                a.getAuthority().equals("ROLE_DOCENTE")
            );

        if (!esDocente) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Acceso exclusivo para docentes"
            );
        }

        Integer coincide = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM docentes d
            JOIN usuarios u
                ON u.id_usuario = d.id_usuario
            WHERE d.id_docente = ?
              AND u.correo = ?
              AND u.rol = 'DOCENTE'
            """,
            Integer.class,
            idDocente,
            auth.getName()
        );

        if (coincide == null || coincide == 0) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "No puedes acceder a este docente"
            );
        }
    }

    // Comprobar asignacion docente-estudiante
    public void verificarAsignacion(
            Authentication auth,
            Long idDocente,
            Long idEstudiante) {

        verificarDocente(auth, idDocente);

        Integer asignado = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM docente_estudiante
            WHERE id_docente = ?
              AND id_estudiante = ?
            """,
            Integer.class,
            idDocente,
            idEstudiante
        );

        if (asignado == null || asignado == 0) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "El estudiante no esta asignado al docente"
            );
        }
    }
}
