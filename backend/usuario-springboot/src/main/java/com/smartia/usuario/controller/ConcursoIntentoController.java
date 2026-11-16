
package com.smartia.usuario.controller;

import com.smartia.usuario.service.AccesoEstudianteService;
import com.smartia.usuario.service.ConcursoIntentoService;
import com.smartia.usuario.service.ConcursoIntentoService.IntentoDTO;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intentos")
public class ConcursoIntentoController {

    private final ConcursoIntentoService servicio;
    private final AccesoEstudianteService acceso;

    public ConcursoIntentoController(
            ConcursoIntentoService servicio,
            AccesoEstudianteService acceso) {

        this.servicio = servicio;
        this.acceso = acceso;
    }

    public record IniciarIntentoRequest(
        Long idEstudiante,
        Long idConcurso
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IntentoDTO iniciar(
            @RequestBody IniciarIntentoRequest request,
            Authentication authentication) {

        if (request.idEstudiante() == null ||
            request.idConcurso() == null) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Debes enviar idEstudiante e idConcurso"
            );
        }

        // Impide crear un intento para otro estudiante
        acceso.verificarPropietario(
            authentication,
            request.idEstudiante()
        );

        return servicio.iniciar(
            request.idEstudiante(),
            request.idConcurso()
        );
    }
}
