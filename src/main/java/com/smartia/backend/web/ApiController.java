package com.smartia.backend.web;

import com.smartia.backend.dto.Dtos.*;
import com.smartia.backend.model.Estudiante;
import com.smartia.backend.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final QuizService service;

    @GetMapping("/concursos")
    public List<ConcursoDto> concursos() {
        return service.concursos();
    }

    @GetMapping("/concursos/{id}/preguntas")
    public List<PreguntaDto> preguntas(@PathVariable Integer id) {
        return service.preguntas(id);
    }

    @PostMapping("/estudiantes")
    @ResponseStatus(HttpStatus.CREATED)
    public Estudiante crearEstudiante(@RequestBody EstudianteRequest r) {
        return service.crearEstudiante(r);
    }

    @PostMapping("/intentos")
    @ResponseStatus(HttpStatus.CREATED)
    public IntentoDto iniciar(@RequestBody IniciarIntentoRequest r) {
        return service.iniciar(r);
    }

    @PostMapping("/intentos/{id}/respuestas")
    public ResponderResponse responder(@PathVariable Integer id, @RequestBody ResponderRequest r) {
        return service.responder(id, r);
    }

    @PostMapping("/intentos/{id}/finalizar")
    public ResultadoDto finalizar(@PathVariable Integer id) {
        return service.finalizar(id);
    }

    @GetMapping("/intentos/{id}/resultado")
    public ResultadoDto resultado(@PathVariable Integer id) {
        return service.resultado(id);
    }

    @PostMapping("/intentos/{id}/feedback")
    public FeedbackDto feedback(@PathVariable Integer id) {
        return service.feedback(id);
    }
}
