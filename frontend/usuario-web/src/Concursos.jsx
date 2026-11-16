
import { useCallback, useEffect, useRef, useState } from "react";
import "./Concursos.css";

const API = "http://localhost:8081";

export default function Concursos({
  volver,
  autorizacion,
  idEstudiante,
}) {
  const [concursos, setConcursos] = useState([]);
  const [concurso, setConcurso] = useState(null);
  const [intento, setIntento] = useState(null);
  const [preguntas, setPreguntas] = useState([]);
  const [respuestas, setRespuestas] = useState({});
  const [indice, setIndice] = useState(0);
  const [resultado, setResultado] = useState(null);

  const [fechaLimite, setFechaLimite] = useState(null);
  const [restante, setRestante] = useState(0);

  const [cargando, setCargando] = useState(true);
  const [procesando, setProcesando] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState("");

  const finalizandoRef = useRef(false);
  const iniciandoRef = useRef(false);

  const fase = resultado
    ? "resultado"
    : intento
    ? "jugando"
    : "catalogo";

  async function peticion(ruta, opciones = {}) {
    const respuesta = await fetch(`${API}${ruta}`, {
      ...opciones,
      headers: {
        Accept: "application/json",
        ...(opciones.body
          ? { "Content-Type": "application/json" }
          : {}),
        ...(autorizacion
          ? { Authorization: autorizacion }
          : {}),
        ...opciones.headers,
      },
    });

    if (!respuesta.ok) {
      const errorHTTP = new Error(
        `Error del servidor (${respuesta.status})`
      );
      errorHTTP.status = respuesta.status;
      throw errorHTTP;
    }

    if (respuesta.status === 204) return null;
    return respuesta.json();
  }

  const cargarConcursos = useCallback(async () => {
    setCargando(true);
    setError("");

    try {
      const datos = await peticion("/api/concursos");
      setConcursos(datos);
    } catch {
      setError("No se pudo cargar el catálogo de concursos.");
    } finally {
      setCargando(false);
    }
  }, [autorizacion]);

  useEffect(() => {
    cargarConcursos();
  }, [cargarConcursos]);

  // El servidor es quien decide si el tiempo venció.
  // Este contador es una referencia visual.
  useEffect(() => {
    if (!intento || resultado || !fechaLimite) return;

    function actualizar() {
      setRestante(
        Math.max(
          0,
          Math.ceil((fechaLimite - Date.now()) / 1000)
        )
      );
    }

    actualizar();

    const intervalo = setInterval(actualizar, 500);

    return () => clearInterval(intervalo);
  }, [intento, resultado, fechaLimite]);

  async function consultarResultado(idIntento) {
    const datos = await peticion(
      `/api/intentos/${idIntento}/resultado`
    );
    setResultado(datos);
    return datos;
  }

  const finalizar = useCallback(async () => {
    if (!intento || finalizandoRef.current || resultado) return;

    finalizandoRef.current = true;
    setProcesando(true);
    setError("");

    try {
      const datos = await peticion(
        `/api/intentos/${intento.idIntento}/finalizar`,
        { method: "POST" }
      );
      setResultado(datos);
    } catch (err) {
      if (err.status === 409) {
        try {
          await consultarResultado(intento.idIntento);
        } catch {
          setError(
            "No se pudo recuperar el resultado del concurso."
          );
        }
      } else {
        setError("No se pudo finalizar el concurso.");
      }
    } finally {
      setProcesando(false);
      finalizandoRef.current = false;
    }
  }, [intento, resultado, autorizacion]);

  // Cuando termina el tiempo visual, solicitamos el cierre.
  useEffect(() => {
    if (
      intento &&
      fechaLimite &&
      restante === 0 &&
      !resultado &&
      !guardando
    ) {
      finalizar();
    }
  }, [
    intento,
    fechaLimite,
    restante,
    resultado,
    guardando,
    finalizar,
  ]);

  async function participar(item) {
    if (iniciandoRef.current || intento) return;

    iniciandoRef.current = true;
    setProcesando(true);
    setError("");

    let creado = null;

    try {
      // Registramos el tiempo local antes de la solicitud.
      // El plazo real lo determina MariaDB.
      const inicioLocal = Date.now();

      creado = await peticion("/api/intentos", {
        method: "POST",
        body: JSON.stringify({
          idEstudiante,
          idConcurso: item.idConcurso,
        }),
      });

      const nuevasPreguntas = await peticion(
        `/api/intentos/${creado.idIntento}/preguntas`
      );

      setConcurso(item);
      setPreguntas(nuevasPreguntas);
      setIndice(0);
      setRespuestas({});
      setResultado(null);

      setFechaLimite(
        inicioLocal + item.duracionMinutos * 60 * 1000
      );

      setRestante(
        Math.max(
          0,
          Math.ceil(
            (inicioLocal +
              item.duracionMinutos * 60 * 1000 -
              Date.now()) /
              1000
          )
        )
      );

      setIntento(creado);
    } catch (err) {
      if (creado) {
        // No descartamos un intento que sí fue creado.
        setConcurso(item);
        setIntento(creado);
        setError(
          "El intento fue creado, pero no pudimos cargar " +
          "las preguntas. No inicies otro intento."
        );
      } else if (err.status === 409) {
        setError(
          "Ya tienes un intento activo o alcanzaste " +
          "el límite de participaciones de este concurso."
        );
      } else {
        setError(
          "No se pudo iniciar el concurso. " +
          "Comprueba la conexión con Spring Boot."
        );
      }
    } finally {
      setProcesando(false);
      iniciandoRef.current = false;
    }
  }

  async function responder(idPregunta, idOpcion) {
    if (!intento || resultado || guardando || procesando) return;

    setGuardando(true);
    setError("");

    try {
      await peticion(
        `/api/intentos/${intento.idIntento}/respuestas`,
        {
          method: "POST",
          body: JSON.stringify({
            idPregunta,
            idOpcion,
          }),
        }
      );

      // Se marca únicamente después de que MariaDB la guarda.
      setRespuestas((actuales) => ({
        ...actuales,
        [idPregunta]: idOpcion,
      }));
    } catch (err) {
      if (err.status === 409) {
        try {
          await consultarResultado(intento.idIntento);
        } catch {
          setError(
            "El intento ya no admite respuestas. " +
            "No se pudo recuperar su resultado."
          );
        }
      } else {
        setError("No se pudo guardar la respuesta.");
      }
    } finally {
      setGuardando(false);
    }
  }

  function formatoTiempo(segundos) {
    const minutos = Math.floor(segundos / 60);
    const seg = segundos % 60;

    return `${String(minutos).padStart(2, "0")}:${String(
      seg
    ).padStart(2, "0")}`;
  }

  function regresar() {
    // No abandonamos silenciosamente un intento activo.
    if (intento && !resultado) {
      setError(
        "Tienes un concurso en curso. " +
        "Finalízalo antes de regresar."
      );
      return;
    }

    volver();
  }

  const pregunta = preguntas[indice];

  return (
    <div className="concursos-pagina">
      <header className="concursos-header">
        <strong>SmartIA 2.0</strong>

        <button
          className="concurso-regresar"
          onClick={regresar}
        >
          Volver al dashboard
        </button>
      </header>

      <main className="concursos-contenido">
        <div className="concursos-titulo">
          <span>DESAFÍOS EDUCATIVOS</span>
          <h1>Concursos</h1>
          <p>
            Compite en desafíos académicos y demuestra
            tus conocimientos.
          </p>
        </div>

        {error && (
          <div className="concursos-error">
            {error}
          </div>
        )}

        {fase === "catalogo" && (
          <>
            <h2>Concursos disponibles</h2>

            {cargando && <p>Cargando concursos...</p>}

            {!cargando && concursos.length === 0 && (
              <p>No hay concursos disponibles por ahora.</p>
            )}

            <div className="concursos-grid">
              {concursos.map((item) => (
                <article
                  className="concurso-card"
                  key={item.idConcurso}
                >
                  <span className="concurso-etiqueta">
                    CONCURSO
                  </span>

                  <h3>{item.titulo}</h3>

                  <p>{item.descripcion}</p>

                  <div className="concurso-datos">
                    <span>
                      Duración: {item.duracionMinutos} min
                    </span>
                    <span>
                      Máximo de intentos: {item.maxIntentos}
                    </span>
                  </div>

                  <button
                    onClick={() => participar(item)}
                    disabled={procesando}
                  >
                    {procesando
                      ? "Iniciando..."
                      : "Participar"}
                  </button>
                </article>
              ))}
            </div>
          </>
        )}

        {fase === "jugando" && (
          <section className="concurso-cuestionario">
            <div className="concurso-detalle">
              <div>
                <span className="concurso-etiqueta">
                  CONCURSO EN CURSO
                </span>
                <h2>{concurso?.titulo}</h2>
              </div>

              <div className="concurso-datos">
                <span>
                  Tiempo aproximado:{" "}
                  <strong>{formatoTiempo(restante)}</strong>
                </span>
                <span>
                  Intento N.º {intento.numeroIntento}
                </span>
              </div>
            </div>

            <div className="concurso-aviso">
              El tiempo comienza al participar.
              Las respuestas se guardan automáticamente
              al seleccionar una alternativa.
            </div>

            {pregunta ? (
              <>
                <div className="concurso-progreso">
                  Pregunta {indice + 1} de {preguntas.length}
                </div>

                <div className="concurso-pregunta">
                  <p>{pregunta.puntos} puntos</p>
                  <h2>{pregunta.enunciado}</h2>
                </div>

                <div className="concurso-opciones">
                  {pregunta.opciones.map((opcion, i) => {
                    const seleccionada =
                      respuestas[pregunta.idPregunta] ===
                      opcion.idOpcion;

                    return (
                      <button
                        type="button"
                        className="concurso-opcion"
                        key={opcion.idOpcion}
                        onClick={() =>
                          responder(
                            pregunta.idPregunta,
                            opcion.idOpcion
                          )
                        }
                        disabled={
                          guardando ||
                          procesando ||
                          restante === 0
                        }
                        style={{
                          width: "100%",
                          textAlign: "left",
                          cursor: "pointer",
                          background: seleccionada
                            ? "#e5ecff"
                            : "#ffffff",
                          border: seleccionada
                            ? "2px solid #3553cc"
                            : "1px solid #d6e0f0",
                        }}
                      >
                        <strong>
                          {String.fromCharCode(65 + i)}
                        </strong>
                        {"  "}
                        {opcion.texto}
                        {seleccionada ? " ✓" : ""}
                      </button>
                    );
                  })}
                </div>

                <div className="concurso-acciones">
                  <button
                    className="concurso-secundario"
                    disabled={indice === 0 || guardando}
                    onClick={() =>
                      setIndice((i) => i - 1)
                    }
                  >
                    Anterior
                  </button>

                  {indice < preguntas.length - 1 ? (
                    <button
                      disabled={guardando}
                      onClick={() =>
                        setIndice((i) => i + 1)
                      }
                    >
                      Siguiente
                    </button>
                  ) : (
                    <button
                      disabled={guardando || procesando}
                      onClick={finalizar}
                    >
                      {procesando
                        ? "Finalizando..."
                        : "Finalizar concurso"}
                    </button>
                  )}
                </div>
              </>
            ) : (
              <div className="concurso-aviso">
                No se pudieron cargar las preguntas
                de este intento.
                <button
                  disabled={procesando}
                  onClick={finalizar}
                >
                  Cerrar intento
                </button>
              </div>
            )}

            {preguntas.length > 0 && (
              <p>
                Respuestas guardadas:{" "}
                {Object.keys(respuestas).length} de{" "}
                {preguntas.length}
              </p>
            )}
          </section>
        )}

        {fase === "resultado" && (
          <section className="concurso-cuestionario">
            <span className="concurso-etiqueta">
              CONCURSO FINALIZADO
            </span>

            <h2>🏆 Resultado del concurso</h2>
            <p>{concurso?.titulo}</p>

            <h1>{resultado.puntaje} puntos</h1>

            <p>
              Respuestas correctas:{" "}
              {resultado.respuestasCorrectas}
            </p>

            <p>
              Total de preguntas:{" "}
              {resultado.totalPreguntas}
            </p>

            <p>
              Estado: {resultado.estado}
            </p>

            <button onClick={volver}>
              Volver al dashboard
            </button>
          </section>
        )}
      </main>
    </div>
  );
}
