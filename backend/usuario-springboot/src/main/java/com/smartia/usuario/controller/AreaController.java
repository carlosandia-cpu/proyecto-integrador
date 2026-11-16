package com.smartia.usuario.controller;

import com.smartia.usuario.model.Area;
import com.smartia.usuario.repository.AreaRepository;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/areas")
public class AreaController {

    private final AreaRepository areaRepository;

    public AreaController(AreaRepository areaRepository) {
        this.areaRepository = areaRepository;
    }

    @GetMapping
    public List<Area> listarAreas() {
        return areaRepository.findAll();
    }
}