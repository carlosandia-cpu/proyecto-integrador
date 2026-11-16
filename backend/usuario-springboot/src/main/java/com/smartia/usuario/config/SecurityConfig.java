
package com.smartia.usuario.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Locale;

@Configuration
public class SecurityConfig {

    // 1. Verificacion segura de contraseñas mediante BCrypt
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 2. Autenticacion de usuarios registrados en MariaDB
    @Bean
    public UserDetailsService userDetailsService(
            JdbcTemplate jdbc) {

        return correo -> {

            List<UserDetails> usuarios = jdbc.query("""
                SELECT correo, hash_password, rol
                FROM usuarios
                WHERE correo = ?
                """,
                (rs, fila) -> User.builder()
                    .username(rs.getString("correo"))
                    .password(rs.getString("hash_password"))
                    .authorities(
                        "ROLE_" + rs.getString("rol")
                            .toUpperCase(Locale.ROOT)
                    )
                    .build(),
                correo
            );

            if (usuarios.isEmpty()) {
                throw new UsernameNotFoundException(
                    "Usuario no encontrado"
                );
            }

            return usuarios.get(0);
        };
    }

    // 3. Configuracion general de seguridad
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http

            // Permitir los origenes definidos en CorsConfig
            .cors(Customizer.withDefaults())

            // Solo para las pruebas locales con HTTP Basic
            .csrf(csrf -> csrf.disable())

            // API sin sesiones HTTP
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            // Reglas de acceso a los endpoints
            .authorizeHttpRequests(auth -> auth

                // ======================================
                // CATALOGOS EDUCATIVOS PUBLICOS
                // ======================================

                .requestMatchers(
                HttpMethod.GET,
                    "/api/areas",
                    "/api/actividades",
                    "/api/actividades/*/preguntas",
                    "/api/concursos"
                ).permitAll()

                // ======================================
                // PERFIL DEL USUARIO
                // ======================================

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/usuarios/me"
                ).authenticated()

                // ======================================
                // HU-9: DIAGNOSTICO ACADEMICO
                // ======================================

                // Diagnostico del estudiante autenticado
                // AccesoEstudianteService verifica propiedad
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/estudiantes/*/diagnostico"
                ).hasRole("ESTUDIANTE")

                // ======================================
                // HU-4: CONCURSOS
                // HU-2: PRACTICAS EDUCATIVAS
                // ======================================

                // Solo estudiantes autenticados
                // Los controladores verifican propiedad
                .requestMatchers(
                    "/api/intentos",
                    "/api/intentos/**",
                    "/api/actividades/intentos",
                    "/api/actividades/intentos/**"
                ).hasRole("ESTUDIANTE")

                // ======================================
                // HU-8: SEGUIMIENTO DOCENTE
                // ======================================

                // Solo docentes autenticados
                // AccesoDocenteService verifica identidad
                // y asignacion docente-estudiante
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/docentes/**"
                ).hasRole("DOCENTE")

                // ======================================
                // RUTAS RESTANTES
                // ======================================

                // Bloquear operaciones docentes no GET
                // y otras rutas de estudiantes
                .requestMatchers(
                    "/api/docentes/**",
                    "/api/estudiantes/**"
                ).denyAll()

                // Denegar cualquier otra ruta no declarada
                .anyRequest().denyAll()
            )

            // HTTP Basic para desarrollo local
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
