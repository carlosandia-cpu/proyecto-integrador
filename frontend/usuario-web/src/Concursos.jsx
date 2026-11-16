
import { useCallback, useEffect, useRef, useState } from "react";
import "./Concursos.css";

const API = "http://localhost:8081";

export default function Concursos({
  volver,
  autorizacion,
  idEstudiante,
}) {
  const [concursos, setConcursos] = useState([]);
  const [estados, setEstados] = useState({});
  const [cargando, setCargando] = useState(true);

  const [concurso, setConcurso] = useState(null);
  const [intento, setIntento] = useState(null);
  const [preguntas, setPreguntas] = useState([]);
  const [respuestas, setRespuestas] = useState({});
  const [indice, setIndice] = useState(0);
  const [resultado, setResultado] = useState(null);

  const [ranking, setRanking] = useState([]);
  const [rankingVisible, setRankingVisible] = useState(false);
  const [cargandoRanking, setCargandoRanking] = useState(false);

  const [fechaLimite, setFechaLimite] = useState(null);
  const [restante, setRestante] = useState(0);

  const [procesando, setProcesando] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState("");

  const iniciandoRef = useRef(false);
  const finalizandoRef = useRef(false);

  // ==========================================
  // PETICIONES AL BACKEND
  // ==========================================

  const peticion = useCallback(
    async (ruta, opciones = {}) => {
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
        const err = new Error(
          `Error del servidor: ${respuesta.status}`
        );
        err.status = respuesta.status;
        throw err;
      }

      if (respuesta.status === 204) return null;

      return respuesta.json();
    },
    [autorizacion]
  );

  // ==========================================
  // CATALOGO + ESTADOS DE CADA CONCURSO
  // ==========================================

  const cargarConcursos = useCallback(async () => {
    setCargando(true);
    setError("");

    try {
      const lista = await peticion("/api/concursos");

      const resultados = await Promise.all(
        lista.map(async (item) => {
          try {
            const estado = await peticion(
              `/api/intentos/estado/${item.idConcurso}`
            );

            return [item.idConcurso, estado];
          } catch {
            return [item.idConcurso, null];
          }
        })
      );

      setConcursos(lista);
      setEstados(Object.fromEntries(resultados));
    } catch {
      setError(
        "No se pudo cargar el catálogo de concursos."
      );
    } finally {
      setCargando(false);
    }
  }, [peticion]);

  useEffect(() => {
    cargarConcursos();
  }, [cargarConcursos]);

  // ==========================================
  // TEMPORIZADOR
  // ==========================================

  useEffect(() => {
    if (!intento || resultado || fechaLimite === null) {
      return;
    }

    function actualizarTiempo() {
      const segundos = Math.max(
        0,
        Math.ceil((fechaLimite - Date.now()) / 1000)
      );

      setRestante(segundos);
    }

    actualizarTiempo();

    const intervalo = setInterval(actualizarTiempo, 500);

    return () => clearInterval(intervalo);
  }, [intento, resultado, fechaLimite]);

  function configurarTiempo(segundos) {
    const segundosValidos = Math.max(
      0,
      Number(segundos) || 0
    );

    setRestante(segundosValidos);
    setFechaLimite(Date.now() + segundosValidos * 1000);
  }

  function formatoTiempo(segundos) {
    const minutos = Math.floor(segundos / 60);
    const restanteSegundos = segundos % 60;

    return (
      String(minutos).padStart(2, "0") +
      ":" +
      String(restanteSegundos).padStart(2, "0")
    );
  }

  // ==========================================
  // RECUPERAR RESULTADO
  // ==========================================

  const consultarResultado = useCallback(
    async (idIntento) => {
      const datos = await peticion(
        `/api/intentos/${idIntento}/resultado`
      );

      setResultado(datos);
      return datos;
    },
    [peticion]
  );

  // ==========================================
  // FINALIZAR CONCURSO
  // ==========================================

  const finalizar = useCallback(async () => {
    if (
      !intento ||
      resultado ||
      finalizandoRef.current
    ) {
      return;
    }

    finalizandoRef.current = true;
    setProcesando(true);
    setError("");

    try {
      const datos = await peticion(
        `/api/intentos/${intento.idIntento}/finalizar`,
        { method: "POST" }
      );

      setResultado(datos);
    } catch {
      try {
        await consultarResultado(intento.idIntento);
      } catch {
        setError(
          "No se pudo finalizar o recuperar el resultado. " +
          "Comprueba la conexión."
        );
      }
    } finally {
      setProcesando(false);
      finalizandoRef.current = false;
    }
  }, [intento, resultado, peticion, consultarResultado]);

  useEffect(() => {
    if (
      intento &&
      !resultado &&
      fechaLimite !== null &&
      restante === 0 &&
      !guardando
    ) {
      finalizar();
    }
  }, [
    intento,
    resultado,
    fechaLimite,
    restante,
    guardando,
    finalizar,
  ]);

  // ==========================================
  // RECUPERAR INTENTO ACTIVO
  // ==========================================

  async function continuar(item) {
    if (procesando || iniciandoRef.current) return;

    setProcesando(true);
    setError("");

    try {
      // Consultar nuevamente para tener datos actuales.
      const estado = await peticion(
        `/api/intentos/estado/${item.idConcurso}`
      );

      setEstados((anteriores) => ({
        ...anteriores,
        [item.idConcurso]: estado,
      }));

      if (!estado.idIntentoActivo) {
        setError(
          "Ya no existe un intento activo para continuar."
        );
        return;
      }

      const intentoRecuperado = {
        idIntento: estado.idIntentoActivo,
        idEstudiante,
        idConcurso: item.idConcurso,
        numeroIntento: estado.numeroIntento,
        estado: "EN_CURSO",
      };

      const preguntasRecuperadas = await peticion(
        `/api/intentos/${estado.idIntentoActivo}/preguntas`
      );

      setConcurso(item);
      setIntento(intentoRecuperado);
      setPreguntas(preguntasRecuperadas);
      setRespuestas(estado.respuestasGuardadas || {});
      setIndice(0);
      setResultado(null);
      setRankingVisible(false);

      // El tiempo se recupera desde MariaDB.
      configurarTiempo(estado.segundosRestantes);
    } catch {
      setError(
        "No se pudo recuperar el concurso. " +
        "Intenta nuevamente desde el catálogo."
      );
    } finally {
      setProcesando(false);
    }
  }

  // ==========================================
  // INICIAR PARTICIPACION
  // ==========================================

  async function participar(item) {
    if (procesando || iniciandoRef.current || intento) {
      return;
    }

    iniciandoRef.current = true;
    setProcesando(true);
    setError("");

    let creado = null;

    try {
      const estadoActual = await peticion(
        `/api/intentos/estado/${item.idConcurso}`
      );

      setEstados((anteriores) => ({
        ...anteriores,
        [item.idConcurso]: estadoActual,
      }));

      if (
        estadoActual.idIntentoActivo ||
        !estadoActual.puedeParticipar
      ) {
        setError(
          estadoActual.idIntentoActivo
            ? "Ya tienes un concurso en curso. " +
              "Utiliza Continuar concurso."
            : "No tienes intentos disponibles."
        );
        return;
      }

      const inicioLocal = Date.now();

      creado = await peticion("/api/intentos", {
        method: "POST",
        body: JSON.stringify({
          idEstudiante,
          idConcurso: item.idConcurso,
        }),
      });

      setConcurso(item);
      setIntento(creado);
      setPreguntas([]);
      setRespuestas({});
      setIndice(0);
      setResultado(null);
      setRankingVisible(false);

      // Aproximación local inicial.
      // Al continuar, la hora procede del servidor.
      const tiempoInicial = Math.max(
        0,
        item.duracionMinutos * 60 -
          Math.ceil((Date.now() - inicioLocal) / 1000)
      );

      configurarTiempo(tiempoInicial);

      const nuevasPreguntas = await peticion(
        `/api/intentos/${creado.idIntento}/preguntas`
      );

      setPreguntas(nuevasPreguntas);
    } catch (err) {
      if (creado) {
        setError(
          "El intento se creó, pero falló la carga " +
          "de preguntas. Usa Reintentar carga o " +
          "vuelve a iniciar sesión para recuperarlo."
        );
      } else if (err.status === 409) {
        setError(
          "El concurso ya tiene un intento activo " +
          "o alcanzaste su límite."
        );
      } else {
        setError(
          "No se pudo iniciar el concurso. " +
          "Revisa la conexión con Spring Boot."
        );
      }
    } finally {
      iniciandoRef.current = false;
      setProcesando(false);
    }
  }

  async function recuperarPreguntas() {
    if (!intento || procesando) return;

    setProcesando(true);
    setError("");

    try {
      const datos = await peticion(
        `/api/intentos/${intento.idIntento}/preguntas`
      );

      setPreguntas(datos);
    } catch {
      try {
        await consultarResultado(intento.idIntento);
      } catch {
        setError(
          "No se pudieron recuperar las preguntas."
        );
      }
    } finally {
      setProcesando(false);
    }
  }

  // ==========================================
  // REGISTRAR RESPUESTAS
  // ==========================================

  async function responder(idPregunta, idOpcion) {
    if (
      !intento ||
      resultado ||
      procesando ||
      guardando ||
      restante === 0
    ) {
      return;
    }

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

      setRespuestas((anteriores) => ({
        ...anteriores,
        [idPregunta]: idOpcion,
      }));
    } catch (err) {
      if (err.status === 409) {
        try {
          await consultarResultado(intento.idIntento);
        } catch {
          setError(
            "El concurso ya no admite respuestas."
          );
        }
      } else {
        setError(
          "No se pudo guardar la respuesta."
        );
      }
    } finally {
      setGuardando(false);
    }
  }

  // ==========================================
  // RANKING
  // ==========================================

  async function verRanking(item) {
    if (intento && !resultado) return;

    setConcurso(item);
    setRanking([]);
    setRankingVisible(true);
    setCargandoRanking(true);
    setError("");

    try {
      const datos = await peticion(
        `/api/intentos/ranking/${item.idConcurso}`
      );
      setRanking(datos);
    } catch {
      setError(
        "No se pudo consultar la clasificación."
      );
    } finally {
      setCargandoRanking(false);
    }
  }

  function cerrarRanking() {
    setRankingVisible(false);
    setError("");

    if (!resultado) setConcurso(null);
  }

  // ==========================================
  // NAVEGACION
  // ==========================================

  function volverCatalogo() {
    if (intento && !resultado) {
      setError(
        "Debes terminar el concurso antes de regresar."
      );
      return;
    }

    setConcurso(null);
    setIntento(null);
    setPreguntas([]);
    setRespuestas({});
    setIndice(0);
    setResultado(null);
    setRankingVisible(false);
    setRanking([]);
    setFechaLimite(null);
    setRestante(0);
    setError("");

    cargarConcursos();
  }

  function regresarDashboard() {
    if (intento && !resultado) {
      setError(
        "Tienes un concurso en curso. " +
        "Finalízalo antes de salir."
      );
      return;
    }

    volver();
  }

  const pregunta = preguntas[indice];

  const estiloBoton = {
    borderRadius: "10px",
    padding: "12px 18px",
    cursor: "pointer",
  };

  // ==========================================
  // INTERFAZ
  // ==========================================

  return (
    <div className="concursos-pagina">
      <header className="concursos-header">
        <strong>SmartIA 2.0</strong>

        <button
          className="concurso-regresar"
          onClick={regresarDashboard}
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
          <div className="concursos-error">{error}</div>
        )}

        {/* CLASIFICACION */}
        {rankingVisible && (
          <section className="concurso-cuestionario">
            <span className="concurso-etiqueta">
              CLASIFICACIÓN
            </span>

            <h2>🏆 Ranking del concurso</h2>
            <p>{concurso?.titulo}</p>

            {cargandoRanking ? (
              <p>Cargando clasificación...</p>
            ) : ranking.length === 0 ? (
              <p>No hay resultados registrados todavía.</p>
            ) : (
              <div style={{ overflowX: "auto" }}>
                <table
                  style={{
                    width: "100%",
                    borderCollapse: "collapse",
                    marginTop: "24px",
                  }}
                >
                  <thead>
                    <tr>
                      {[
                        "Posición",
                        "Estudiante",
                        "Puntos",
                        "Tiempo",
                        "Intentos",
                      ].map((titulo) => (
                        <th
                          key={titulo}
                          style={{
                            padding: "14px",
                            textAlign: "left",
                            borderBottom:
                              "2px solid #d9e2f2",
                          }}
                        >
                          {titulo}
                        </th>
                      ))}
                    </tr>
                  </thead>

                  <tbody>
                    {ranking.map((item) => (
                      <tr key={item.idEstudiante}>
                        <td style={{ padding: "14px" }}>
                          {item.posicion === 1
                            ? "🥇 1.º"
                            : item.posicion === 2
                            ? "🥈 2.º"
                            : item.posicion === 3
                            ? "🥉 3.º"
                            : `#${item.posicion}`}
                        </td>

                        <td style={{ padding: "14px" }}>
                          {item.estudiante}
                          {Number(item.idEstudiante) ===
                            Number(idEstudiante)
                            ? " (Tú)"
                            : ""}
                        </td>

                        <td style={{ padding: "14px" }}>
                          {item.mejorPuntaje}
                        </td>

                        <td style={{ padding: "14px" }}>
                          {item.tiempoSegundos} s
                        </td>

                        <td style={{ padding: "14px" }}>
                          {item.intentosRealizados}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <button
              style={{ ...estiloBoton, marginTop: "24px" }}
              onClick={cerrarRanking}
            >
              {resultado
                ? "Volver a mi resultado"
                : "Volver a concursos"}
            </button>
          </section>
        )}

        {/* CATALOGO */}
        {!rankingVisible && !intento && (
          <>
            <h2>Concursos disponibles</h2>

            {cargando && <p>Cargando concursos...</p>}

            {!cargando && concursos.length === 0 && (
              <p>No hay concursos disponibles.</p>
            )}

            <div className="concursos-grid">
              {concursos.map((item) => {
                const estado = estados[item.idConcurso];

                const tieneActivo =
                  Boolean(estado?.idIntentoActivo);

                const puedeParticipar =
                  estado?.puedeParticipar === true;

                const etiqueta = tieneActivo
                  ? "Continuar concurso"
                  : !estado
                  ? "Estado no disponible"
                  : puedeParticipar
                  ? "Participar"
                  : "Intentos agotados";

                const deshabilitado =
                  cargando ||
                  procesando ||
                  !estado ||
                  (!tieneActivo && !puedeParticipar);

                return (
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

                    {estado && (
                      <p
                        style={{
                          marginTop: "12px",
                          fontSize: "14px",
                        }}
                      >
                        Intentos disponibles:{" "}
                        <strong>
                          {estado.intentosDisponibles}
                        </strong>
                        {" de "}
                        {estado.maxIntentos}
                      </p>
                    )}

                    <div
                      style={{
                        display: "flex",
                        flexWrap: "wrap",
                        gap: "12px",
                        marginTop: "16px",
                      }}
                    >
                      <button
                        disabled={deshabilitado}
                        onClick={() =>
                          tieneActivo
                            ? continuar(item)
                            : participar(item)
                        }
                      >
                        {etiqueta}
                      </button>

                      <button
                        className="concurso-secundario"
                        onClick={() => verRanking(item)}
                        disabled={procesando}
                      >
                        Ver clasificación
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>

            {!cargando &&
              concursos.some(
                (item) => !estados[item.idConcurso]
              ) && (
                <button
                  style={{
                    ...estiloBoton,
                    marginTop: "20px",
                  }}
                  onClick={cargarConcursos}
                >
                  Reintentar consulta de estados
                </button>
              )}
          </>
        )}

        {/* PARTICIPACION */}
        {!rankingVisible && intento && !resultado && (
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
                  Tiempo restante:{" "}
                  <strong>
                    {formatoTiempo(restante)}
                  </strong>
                </span>

                <span>
                  Intento N.º {intento.numeroIntento}
                </span>
              </div>
            </div>

            <div className="concurso-aviso">
              Las respuestas se guardan automáticamente.
              Si vuelves a iniciar sesión, podrás continuar
              mientras el intento siga activo.
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
                      Number(
                        respuestas[pregunta.idPregunta]
                      ) === Number(opcion.idOpcion);

                    return (
                      <button
                        type="button"
                        key={opcion.idOpcion}
                        className="concurso-opcion"
                        disabled={
                          procesando ||
                          guardando ||
                          restante === 0
                        }
                        onClick={() =>
                          responder(
                            pregunta.idPregunta,
                            opcion.idOpcion
                          )
                        }
                        style={{
                          width: "100%",
                          textAlign: "left",
                          background: seleccionada
                            ? "#e5ecff"
                            : "#fff",
                          border: seleccionada
                            ? "2px solid #3553cc"
                            : "1px solid #d6e0f0",
                        }}
                      >
                        <strong>
                          {String.fromCharCode(65 + i)}
                        </strong>
                        {" "}
                        {opcion.texto}
                        {seleccionada && " ✓"}
                      </button>
                    );
                  })}
                </div>

                <div className="concurso-acciones">
                  <button
                    className="concurso-secundario"
                    disabled={indice === 0 || guardando}
                    onClick={() =>
                      setIndice((actual) => actual - 1)
                    }
                  >
                    Anterior
                  </button>

                  {indice < preguntas.length - 1 ? (
                    <button
                      disabled={guardando}
                      onClick={() =>
                        setIndice((actual) => actual + 1)
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

                <p>
                  Respuestas guardadas:{" "}
                  {Object.keys(respuestas).length} de{" "}
                  {preguntas.length}
                </p>
              </>
            ) : (
              <div className="concurso-aviso">
                <p>
                  Las preguntas no se pudieron cargar.
                </p>

                <button
                  disabled={procesando}
                  onClick={recuperarPreguntas}
                >
                  Reintentar carga
                </button>

                <button
                  disabled={procesando}
                  onClick={finalizar}
                >
                  Finalizar intento
                </button>
              </div>
            )}
          </section>
        )}

        {/* RESULTADO */}
        {!rankingVisible && resultado && (
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

            <p>Estado: {resultado.estado}</p>

            <div
              style={{
                display: "flex",
                gap: "12px",
                flexWrap: "wrap",
                marginTop: "24px",
              }}
            >
              <button
                style={estiloBoton}
                onClick={() => verRanking(concurso)}
              >
                🏆 Ver clasificación
              </button>

              <button
                style={estiloBoton}
                className="concurso-secundario"
                onClick={volverCatalogo}
              >
                Volver a concursos
              </button>
            </div>
          </section>
        )}
      </main>
    </div>
  );
}
