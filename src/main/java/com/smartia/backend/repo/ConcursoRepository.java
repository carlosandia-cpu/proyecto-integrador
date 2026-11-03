package com.smartia.backend.repo;

import com.smartia.backend.model.Concurso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConcursoRepository extends JpaRepository<Concurso, Integer> {
    List<Concurso> findByEstado(String estado);
}
