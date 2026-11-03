package com.smartia.backend.repo;

import com.smartia.backend.model.Pregunta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreguntaRepository extends JpaRepository<Pregunta, Integer> {
    List<Pregunta> findByIdConcurso(Integer idConcurso);

    long countByIdConcurso(Integer idConcurso);
}