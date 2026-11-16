
import { useEffect, useState } from "react";
import Actividades from "./Actividades";
import Concursos from "./Concursos";
import "./App.css";

const API = "http://localhost:8081";

// Cuenta de demostración utilizada en nuestras pruebas.
// Pendiente: obtener este ID automáticamente del backend.
const ID_ESTUDIANTE_DEMO = 1;

const modulos = [
  {
    icono: "📐",
    titulo: "Matemáticas",
    descripcion: "Practica ejercicios y fortalece tus habilidades.",
    area: "MAT",
    boton: "Abrir actividades",
  },
  {
    icono: "📖",
    titulo: "Comprensión lectora",
    descripcion: "Mejora tu lectura y comprensión de textos.",
    area: "LECT",
    boton: "Abrir actividades",
  },
  {
    icono: "🏆",
    titulo: "Concursos",
    descripcion:
      "Participa en competencias académicas con otros estudiantes.",
    area: "CONCURSOS",
    boton: "Ver concursos",
  },
  {
    icono: "📊",
    titulo: "Mis resultados",
    descripcion: "Consulta tu rendimiento académico.",
    area: null,
  },
  {
    icono: "🎯",
    titulo: "Mi diagnóstico",
    descripcion: "Descubre tus avances y áreas por reforzar.",
    area: null,
  },
];

function crearBasicAuth(correo, clave) {
  const bytes = new TextEncoder().encode(
    `${correo}:${clave}`
  );

  const texto = Array.from(
    bytes,
    (byte) => String.fromCharCode(byte)
  ).join("");

  return `Basic ${btoa(texto)}`;
}

