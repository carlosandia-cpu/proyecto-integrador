
package com.smartia.usuario.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> manejarError(
            ResponseStatusException ex,
            HttpServletRequest request) {

        int codigo = ex.getStatusCode().value();

        String mensaje = ex.getReason();

        if (mensaje == null || mensaje.isBlank()) {
            mensaje = "No se pudo procesar la solicitud";
        }

        Map<String, Object> respuesta = Map.of(
            "fecha", LocalDateTime.now().toString(),
            "estado", codigo,
            "mensaje", mensaje,
            "ruta", request.getRequestURI()
        );

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(respuesta);
    }
}
