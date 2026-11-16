
package com.smartia.usuario.controller;

import com.smartia.usuario.service.AccesoEstudianteService;
import com.smartia.usuario.service.ResultadoConcursoService;
import com.smartia.usuario.service.ResultadoConcursoService.ResultadoDTO;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intentos")
public class ResultadoConcursoController {

    private final ResultadoConcursoService servicio;
    private final AccesoEstudianteService acceso;

    public ResultadoConcursoController(
            ResultadoConcursoService servicio,
            AccesoEstudianteService acceso) {

        this.servicio = servicio;
        this.acceso = acceso;
    }

    public record RespuestaRequest(
        Long idPregunta,
        Long idOpcion
    ) {}

    public record Mensaje(String mensaje) {}

    @PostMapping("/{id}/respuestas")
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

        acceso.verificarIntentoConcurso(authentication, id);

        servicio.responder(
            id,
            request.idPregunta(),
            request.idOpcion()
        );

        return new Mensaje("Respuesta guardada");
    }

    @PostMapping("/{id}/finalizar")
    public ResultadoDTO finalizar(
            @PathVariable Long id,
            Authentication authentication) {

        acceso.verificarIntentoConcurso(authentication, id);

        return servicio.finalizar(id);
    }

    @GetMapping("/{id}/resultado")
    public ResultadoDTO resultado(
            @PathVariable Long id,
            Authentication authentication) {

        acceso.verificarIntentoConcurso(authentication, id);

        return servicio.resultado(id);
    }
}
