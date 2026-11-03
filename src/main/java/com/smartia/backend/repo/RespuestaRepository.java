package com.smartia.backend.repo;

import com.smartia.backend.model.Respuesta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RespuestaRepository extends JpaRepository<Respuesta, Integer> {
    List<Respuesta> findByIdIntento(Integer idIntento);
    boolean existsByIdIntentoAndIdPregunta(Integer idIntento, Integer idPregunta);
}
