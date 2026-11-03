package com.smartia.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartia.app.data.ConcursoResumen

// ---------- Paleta de SmartIA ----------
private val Marino = Color(0xFF14204A)
private val Indigo = Color(0xFF3949AB)
private val Ambar = Color(0xFFFFC857)
private val Verde = Color(0xFF1E9E6A)
private val Rojo = Color(0xFFD64545)
private val AzulLectura = Color(0xFF3F5EFB)
private val NaranjaMate = Color(0xFFE8590C)

private val DegradadoEncabezado = Brush.linearGradient(listOf(Marino, Indigo))

val EsquemaSmartia = lightColorScheme(
    primary = Marino,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E9FF),
    onPrimaryContainer = Marino,
    secondary = Ambar,
    onSecondary = Marino,
    background = Color(0xFFF4F6FB),
    onBackground = Marino,
    surface = Color.White,
    onSurface = Marino,
    surfaceVariant = Color(0xFFE8ECF7),
    onSurfaceVariant = Color(0xFF5A6585),
    outline = Color(0xFF8A94AE),
    outlineVariant = Color(0xFFD5DBEA),
    error = Rojo
)

// El servidor manda el area en mayusculas (LECTURA / MATEMATICAS)
private fun nombreArea(area: String) =
    if (area.equals("matematicas", ignoreCase = true)) "Matemáticas" else "Lectura"

@Composable
fun SmartiaApp(vm: ConcursoViewModel = viewModel()) {
    val estado = vm.estado
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (estado.pantalla) {
            Pantalla.CONCURSOS -> ConcursosScreen(estado, vm::iniciar, vm::cargarConcursos)
            Pantalla.PREGUNTA -> PreguntaScreen(estado, vm::elegirOpcion, vm::responder)
            Pantalla.RESULTADO -> ResultadoScreen(
                estado,
                vm::cargarResultado,
                vm::cargarFeedback,
                vm::volverAlInicio
            )
        }
    }
}

// Encabezado con degradado que llega hasta arriba de la pantalla
@Composable
private fun Encabezado(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                DegradadoEncabezado,
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = contenido
    )
}

/* ---------- Pantalla 1: elegir concurso ---------- */
@Composable
fun ConcursosScreen(estado: EstadoUi, onIniciar: (Int) -> Unit, onReintentar: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Encabezado {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Ambar),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", color = Marino, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text("SmartIA", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(22.dp))
            Text("¡Hola! 👋", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            Text("¿Listo para el reto?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Elige un concurso, responde y mide tu rendimiento.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp)
        ) {
            Text(
                "Concursos disponibles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            if (estado.cargando) CircularProgressIndicator()

            estado.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onReintentar) { Text("Reintentar") }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(estado.concursos) { c ->
                    TarjetaConcurso(c, !estado.cargando) { onIniciar(c.idConcurso) }
                }
            }
        }
    }
}

