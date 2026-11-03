package com.smartia.backend.service;

import com.smartia.backend.dto.Dtos.*;
import com.smartia.backend.model.*;
import com.smartia.backend.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final EstudianteRepository estudianteRepo;
    private final ConcursoRepository concursoRepo;
    private final PreguntaRepository preguntaRepo;
    private final OpcionRepository opcionRepo;
    private final IntentoRepository intentoRepo;
    private final RespuestaRepository respuestaRepo;
    private final GeminiService gemini;

    private static ResponseStatusException error(HttpStatus s, String msg) {
        return new ResponseStatusException(s, msg);
    }

    // ---------- Concursos y preguntas ----------
    public List<ConcursoDto> concursos() {
        return concursoRepo.findByEstado("ACTIVO").stream()
                .map(c -> new ConcursoDto(c.getIdConcurso(), c.getTitulo(), c.getArea(), c.getTextoBase(),
                        preguntaRepo.countByIdConcurso(c.getIdConcurso())))
                .toList();
    }

    public List<PreguntaDto> preguntas(Integer idConcurso) {
        concursoRepo.findById(idConcurso).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Concurso no encontrado"));
        List<Pregunta> ps = preguntaRepo.findByIdConcurso(idConcurso);
        if (ps.isEmpty()) return List.of();
        Map<Integer, List<Opcion>> ops = opcionRepo
                .findByIdPreguntaIn(ps.stream().map(Pregunta::getIdPregunta).toList())
                .stream().collect(Collectors.groupingBy(Opcion::getIdPregunta));
        return ps.stream().map(p -> new PreguntaDto(
                p.getIdPregunta(), p.getEnunciado(), p.getPuntaje(),
                ops.getOrDefault(p.getIdPregunta(), List.of()).stream()
                        .map(o -> new OpcionDto(o.getIdOpcion(), o.getTexto())).toList()
        )).toList();
    }

    // ---------- Estudiantes ----------
    public Estudiante crearEstudiante(EstudianteRequest r) {
        if (r.nombre() == null || r.nombre().isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }
        Estudiante e = new Estudiante();
        e.setNombre(r.nombre().trim());
        e.setGrado(r.grado() == null ? "" : r.grado().trim());
        return estudianteRepo.save(e);
    }

    // ---------- Intentos ----------
    public IntentoDto iniciar(IniciarIntentoRequest r) {
        estudianteRepo.findById(r.idEstudiante()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Estudiante no encontrado"));
        concursoRepo.findById(r.idConcurso()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Concurso no encontrado"));
        Intento in = new Intento();
        in.setIdEstudiante(r.idEstudiante());
        in.setIdConcurso(r.idConcurso());
        in.setFechaInicio(LocalDateTime.now());
        in.setPuntajeTotal(0);
        in = intentoRepo.save(in);
        return new IntentoDto(in.getIdIntento(), in.getIdConcurso(), in.getPuntajeTotal());
    }

    @Transactional
    public ResponderResponse responder(Integer idIntento, ResponderRequest r) {
        Intento intento = buscarIntento(idIntento);
        if (intento.getFechaFin() != null) throw error(HttpStatus.CONFLICT, "El intento ya finalizo");

        Pregunta pregunta = preguntaRepo.findById(r.idPregunta())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Pregunta no encontrada"));
        if (!pregunta.getIdConcurso().equals(intento.getIdConcurso()))
            throw error(HttpStatus.BAD_REQUEST, "La pregunta no pertenece a este concurso");

        Opcion elegida = opcionRepo.findById(r.idOpcion())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Opcion no encontrada"));
        if (!elegida.getIdPregunta().equals(pregunta.getIdPregunta()))
            throw error(HttpStatus.BAD_REQUEST, "La opcion no pertenece a esa pregunta");

        if (respuestaRepo.existsByIdIntentoAndIdPregunta(idIntento, pregunta.getIdPregunta()))
            throw error(HttpStatus.CONFLICT, "Esa pregunta ya fue respondida");

        boolean ok = Boolean.TRUE.equals(elegida.getEsCorrecta());
        int pts = ok ? pregunta.getPuntaje() : 0;

        Respuesta res = new Respuesta();
        res.setIdIntento(idIntento);
        res.setIdPregunta(pregunta.getIdPregunta());
        res.setIdOpcion(elegida.getIdOpcion());
        res.setEsCorrecta(ok);
        res.setPuntajeObtenido(pts);
        respuestaRepo.save(res);

        intento.setPuntajeTotal(intento.getPuntajeTotal() + pts);
        intentoRepo.save(intento);

        Integer idCorrecta = opcionRepo.findByIdPregunta(pregunta.getIdPregunta()).stream()
                .filter(o -> Boolean.TRUE.equals(o.getEsCorrecta()))
                .map(Opcion::getIdOpcion).findFirst().orElse(null);

        return new ResponderResponse(ok, pts, idCorrecta, intento.getPuntajeTotal());
    }

    @Transactional
    public ResultadoDto finalizar(Integer idIntento) {
        Intento intento = buscarIntento(idIntento);
        if (intento.getFechaFin() == null) {
            intento.setFechaFin(LocalDateTime.now());
            intentoRepo.save(intento);
        }
        return resultado(idIntento);
    }

    public ResultadoDto resultado(Integer idIntento) {
        Intento intento = buscarIntento(idIntento);
        Concurso concurso = concursoRepo.findById(intento.getIdConcurso())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Concurso no encontrado"));
        List<Pregunta> ps = preguntaRepo.findByIdConcurso(concurso.getIdConcurso());
        Map<Integer, Pregunta> preguntaPorId = ps.stream()
                .collect(Collectors.toMap(Pregunta::getIdPregunta, Function.identity()));
        List<Opcion> ops = ps.isEmpty() ? List.of() : opcionRepo.findByIdPreguntaIn(preguntaPorId.keySet());
        Map<Integer, Opcion> opcionPorId = ops.stream()
                .collect(Collectors.toMap(Opcion::getIdOpcion, Function.identity()));
        List<Respuesta> rs = respuestaRepo.findByIdIntento(idIntento);

        List<DetalleDto> detalle = rs.stream().map(r -> {
            Pregunta p = preguntaPorId.get(r.getIdPregunta());
            Opcion elegida = opcionPorId.get(r.getIdOpcion());
            String correcta = ops.stream()
                    .filter(o -> o.getIdPregunta().equals(r.getIdPregunta()) && Boolean.TRUE.equals(o.getEsCorrecta()))
                    .map(Opcion::getTexto).findFirst().orElse("");
            return new DetalleDto(p.getEnunciado(), elegida.getTexto(), correcta, Boolean.TRUE.equals(r.getEsCorrecta()));
        }).toList();

        int maximo = ps.stream().mapToInt(Pregunta::getPuntaje).sum();
        int aciertos = (int) rs.stream().filter(r -> Boolean.TRUE.equals(r.getEsCorrecta())).count();
        return new ResultadoDto(idIntento, concurso.getTitulo(), intento.getPuntajeTotal(),
                maximo, aciertos, ps.size(), detalle);
    }

    // ---------- IA ----------
    public FeedbackDto feedback(Integer idIntento) {
        ResultadoDto r = resultado(idIntento);
        Intento intento = buscarIntento(idIntento);
        Concurso c = concursoRepo.findById(intento.getIdConcurso()).orElseThrow();

        StringBuilder sb = new StringBuilder();
        sb.append("Eres un tutor amable y motivador para estudiantes de primaria. ")
                .append("Responde SIEMPRE en espanol, en maximo 120 palabras, sin usar tablas.\n")
                .append("Concurso: ").append(r.concurso()).append(" (area: ").append(c.getArea()).append(")\n");
        if (c.getTextoBase() != null && !c.getTextoBase().isBlank()) {
            sb.append("Texto base del concurso: ").append(c.getTextoBase()).append("\n");
        }
        sb.append("Puntaje: ").append(r.puntajeTotal()).append(" de ").append(r.puntajeMaximo())
                .append(" (").append(r.aciertos()).append(" aciertos de ").append(r.totalPreguntas()).append(")\n");
        sb.append("Detalle de respuestas:\n");
        for (DetalleDto d : r.detalle()) {
            sb.append("- Pregunta: ").append(d.enunciado())
                    .append(" | Respondio: ").append(d.respuestaElegida())
                    .append(" | Correcta: ").append(d.respuestaCorrecta())
                    .append(d.esCorrecta() ? " (acierto)" : " (error)").append("\n");
        }
        sb.append("\nDa retroalimentacion: felicita lo que hizo bien, explica con sencillez los errores ")
                .append("y sugiere que tema practicar. No repitas todas las preguntas.");
        return new FeedbackDto(gemini.generar(sb.toString()));
    }

    private Intento buscarIntento(Integer id) {
        return intentoRepo.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Intento no encontrado"));
    }
}