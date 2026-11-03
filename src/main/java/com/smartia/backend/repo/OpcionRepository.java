package com.smartia.backend.repo;

import com.smartia.backend.model.Opcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OpcionRepository extends JpaRepository<Opcion, Integer> {
    List<Opcion> findByIdPregunta(Integer idPregunta);
    List<Opcion> findByIdPreguntaIn(Collection<Integer> ids);
}