@Composable
private fun Etiqueta(texto: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(
            texto,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TarjetaConcurso(c: ConcursoResumen, habilitado: Boolean, onClick: () -> Unit) {
    val esMate = c.area.equals("matematicas", ignoreCase = true)
    val color = if (esMate) NaranjaMate else AzulLectura
    val degradado = Brush.linearGradient(
        if (esMate) listOf(Color(0xFFE8590C), Color(0xFFFFB347))
        else listOf(Color(0xFF3F5EFB), Color(0xFF7C9CFF))
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(degradado),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (esMate) "🧮" else "📖", fontSize = 28.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Etiqueta(nombreArea(c.area), color)
                        Etiqueta("${c.totalPreguntas} preguntas", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(
                onClick = onClick,
                enabled = habilitado,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) { Text("Comenzar concurso  →", fontWeight = FontWeight.Bold) }
        }
    }
}

/* ---------- Pantalla 2: una pregunta a la vez ---------- */
@Composable
fun PreguntaScreen(estado: EstadoUi, onElegir: (Int) -> Unit, onSiguiente: () -> Unit) {
    val concurso = estado.concurso ?: return
    val total = concurso.preguntas.size
    val pregunta = concurso.preguntas[estado.indice]
    val esUltima = estado.indice + 1 == total
    var verTexto by remember(estado.concurso) { mutableStateOf(true) }
    val letras = listOf("A", "B", "C", "D", "E", "F")

    Column(Modifier.fillMaxSize()) {
        Encabezado {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    concurso.titulo,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(50), color = Ambar) {
                    Text(
                        "${estado.indice + 1} / $total",
                        Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        color = Marino,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { (estado.indice + 1).toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = Ambar,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Pregunta ${estado.indice + 1} de $total",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!concurso.textoBase.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📖  Lee el texto", fontWeight = FontWeight.Bold)
                            TextButton(onClick = { verTexto = !verTexto }) {
                                Text(if (verTexto) "Ocultar" else "Mostrar")
                            }
                        }
                        if (verTexto) {
                            Text(
                                concurso.textoBase,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    pregunta.enunciado,
                    Modifier.padding(18.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            pregunta.opciones.forEachIndexed { i, o ->
                OpcionItem(
                    letra = letras.getOrElse(i) { "•" },
                    texto = o.texto,
                    elegida = estado.opcionElegida == o.idOpcion
                ) { onElegir(o.idOpcion) }
            }
        }

        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding()
        ) {
            estado.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 6.dp))
            }
            Button(
                onClick = onSiguiente,
                enabled = estado.opcionElegida != null && !estado.cargando,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    if (esUltima) "Terminar concurso" else "Siguiente pregunta",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun OpcionItem(letra: String, texto: String, elegida: Boolean, onClick: () -> Unit) {
    val colores = MaterialTheme.colorScheme
    val fondo by animateColorAsState(
        targetValue = if (elegida) colores.primaryContainer else colores.surface,
        animationSpec = tween(180), label = "fondo"
    )
    val borde by animateColorAsState(
        targetValue = if (elegida) colores.primary else colores.outlineVariant,
        animationSpec = tween(180), label = "borde"
    )
    val insignia by animateColorAsState(
        targetValue = if (elegida) colores.primary else colores.surfaceVariant,
        animationSpec = tween(180), label = "insignia"
    )
    val forma = RoundedCornerShape(16.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(fondo)
            .border(if (elegida) 2.dp else 1.dp, borde, forma)
            .selectable(selected = elegida, onClick = onClick, role = Role.RadioButton)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(insignia),
            contentAlignment = Alignment.Center
        ) {
            Text(
                letra,
                fontWeight = FontWeight.Bold,
                color = if (elegida) colores.onPrimary else colores.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/* ---------- Pantalla 3: resultado de la participación ---------- */
@Composable
fun ResultadoScreen(
    estado: EstadoUi,
    onReintentar: () -> Unit,
    onReintentarFeedback: () -> Unit,
    onVolver: () -> Unit
) {
    val r = estado.resultado
    val maximo = r?.puntajeMaximo ?: 0
    val porcentaje = if (r != null && maximo > 0) r.puntajeTotal * 100 / maximo else 0

    val (emoji, titulo, detalle) = when {
        porcentaje >= 80 -> Triple("🏆", "¡Excelente trabajo!", "Dominas muy bien este tema.")
        porcentaje >= 50 -> Triple("👏", "¡Buen intento!", "Puedes mejorar. Inténtalo otra vez.")
        else -> Triple("💪", "Sigue practicando", "Cada intento te acerca a la meta.")
    }
    val colorAnillo = when {
        porcentaje >= 80 -> Verde
        porcentaje >= 50 -> Color(0xFFF2A900)
        else -> Rojo
    }
    val colorPista = MaterialTheme.colorScheme.surfaceVariant

    var animar by remember { mutableStateOf(false) }
    LaunchedEffect(r) { if (r != null) animar = true }
    val progreso by animateFloatAsState(
        targetValue = if (animar) porcentaje / 100f else 0f,
        animationSpec = tween(1200),
        label = "anillo"
    )

    Column(Modifier.fillMaxSize()) {
        Encabezado {
            Text("Concurso terminado", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                r?.concurso ?: "Tu resultado",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (estado.cargando && r == null) CircularProgressIndicator()

            if (r != null) {
                Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val grosor = 18.dp.toPx()
                        val lado = Size(size.width - grosor, size.height - grosor)
                        val esquina = Offset(grosor / 2, grosor / 2)
                        drawArc(
                            color = colorPista,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = esquina,
                            size = lado,
                            style = Stroke(width = grosor, cap = StrokeCap.Round)
                        )
                        if (progreso > 0f) {
                            drawArc(
                                color = colorAnillo,
                                startAngle = -90f,
                                sweepAngle = 360f * progreso,
                                useCenter = false,
                                topLeft = esquina,
                                size = lado,
                                style = Stroke(width = grosor, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${r.puntajeTotal} / $maximo", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "$porcentaje %",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(emoji, fontSize = 40.sp)
                Text(
                    titulo,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    detalle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CajaDato("✓", r.aciertos.toString(), "Correctas", Verde)
                    CajaDato("✗", (r.totalPreguntas - r.aciertos).toString(), "Incorrectas", Rojo)
                }

                Spacer(Modifier.height(18.dp))
                TarjetaFeedback(estado, onReintentarFeedback)
            }

            estado.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onReintentar) { Text("Reintentar") }
            }
        }

        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding()
        ) {
            Button(
                onClick = onVolver,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) { Text("Volver a los concursos", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

// Tarjeta con la retroalimentacion generada por la IA
@Composable
private fun TarjetaFeedback(estado: EstadoUi, onReintentar: () -> Unit) {
    val texto = estado.feedback
    val fallo = estado.errorFeedback
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🤖  Retroalimentación de SmartIA", fontWeight = FontWeight.Bold)
            when {
                estado.cargandoFeedback -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Analizando tus respuestas…")
                }
                texto != null -> Text(texto, lineHeight = 22.sp)
                fallo != null -> {
                    Text(fallo, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onReintentar) { Text("Reintentar") }
                }
            }
        }
    }
}

@Composable
private fun RowScope.CajaDato(icono: String, valor: String, etiqueta: String, color: Color) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icono, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(valor, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Text(
                etiqueta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}