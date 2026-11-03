-- SmartIA - HU-4 (T01): base de datos y datos de prueba
-- Se puede ejecutar varias veces sin error (IF NOT EXISTS / INSERT IGNORE)

CREATE DATABASE IF NOT EXISTS smartia CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smartia;

CREATE TABLE IF NOT EXISTS estudiante (
  id_estudiante INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  grado VARCHAR(30)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS concurso (
  id_concurso INT AUTO_INCREMENT PRIMARY KEY,
  titulo VARCHAR(150) NOT NULL,
  area VARCHAR(20) NOT NULL,
  texto_base TEXT,
  estado VARCHAR(20) DEFAULT 'activo'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS pregunta (
  id_pregunta INT AUTO_INCREMENT PRIMARY KEY,
  id_concurso INT NOT NULL,
  enunciado TEXT NOT NULL,
  puntaje INT DEFAULT 1,
  FOREIGN KEY (id_concurso) REFERENCES concurso(id_concurso)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS opcion (
  id_opcion INT AUTO_INCREMENT PRIMARY KEY,
  id_pregunta INT NOT NULL,
  texto VARCHAR(255) NOT NULL,
  es_correcta BOOLEAN DEFAULT FALSE,
  FOREIGN KEY (id_pregunta) REFERENCES pregunta(id_pregunta)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS intento (
  id_intento INT AUTO_INCREMENT PRIMARY KEY,
  id_estudiante INT NOT NULL,
  id_concurso INT NOT NULL,
  fecha_inicio DATETIME,
  fecha_fin DATETIME,
  puntaje_total INT DEFAULT 0,
  FOREIGN KEY (id_estudiante) REFERENCES estudiante(id_estudiante),
  FOREIGN KEY (id_concurso) REFERENCES concurso(id_concurso)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS respuesta (
  id_respuesta INT AUTO_INCREMENT PRIMARY KEY,
  id_intento INT NOT NULL,
  id_pregunta INT NOT NULL,
  id_opcion INT NULL,
  es_correcta BOOLEAN,
  puntaje_obtenido INT DEFAULT 0,
  FOREIGN KEY (id_intento) REFERENCES intento(id_intento),
  FOREIGN KEY (id_pregunta) REFERENCES pregunta(id_pregunta),
  FOREIGN KEY (id_opcion) REFERENCES opcion(id_opcion)
) ENGINE=InnoDB;

-- Datos de prueba
INSERT IGNORE INTO estudiante (id_estudiante, nombre, grado) VALUES (1, 'Estudiante de prueba', '5to Secundaria');

INSERT IGNORE INTO concurso (id_concurso, titulo, area, texto_base, estado) VALUES
(1, 'Concurso de lectura 1', 'lectura', 'Aquí va un texto breve de lectura.', 'activo'),
(2, 'Concurso de matemáticas 1', 'matematicas', NULL, 'activo');

INSERT IGNORE INTO pregunta (id_pregunta, id_concurso, enunciado, puntaje) VALUES
(1, 1, '¿Cuál es la idea principal del texto?', 1),
(2, 1, '¿Qué quiere decir el autor al final?', 1),
(3, 2, '¿Cuánto es 12 x 3?', 1),
(4, 2, 'Si x + 5 = 12, ¿cuánto vale x?', 1);

INSERT IGNORE INTO opcion (id_opcion, id_pregunta, texto, es_correcta) VALUES
(1, 1, 'Opción A', FALSE), (2, 1, 'Opción B', TRUE),  (3, 1, 'Opción C', FALSE),
(4, 2, 'Opción A', TRUE),  (5, 2, 'Opción B', FALSE), (6, 2, 'Opción C', FALSE),
(7, 3, '33', FALSE),       (8, 3, '36', TRUE),        (9, 3, '38', FALSE),
(10, 4, '5', FALSE),       (11, 4, '7', TRUE),        (12, 4, '17', FALSE);
