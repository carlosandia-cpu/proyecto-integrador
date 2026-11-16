
package com.smartia.usuario.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccesoEstudianteService {

    private final JdbcTemplate jdbc;

    public AccesoEstudianteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Verifica que el usuario sea un estudiante autenticado
    private void verificarEstudiante(Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Debes iniciar sesion"
            );
        }

        boolean esEstudiante = auth.getAuthorities()
            .stream()
            .anyMatch(a ->
                a.getAuthority().equals("ROLE_ESTUDIANTE")
            );

        if (!esEstudiante) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Acceso no autorizado"
            );
        }
    }

    // Comprueba que una cuenta sea dueña del estudiante
    public void verificarPropietario(
            Authentication auth, Long idEstudiante) {

        verificarEstudiante(auth);

        Integer cantidad = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM estudiantes e
            JOIN usuarios u
                ON u.id_usuario = e.id_usuario
            WHERE e.id_estudiante = ?
              AND u.correo = ?
            """,
            Integer.class,
            idEstudiante,
            auth.getName()
        );

        if (cantidad == null || cantidad == 0) {
            denegar();
        }
    }

    // Comprueba que el intento de concurso pertenezca
    // al estudiante autenticado
    public void verificarIntentoConcurso(
            Authentication auth, Long idIntento) {

        verificarEstudiante(auth);

        Integer cantidad = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM intentos_concurso i
            JOIN estudiantes e
                ON e.id_estudiante = i.id_estudiante
            JOIN usuarios u
                ON u.id_usuario = e.id_usuario
            WHERE i.id_intento = ?
              AND u.correo = ?
            """,
            Integer.class,
            idIntento,
            auth.getName()
        );

        if (cantidad == null || cantidad == 0) {
            denegar();
        }
    }

    // Comprueba la propiedad de un intento de práctica
    public void verificarIntentoActividad(
            Authentication auth, Long idIntento) {

        verificarEstudiante(auth);

        Integer cantidad = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM intentos_actividad i
            JOIN estudiantes e
                ON e.id_estudiante = i.id_estudiante
            JOIN usuarios u
                ON u.id_usuario = e.id_usuario
            WHERE i.id_intento_actividad = ?
              AND u.correo = ?
            """,
            Integer.class,
            idIntento,
            auth.getName()
        );

        if (cantidad == null || cantidad == 0) {
            denegar();
        }
    }

    private void denegar() {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "No tienes permiso para acceder a estos datos"
        );
    }
}
