
package com.smartia.usuario.controller;

import com.smartia.usuario.service.DocenteService;
import com.smartia.usuario.service.AccesoDocenteService;

import com.smartia.usuario.service.DocenteService.EstudianteDTO;
import com.smartia.usuario.service.DocenteService.ResultadoDTO;
import com.smartia.usuario.service.DocenteService.HistorialDTO;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/docentes")
public class DocenteController {

    private final DocenteService servicio;
    private final AccesoDocenteService acceso;

    public DocenteController(
            DocenteService servicio,
            AccesoDocenteService acceso) {

        this.servicio = servicio;
        this.acceso = acceso;
    }

    // Obtener estudiantes asignados al docente
    @GetMapping("/{idDocente}/estudiantes")
    public List<EstudianteDTO> estudiantes(
            @PathVariable Long idDocente,
            Authentication authentication) {

        acceso.verificarDocente(
            authentication,
            idDocente
        );

        return servicio.estudiantes(idDocente);
    }

    // Obtener resultados de concursos
    @GetMapping("/{idDocente}/estudiantes/{idEstudiante}/resultados")
    public List<ResultadoDTO> resultados(
            @PathVariable Long idDocente,
            @PathVariable Long idEstudiante,
            Authentication authentication) {

        acceso.verificarAsignacion(
            authentication,
            idDocente,
            idEstudiante
        );

        return servicio.resultados(
            idDocente,
            idEstudiante
        );
    }

    // Obtener historial completo:
    // concursos y practicas educativas
    @GetMapping("/{idDocente}/estudiantes/{idEstudiante}/historial")
    public List<HistorialDTO> historial(
            @PathVariable Long idDocente,
            @PathVariable Long idEstudiante,
            Authentication authentication) {

        acceso.verificarAsignacion(
            authentication,
            idDocente,
            idEstudiante
        );

        return servicio.historialCompleto(
            idDocente,
            idEstudiante
        );
    }
}
