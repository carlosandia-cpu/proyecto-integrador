# SmartIA - Backend de usuario (Spring Boot)

## Requisitos
- JDK 21 (el 26 puede dar errores con Spring Boot)
- XAMPP con MySQL/MariaDB encendido
- Base de datos: importar `smartia.sql` en phpMyAdmin

## Ejecutar
1. Abrir esta carpeta en IntelliJ IDEA (o VS Code con extension de Java), que detecta el `pom.xml`.
2. Elegir JDK 21 como SDK del proyecto.
3. (IA) Crear una clave en https://aistudio.google.com y definir la variable de entorno:
   - Windows (PowerShell): `$env:GEMINI_API_KEY="tu_clave"`
   - o en IntelliJ: Run > Edit Configurations > Environment variables
4. Ejecutar `SmartiaApplication`. Queda en http://localhost:8080

## Endpoints
| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | /api/concursos | Concursos activos |
| GET | /api/concursos/{id}/preguntas | Preguntas con opciones (sin revelar la correcta) |
| POST | /api/estudiantes | `{"nombre":"Ana","grado":"5to"}` |
| POST | /api/intentos | `{"idEstudiante":1,"idConcurso":1}` |
| POST | /api/intentos/{id}/respuestas | `{"idPregunta":1,"idOpcion":1}` |
| POST | /api/intentos/{id}/finalizar | Cierra el intento y devuelve el resultado |
| GET | /api/intentos/{id}/resultado | Puntaje y detalle |
| POST | /api/intentos/{id}/feedback | Retroalimentacion generada por IA |

## Prueba rapida
```
curl http://localhost:8080/api/concursos
curl -X POST http://localhost:8080/api/intentos -H "Content-Type: application/json" -d "{\"idEstudiante\":1,\"idConcurso\":2}"
curl -X POST http://localhost:8080/api/intentos/1/respuestas -H "Content-Type: application/json" -d "{\"idPregunta\":3,\"idOpcion\":9}"
curl -X POST http://localhost:8080/api/intentos/1/finalizar
curl -X POST http://localhost:8080/api/intentos/1/feedback
```

## Desde la app Android
- Emulador: base URL `http://10.0.2.2:8080/api/`
- Celular real: `http://IP_DE_TU_PC:8080/api/` (misma red wifi, abrir el puerto 8080 en el firewall)
- Como es http, agregar en AndroidManifest.xml dentro de `<application>`: `android:usesCleartextTraffic="true"`
