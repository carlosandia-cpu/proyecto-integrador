
package com.smartia.usuario.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final JdbcTemplate jdbc;

    public UsuarioController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PerfilDTO(
        Long idUsuario,
        Long idEstudiante,
        String nombre,
        String apellidos,
        String correo,
        String rol
    ) {}

    @GetMapping("/me")
    public PerfilDTO miPerfil(Authentication authentication) {

        if (authentication == null ||
            !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "No autenticado"
            );
        }

        List<PerfilDTO> usuarios = jdbc.query("""
            SELECT
                u.id_usuario,
                e.id_estudiante,
                u.nombre,
                u.apellidos,
                u.correo,
                u.rol
            FROM usuarios u
            LEFT JOIN estudiantes e
                ON e.id_usuario = u.id_usuario
            WHERE u.correo = ?
              AND u.activo = TRUE
            """,
            (rs, fila) -> {

                Long idEstudiante =
                    rs.getObject(
                        "id_estudiante",
                        Long.class
                    );

                return new PerfilDTO(
                    rs.getLong("id_usuario"),
                    idEstudiante,
                    rs.getString("nombre"),
                    rs.getString("apellidos"),
                    rs.getString("correo"),
                    rs.getString("rol")
                );
            },
            authentication.getName()
        );

        if (usuarios.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Usuario no encontrado"
            );
        }

        return usuarios.get(0);
    }
}
