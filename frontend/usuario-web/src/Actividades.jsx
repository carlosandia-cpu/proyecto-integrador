
import { useEffect, useRef, useState } from "react";
import "./Actividades.css";

const API = "http://localhost:8081";

async function solicitar(ruta, opciones = {}) {
  const respuesta = await fetch(`${API}${ruta}`, {
    ...opciones,
    headers: {
      Accept: "application/json",
      ...(opciones.body
        ? { "Content-Type": "application/json" }
        : {}),
      ...opciones.headers,
    },
  });

  if (!respuesta.ok) {
    let mensaje = `Error HTTP ${respuesta.status}`;

    try {
      const detalle = await respuesta.json();

      mensaje =
        detalle.message ||
        detalle.mensaje ||
        detalle.detail ||
        mensaje;
    } catch {
      // Mantener el mensaje HTTP.
    }

    throw new Error(mensaje);
  }

  return respuesta.json();
}

export default function Actividades({
  area,
  idEstudiante,
  autorizacion,
  volver,
}) {
  const [actividades, setActividades] = useState([]);
  const [actividad, setActividad] = useState(null);
  const [preguntas, setPreguntas] = useState([]);

  const [respuestas, setRespuestas] = useState({});
  const [indice, setIndice] = useState(0);

  const [idIntento, setIdIntento] = useState(null);
  const [resultado, setResultado] = useState(null);

  const [cargando, setCargando] = useState(true);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState("");

  const enviandoRef = useRef(false);
  const esMatematicas = area === "MAT";

  useEffect(() => {
    const controller = new AbortController();

    async function cargarActividades() {
      setCargando(true);
      setError("");

      try {
        const datos = await solicitar("/api/actividades", {
          signal: controller.signal,
        });

        const filtradas = datos.filter((item) => {
          const nombre = (item.area || "").toLowerCase();

          return esMatematicas
            ? nombre.includes("matemat")
            : nombre.includes("lectora");
        });

        setActividades(filtradas);
      } catch (err) {
        if (err.name !== "AbortError") {
          setError(err.message);
        }
      } finally {
        if (!controller.signal.aborted) {
          setCargando(false);
        }
      }
    }

    cargarActividades();

    return () => controller.abort();
  }, [esMatematicas]);

  async function abrirActividad(item) {
    setCargando(true);
    setError("");
    setRespuestas({});
    setIndice(0);
    setResultado(null);
    setIdIntento(null);

    try {
      const datos = await solicitar(
        `/api/actividades/${item.idActividad}/preguntas`
      );

      setPreguntas(datos);
      setActividad(item);
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando(false);
    }
  }

  function seleccionarRespuesta(idPregunta, idOpcion) {
    if (enviandoRef.current || idIntento !== null) return;

    setRespuestas((anteriores) => ({
      ...anteriores,
      [idPregunta]: idOpcion,
    }));
  }

  async function finalizarActividad() {
    if (enviandoRef.current || idIntento !== null) return;

    const faltantes = preguntas.some(
      (pregunta) =>
        respuestas[pregunta.idPregunta] === undefined
    );

    if (faltantes) {
      setError("Debes responder todas las preguntas.");
      return;
    }

    enviandoRef.current = true;
    setEnviando(true);
    setError("");

    let intentoCreado = null;

    try {
      // 1. Crear el intento en Spring Boot.
      const inicio = await solicitar(
        "/api/actividades/intentos",
        {
          method: "POST",
          headers: {
            Authorization: autorizacion,
          },
          body: JSON.stringify({
            idEstudiante,
            idActividad: actividad.idActividad,
          }),
        }
      );

      // Nombre confirmado en InicioDTO.
      intentoCreado = inicio.idIntentoActividad;

      if (!intentoCreado) {
        throw new Error(
          "No se recibió idIntentoActividad del backend."
        );
      }

      setIdIntento(intentoCreado);

      // 2. Guardar las respuestas seleccionadas.
      for (const pregunta of preguntas) {
        await solicitar(
          `/api/actividades/intentos/${intentoCreado}/respuestas`,
          {
            method: "POST",
            headers: {
              Authorization: autorizacion,
            },
            body: JSON.stringify({
              idPregunta: pregunta.idPregunta,
              idOpcion: respuestas[pregunta.idPregunta],
            }),
          }
        );
      }

      // 3. Solicitar la calificación.
      await solicitar(
        `/api/actividades/intentos/${intentoCreado}/finalizar`,
        {
          method: "POST",
          headers: {
            Authorization: autorizacion,
          },
        }
      );

      // 4. Recuperar el resultado desde MariaDB.
      const datosResultado = await solicitar(
        `/api/actividades/intentos/${intentoCreado}/resultado`,
        {
          headers: {
            Authorization: autorizacion,
          },
        }
      );

      setResultado(datosResultado);
    } catch (err) {
      setError(
        err.message +
          (intentoCreado
            ? " El intento ya existe. No se creará otro automáticamente."
            : "")
      );
    } finally {
      enviandoRef.current = false;
      setEnviando(false);
    }
  }

  function volverAlListado() {
    if (enviandoRef.current) return;

    setActividad(null);
    setPreguntas([]);
    setRespuestas({});
    setIndice(0);
    setIdIntento(null);
    setResultado(null);
    setError("");
  }

  const pregunta = preguntas[indice];

  const respondidas = preguntas.filter(
    (item) =>
      respuestas[item.idPregunta] !== undefined
  ).length;

  const progreso = preguntas.length
    ? (respondidas / preguntas.length) * 100
    : 0;

  const etiquetasResultado = {
    idIntentoActividad: "Número de intento",
    estado: "Estado",
    puntaje: "Puntaje obtenido",
    correctas: "Respuestas correctas",
    incorrectas: "Respuestas incorrectas",
    sinResponder: "Sin responder",
    totalPreguntas: "Total de preguntas",
  };

  return (
    <div className="actividad-pagina">
      <header className="actividad-header">
        <strong>SmartIA 2.0</strong>

        <button
          onClick={volver}
          disabled={enviando}
        >
          Volver al dashboard
        </button>
      </header>

      <main className="actividad-contenido">
        <div className="actividad-titulo">
          <span>ÁREA DE APRENDIZAJE</span>

          <h1>
            {esMatematicas
              ? "Matemáticas"
              : "Comprensión lectora"}
          </h1>

          <p>
            Resuelve actividades y obtén una calificación real.
          </p>
        </div>

        {error && (
          <div className="actividad-error" role="alert">
            {error}
          </div>
        )}

        {cargando ? (
          <p>Cargando información...</p>
        ) : !actividad ? (
          <section>
            <h2>Actividades disponibles</h2>

            {actividades.length === 0 && (
              <p>
                No hay actividades disponibles en esta área.
              </p>
            )}

            <div className="actividad-lista">
              {actividades.map((item) => (
                <article
                  className="actividad-tarjeta"
                  key={item.idActividad}
                >
                  <span className="actividad-nivel">
                    {item.dificultad}
                  </span>

                  <h3>{item.titulo}</h3>
                  <p>{item.descripcion}</p>

                  <button
                    onClick={() => abrirActividad(item)}
                  >
                    Comenzar actividad
                  </button>
                </article>
              ))}
            </div>
          </section>
        ) : resultado ? (
          <section className="actividad-tarjeta resultado-local">
            <h2>Actividad finalizada</h2>

            <p>
              Tu actividad fue registrada y calificada
              correctamente por SmartIA.
            </p>

            <h3>Resultado de la evaluación</h3>

            <div className="resumen-resultados">
              {Object.entries(resultado).map(
                ([clave, valor]) => (
                  <div
                    className="dato-resultado"
                    key={clave}
                  >
                    <strong>
                      {etiquetasResultado[clave] || clave}
                    </strong>

                    <span>
                      {String(valor ?? "—")}
                    </span>
                  </div>
                )
              )}
            </div>

            <button onClick={volverAlListado}>
              Volver a actividades
            </button>
          </section>
        ) : preguntas.length === 0 ? (
          <section className="actividad-tarjeta">
            <h2>{actividad.titulo}</h2>

            <p>
              Esta actividad todavía no tiene preguntas.
            </p>

            <button onClick={volverAlListado}>
              Volver a actividades
            </button>
          </section>
        ) : (
          <section className="actividad-cuestionario">
            <div className="actividad-progreso">
              <div>
                <strong>{actividad.titulo}</strong>

                <p>
                  Pregunta {indice + 1} de{" "}
                  {preguntas.length}
                </p>
              </div>

              <span>
                {respondidas}/{preguntas.length} respondidas
              </span>
            </div>

            <div className="actividad-barra">
              <div style={{ width: `${progreso}%` }} />
            </div>

            {actividad.contenidoLectura && (
              <section className="lectura-contenido">
                <h3>Texto de lectura</h3>

                <p>
                  {actividad.contenidoLectura}
                </p>
              </section>
            )}

            <div className="actividad-pregunta">
              <span className="actividad-puntos">
                {pregunta.puntos} puntos
              </span>

              <h2>{pregunta.enunciado}</h2>

              <div className="actividad-opciones">
                {pregunta.opciones.map((opcion) => {
                  const seleccionada =
                    respuestas[pregunta.idPregunta] ===
                    opcion.idOpcion;

                  return (
                    <label
                      key={opcion.idOpcion}
                      className={
                        seleccionada
                          ? "opcion seleccionada"
                          : "opcion"
                      }
                    >
                      <input
                        type="radio"
                        name={`pregunta-${pregunta.idPregunta}`}
                        checked={seleccionada}
                        disabled={
                          enviando || idIntento !== null
                        }
                        onChange={() =>
                          seleccionarRespuesta(
                            pregunta.idPregunta,
                            opcion.idOpcion
                          )
                        }
                      />

                      <span>{opcion.texto}</span>
                    </label>
                  );
                })}
              </div>
            </div>

            <div className="actividad-acciones">
              <button
                className="boton-secundario"
                onClick={() =>
                  setIndice((actual) => actual - 1)
                }
                disabled={indice === 0 || enviando}
              >
                Anterior
              </button>

              {indice < preguntas.length - 1 ? (
                <button
                  onClick={() =>
                    setIndice((actual) => actual + 1)
                  }
                  disabled={enviando}
                >
                  Siguiente
                </button>
              ) : (
                <button
                  onClick={finalizarActividad}
                  disabled={
                    enviando ||
                    idIntento !== null ||
                    respondidas !== preguntas.length
                  }
                >
                  {enviando
                    ? "Guardando y calificando..."
                    : "Finalizar y calificar"}
                </button>
              )}
            </div>

            <button
              className="actividad-cancelar"
              onClick={volverAlListado}
              disabled={enviando}
            >
              Volver a actividades
            </button>
          </section>
        )}
      </main>
    </div>
  );
}
