
package com.smartia.usuario.controller;

import com.smartia.usuario.service.DiagnosticoService;
import com.smartia.usuario.service.AccesoEstudianteService;
import com.smartia.usuario.service.DiagnosticoService.DiagnosticoDTO;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/estudiantes")
public class DiagnosticoController {

    private final DiagnosticoService servicio;
    private final AccesoEstudianteService acceso;

    public DiagnosticoController(
            DiagnosticoService servicio,
            AccesoEstudianteService acceso) {

        this.servicio = servicio;
        this.acceso = acceso;
    }

    @GetMapping("/{id}/diagnostico")
    public DiagnosticoDTO diagnostico(
            @PathVariable Long id,
            Authentication authentication) {

        // Verificar que el estudiante sea propietario
        acceso.verificarPropietario(authentication, id);

        // Consultar sus resultados
        return servicio.obtenerDiagnostico(id);
    }
}