function App() {
  const [correo, setCorreo] = useState("");
  const [clave, setClave] = useState("");

  const [usuario, setUsuario] = useState(null);
  const [autorizacion, setAutorizacion] = useState("");
  const [vista, setVista] = useState(null);

  const [areas, setAreas] = useState([]);
  const [cargandoAreas, setCargandoAreas] = useState(true);
  const [errorAreas, setErrorAreas] = useState("");

  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState("");

  // Obtener áreas educativas desde Spring Boot.
  useEffect(() => {
    const controller = new AbortController();

    async function cargarAreas() {
      try {
        const respuesta = await fetch(
          `${API}/api/areas`,
          { signal: controller.signal }
        );

        if (!respuesta.ok) {
          throw new Error(
            "No se pudieron cargar las áreas educativas."
          );
        }

        const datos = await respuesta.json();
        setAreas(datos);
      } catch (err) {
        if (err.name !== "AbortError") {
          setErrorAreas(
            "No se pudieron cargar las áreas educativas."
          );
        }
      } finally {
        if (!controller.signal.aborted) {
          setCargandoAreas(false);
        }
      }
    }

    cargarAreas();

    return () => controller.abort();
  }, []);

  // Iniciar sesión con la autenticación de Spring Boot.
  async function iniciarSesion(evento) {
    evento.preventDefault();

    setCargando(true);
    setError("");

    try {
      const auth = crearBasicAuth(
        correo.trim(),
        clave
      );

      const respuesta = await fetch(
        `${API}/api/usuarios/me`,
        {
          method: "GET",
          headers: {
            Authorization: auth,
            Accept: "application/json",
          },
        }
      );

      if (respuesta.status === 401) {
        throw new Error(
          "Correo o contraseña incorrectos."
        );
      }

      if (!respuesta.ok) {
        throw new Error(
          "No se pudo iniciar sesión."
        );
      }

      const datos = await respuesta.json();
      const rol = datos.rol?.toUpperCase();

      if (
        rol !== "ESTUDIANTE" &&
        rol !== "DOCENTE"
      ) {
        throw new Error(
          "Este rol todavía no tiene una interfaz disponible."
        );
      }

      setUsuario({
        ...datos,
        rol,
      });

      // Credencial temporal, conservada solo en memoria.
      // No se guarda en localStorage ni sessionStorage.
      setAutorizacion(auth);

      setClave("");
      setVista(null);
    } catch (err) {
      setError(
        err instanceof TypeError
          ? "No se pudo conectar con Spring Boot."
          : err.message
      );
    } finally {
      setCargando(false);
    }
  }

  // Cerrar sesión y limpiar los datos de autenticación.
  function cerrarSesion() {
    setUsuario(null);
    setAutorizacion("");
    setCorreo("");
    setClave("");
    setVista(null);
    setError("");
  }

  // Rutas del estudiante.
  if (
    usuario &&
    usuario.rol === "ESTUDIANTE" &&
    vista
  ) {
    // Módulo de concursos.
    if (vista === "CONCURSOS") {
      return (
        <Concursos
          idEstudiante={ID_ESTUDIANTE_DEMO}
          autorizacion={autorizacion}
          volver={() => setVista(null)}
        />
      );
    }

    // Actividades de Matemáticas y Comprensión lectora.
    if (vista === "MAT" || vista === "LECT") {
      return (
        <Actividades
          area={vista}
          idEstudiante={ID_ESTUDIANTE_DEMO}
          autorizacion={autorizacion}
          volver={() => setVista(null)}
        />
      );
    }
  }

  // Pantalla de inicio de sesión.
  if (!usuario) {
    return (
      <div className="login-page">
        <div className="login-panel">
          <div className="marca">
            <span className="marca-icono">
              🎓
            </span>

            <h1>SmartIA 2.0</h1>

            <p>
              Plataforma educativa inteligente
            </p>
          </div>

          <form
            className="login-card"
            onSubmit={iniciarSesion}
          >
            <h2>Bienvenido</h2>

            <p>
              Inicia sesión para continuar aprendiendo.
            </p>

            <label htmlFor="correo">
              Correo electrónico
            </label>

            <input
              id="correo"
              type="email"
              placeholder="correo@ejemplo.com"
              autoComplete="username"
              value={correo}
              onChange={(e) =>
                setCorreo(e.target.value)
              }
              required
            />

            <label htmlFor="clave">
              Contraseña
            </label>

            <input
              id="clave"
              type="password"
              placeholder="Ingresa tu contraseña"
              autoComplete="current-password"
              value={clave}
              onChange={(e) =>
                setClave(e.target.value)
              }
              required
            />

            {error && (
              <div className="mensaje-error">
                {error}
              </div>
            )}

            <button
              className="boton-principal"
              disabled={cargando}
            >
              {cargando
                ? "Verificando..."
                : "Iniciar sesión"}
            </button>

            <p className="nota-login">
              Acceso para estudiantes y docentes.
            </p>
          </form>
        </div>
      </div>
    );
  }

  // Determinar qué dashboard mostrar.
  const esDocente =
    usuario.rol === "DOCENTE";

  return (
    <div className="dashboard">
      {/* MENÚ LATERAL */}
      <aside className="sidebar">
        <div className="sidebar-marca">
          🎓 SmartIA 2.0
        </div>

        <div className="sidebar-menu">
          <div className="menu-activo">
            🏠 Inicio
          </div>

          <div className="menu-info">
            {esDocente
              ? "Panel docente"
              : "Panel estudiante"}
          </div>
        </div>

        <div className="sidebar-pie">
          Plataforma educativa inteligente
        </div>
      </aside>

      {/* CONTENIDO PRINCIPAL */}
      <div className="panel-principal">
        <header className="barra-superior">
          <div>
            <strong>SmartIA 2.0</strong>

            <span className="etiqueta-rol">
              {esDocente
                ? "Docente"
                : "Estudiante"}
            </span>
          </div>

          <button
            className="boton-salir"
            onClick={cerrarSesion}
          >
            Cerrar sesión
          </button>
        </header>

        <main className="panel-contenido">
          {/* BIENVENIDA */}
          <section className="bienvenida">
            <div>
              <span>
                PLATAFORMA EDUCATIVA
              </span>

              <h1>
                ¡Hola, {usuario.nombre}!
              </h1>

              <p>
                {esDocente
                  ? "Bienvenido a tu espacio de seguimiento académico."
                  : "Continúa aprendiendo y descubre nuevos desafíos."}
              </p>
            </div>

            <div className="bienvenida-icono">
              {esDocente ? "👨‍🏫" : "🚀"}
            </div>
          </section>

          {/* PERFIL */}
          <section className="seccion">
            <h2>Mi perfil</h2>

            <div className="perfil-card">
              <div className="avatar">
                {usuario.nombre
                  ?.charAt(0)
                  ?.toUpperCase() || "U"}
              </div>

              <div>
                <strong>
                  {usuario.nombre}{" "}
                  {usuario.apellidos || ""}
                </strong>

                <p>{usuario.correo}</p>

                <span className="perfil-rol">
                  {usuario.rol}
                </span>
              </div>
            </div>
          </section>

          {/* DASHBOARD DEL ESTUDIANTE */}
          {!esDocente ? (
            <>
              <section className="seccion">
                <h2>
                  Mis módulos de aprendizaje
                </h2>

                <p className="seccion-descripcion">
                  Explora las herramientas disponibles
                  en SmartIA.
                </p>

                <div className="modulos-grid">
                  {modulos.map((modulo) => (
                    <article
                      className="modulo-card"
                      key={modulo.titulo}
                    >
                      <span className="modulo-icono">
                        {modulo.icono}
                      </span>

                      <h3>
                        {modulo.titulo}
                      </h3>

                      <p>
                        {modulo.descripcion}
                      </p>

                      {modulo.area ? (
                        <button
                          className="boton-abrir-modulo"
                          onClick={() =>
                            setVista(modulo.area)
                          }
                        >
                          {modulo.boton}
                        </button>
                      ) : (
                        <span className="estado-modulo">
                          En preparación
                        </span>
                      )}
                    </article>
                  ))}
                </div>
              </section>

              {/* ÁREAS REALES DE MARIA DB */}
              <section className="seccion">
                <h2>
                  Áreas educativas
                </h2>

                <p className="seccion-descripcion">
                  Información obtenida desde nuestra API.
                </p>

                {cargandoAreas && (
                  <p>Cargando áreas...</p>
                )}

                {errorAreas && (
                  <div className="mensaje-error">
                    {errorAreas}
                  </div>
                )}

                {!cargandoAreas &&
                  !errorAreas && (
                    <div className="areas-grid">
                      {areas.map((area) => (
                        <article
                          className="area-card"
                          key={area.idArea}
                        >
                          <h3>
                            {area.nombre}
                          </h3>

                          <p>
                            Código: {area.codigo}
                          </p>

                          <span>
                            Área disponible
                          </span>
                        </article>
                      ))}

                      {areas.length === 0 && (
                        <p>
                          No hay áreas registradas.
                        </p>
                      )}
                    </div>
                  )}
              </section>
            </>
          ) : (
            // DASHBOARD DEL DOCENTE
            <section className="seccion">
              <h2>
                Panel del docente
              </h2>

              <p className="seccion-descripcion">
                Tu acceso de docente fue verificado
                correctamente.
              </p>

              <div className="modulos-grid">
                <article className="modulo-card">
                  <span className="modulo-icono">
                    👥
                  </span>

                  <h3>
                    Mis estudiantes
                  </h3>

                  <p>
                    Consulta los estudiantes asignados.
                  </p>

                  <span className="estado-modulo">
                    En preparación
                  </span>
                </article>

                <article className="modulo-card">
                  <span className="modulo-icono">
                    📈
                  </span>

                  <h3>
                    Seguimiento académico
                  </h3>

                  <p>
                    Revisa historiales y resultados.
                  </p>

                  <span className="estado-modulo">
                    En preparación
                  </span>
                </article>
              </div>
            </section>
          )}
        </main>
      </div>
    </div>
  );
}

export default App;
