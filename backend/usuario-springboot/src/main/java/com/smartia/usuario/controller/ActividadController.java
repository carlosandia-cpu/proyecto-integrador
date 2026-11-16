
package com.smartia.usuario.controller;

import com.smartia.usuario.service.ActividadService;
import com.smartia.usuario.service.AccesoEstudianteService;
import com.smartia.usuario.service.ActividadService.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/actividades")
public class ActividadController {

    private final ActividadService servicio;
    private final AccesoEstudianteService acceso;

    public ActividadController(
            ActividadService servicio,
            AccesoEstudianteService acceso) {

        this.servicio = servicio;
        this.acceso = acceso;
    }

    public record IniciarRequest(
        Long idEstudiante,
        Long idActividad
    ) {}

    public record RespuestaRequest(
        Long idPregunta,
        Long idOpcion
    ) {}

    public record Mensaje(String mensaje) {}

    // Consultar preguntas publicas de una actividad
    @GetMapping("/{id}/preguntas")
    public List<PreguntaDTO> preguntas(
            @PathVariable Long id) {

        return servicio.preguntas(id);
    }

    // Iniciar una actividad del estudiante autenticado
    @PostMapping("/intentos")
    @ResponseStatus(HttpStatus.CREATED)
    public InicioDTO iniciar(
            @RequestBody IniciarRequest request,
            Authentication authentication) {

        if (request.idEstudiante() == null ||
            request.idActividad() == null) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Faltan datos de la actividad"
            );
        }

        acceso.verificarPropietario(
            authentication,
            request.idEstudiante()
        );

        return servicio.iniciar(
            request.idEstudiante(),
            request.idActividad()
        );
    }

    // Registrar respuestas propias
    @PostMapping("/intentos/{id}/respuestas")
    public Mensaje responder(
            @PathVariable Long id,
            @RequestBody RespuestaRequest request,
            Authentication authentication) {

        if (request.idPregunta() == null ||
            request.idOpcion() == null) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Faltan datos de la respuesta"
            );
        }

        acceso.verificarIntentoActividad(authentication, id);

        servicio.responder(
            id,
            request.idPregunta(),
            request.idOpcion()
        );

        return new Mensaje("Respuesta guardada");
    }

    // Finalizar la actividad propia
    @PostMapping("/intentos/{id}/finalizar")
    public ResultadoDTO finalizar(
            @PathVariable Long id,
            Authentication authentication) {

        acceso.verificarIntentoActividad(authentication, id);

        return servicio.finalizar(id);
    }

    // Consultar el resultado propio
    @GetMapping("/intentos/{id}/resultado")
    public ResultadoDTO resultado(
            @PathVariable Long id,
            Authentication authentication) {

        acceso.verificarIntentoActividad(authentication, id);

        return servicio.resultado(id);
    }
}
