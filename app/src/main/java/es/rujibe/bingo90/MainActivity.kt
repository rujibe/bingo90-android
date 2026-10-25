package es.rujibe.bingo90

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private val Red = Color(0xFFC9141E)
private val DarkRed = Color(0xFF9E1018)
private val Yellow = Color(0xFFFFE889)
private val Cream = Color(0xFFFFF7D8)
private val Blue = Color(0xFF0878E8)
private val Green = Color(0xFF16A34A)
private val Purple = Color(0xFF7717D6)
private const val MAX_ACTIVE = 6
private data class Ticket(val id: Long, val name: String, val grid: List<List<Int>>, val marked: Set<Int> = emptySet())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White, secondary = Yellow, background = Yellow, surface = Cream, error = Red)) {
                MiBingoApp()
            }
        }
    }
}

@Composable
private fun MiBingoApp() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cards = remember { mutableStateListOf<Ticket>().apply { addAll(loadCards(ctx)) } }
    val activeIds = remember { mutableStateListOf<Long>().apply {
        addAll(loadActiveIds(ctx).filter { id -> cards.any { it.id == id } }.distinct().take(MAX_ACTIVE))
    } }
    var page by remember { mutableStateOf("home") }
    var count by remember { mutableIntStateOf(6) }
    var pendingMode by remember { mutableStateOf("series") }
    var options by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var numbersInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }

    fun persistActive() = saveActiveIds(ctx, activeIds.toList())
    fun addTicket(ticket: Ticket) {
        if (activeIds.size < MAX_ACTIVE && ticket.id !in activeIds) activeIds.add(ticket.id)
        persistActive()
    }
    fun startGame(mode: String) {
        pendingMode = mode
        activeIds.clear()
        if (mode == "series") {
            val start = Random.nextInt(0, (cards.size / 6).coerceAtLeast(1)) * 6
            cards.drop(start).take(6).take(count).forEach { activeIds.add(it.id) }
        } else {
            val seriesCount = (cards.size / 6).coerceAtLeast(1)
            val seriesStart = Random.nextInt(seriesCount) * 6
            cards.drop(seriesStart).take(6).shuffled().take(count).forEach { activeIds.add(it.id) }
        }
        if (activeIds.isEmpty()) {
            val t = Ticket(nextTicketId(cards), "Cartón 1", generateGrid())
            cards.add(t); saveCards(ctx, cards); activeIds.add(t.id)
        }
        cards.indices.forEach { i -> if (cards[i].id in activeIds) cards[i] = cards[i].copy(marked = emptySet()) }
        saveCards(ctx, cards); persistActive()
        page = "game"
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DarkRed, Red, Color(0xFFB70E18)))).padding(5.dp).border(2.dp, Yellow, RoundedCornerShape(13.dp)).padding(5.dp)) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).background(Brush.verticalGradient(listOf(Cream, Yellow)))) {
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFE51B24), Red))).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page != "home") Text("‹", Modifier.clickable { page = "home" }.padding(end = 14.dp), color = Color.White, style = MaterialTheme.typography.headlineLarge)
                Column(Modifier.weight(1f)) {
                    Text(if (page == "home") "MI BINGO" else when (page) {
                        "choose" -> "Elegir cartones"
                        "game" -> "Mi Bingo 90"
                        "create" -> "Crea tus propios cartones"
                        "saved" -> "Cartones guardados"
                        else -> "Selección de cartones"
                    }, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    if (page == "home") Text("TUS CARTONES, TU PARTIDA", color = Yellow, style = MaterialTheme.typography.labelSmall)
                }
                if (page == "game") Text("↻", Modifier.clickable { options = true }.padding(4.dp), color = Yellow, style = MaterialTheme.typography.headlineMedium)
            }

            when (page) {
                "home" -> HomePage(
                    onChoose = { page = "choose" },
                    onRandom = { page = "random" },
                    onCreate = { nameInput = ""; numbersInput = ""; message = ""; editingId = null; page = "create" },
                    onSaved = { page = "saved" }
                )
                "choose" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Selecciona cómo quieres obtener tus cartones:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    BigButton("▦", "Cartones de la serie", "($count cartones)", Blue) { startGame("series") }
                    BigButton("🎲", "Selección aleatoria", "($count cartones)", Green) { startGame("random") }
                    BigButton("123", "Elegir número de cartones", "(1 a 6 · actual: $count)", Purple) { page = "count" }
                    InfoPanel("Los cartones se eligen de la misma serie. Hay 1.000 cartones disponibles, organizados en series de 6.")
                }
                "count" -> Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("¿Cuántos cartones quieres?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..6).forEach { n ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (count == n) Blue else Color.White).border(1.dp, Blue, RoundedCornerShape(8.dp)).clickable { count = n }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                                Text("$n", color = if (count == n) Color.White else Blue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    BigButton("✓", "Guardar cantidad", "$count cartones por partida", Green) { page = "choose" }
                }
                "game" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Cartones en la partida: ${activeIds.size}/6", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { options = true }) { Text("Opciones") }
                        TextButton(onClick = {
                            cards.filter { it.id in activeIds }.let { saveSeries(ctx, it) }
                            message = "Serie guardada en este móvil."
                        }) { Text("Guardar serie") }
                    }
                    if (message.isNotBlank()) Text(message, color = Green, style = MaterialTheme.typography.bodySmall)
                    activeIds.toList().forEachIndexed { index, id ->
                        val i = cards.indexOfFirst { it.id == id }
                        if (i >= 0) {
                            val ticket = cards[i]
                            TicketPanel(ticket, index + 1, onToggle = { n ->
                                val j = cards.indexOfFirst { it.id == id }
                                if (j >= 0) { val old = cards[j]; cards[j] = old.copy(marked = if (n in old.marked) old.marked - n else old.marked + n); saveCards(ctx, cards) }
                            }, onClear = {
                                val j = cards.indexOfFirst { it.id == id }
                                if (j >= 0) { cards[j] = cards[j].copy(marked = emptySet()); saveCards(ctx, cards) }
                            }, onRemove = { activeIds.remove(id); persistActive() })
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                "create" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Nombre del cartón:", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Ejemplo: Mi cartón favorito") }, singleLine = true)
                    Text("Escribe 15 números distintos del 1 al 90, separados por comas.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = numbersInput, onValueChange = { numbersInput = it; message = "" }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), placeholder = { Text("6, 10, 23, 35, 46, 53...") })
                    if (message.isNotBlank()) Text(message, color = Red)
                    BigButton("🎲", "Generar cartón", "Crear números automáticamente", Blue) { numbersInput = generateGrid().flatten().filter { it > 0 }.joinToString(", "); message = "Puedes guardar este cartón o editar sus números." }
                    BigButton("✎", "Guardar cartón", "Guardar permanentemente en el móvil", Green) {
                        val parts = numbersInput.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
                        val nums = parts.mapNotNull { it.toIntOrNull() }
                        val grid = if (parts.size == 15 && nums.size == 15) makeGrid(nums) else null
                        if (grid == null) message = "Revisa los 15 números: deben ser únicos y válidos para un cartón de bingo."
                        else {
                            val ticket = Ticket(editingId ?: nextTicketId(cards), nameInput.ifBlank { "Mi cartón ${cards.size + 1}" }, grid)
                            if (editingId != null) { val i = cards.indexOfFirst { it.id == editingId }; if (i >= 0) cards[i] = ticket else cards.add(ticket) } else cards.add(ticket)
                            saveCards(ctx, cards)
                            message = "¡Cartón guardado!"
                        }
                    }
                    InfoPanel("Tus cartones se guardan en el móvil para usarlos en futuras partidas.")
                }
                "saved" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cartones guardados en este móvil", fontWeight = FontWeight.Bold)
                    cards.filter { it.name.startsWith("Mi cartón") || it.name.startsWith("Mi ") }.forEach { ticket ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(ticket.name, fontWeight = FontWeight.Bold); Text("Creado y guardado en el dispositivo", style = MaterialTheme.typography.bodySmall) }
                                TextButton(onClick = { if (activeIds.size < 6 && ticket.id !in activeIds) activeIds.add(ticket.id); persistActive(); page = "game" }) { Text("Usar") }
                                TextButton(onClick = { nameInput = ticket.name; numbersInput = ticket.grid.flatten().filter { it > 0 }.joinToString(", "); editingId = ticket.id; page = "create" }) { Text("Editar") }
                            }
                        }
                    }
                    if (cards.none { it.name.startsWith("Mi cartón") || it.name.startsWith("Mi ") }) Text("Todavía no has guardado cartones propios.")
                    val savedSeries = loadSavedSeries(ctx)
                    if (savedSeries.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Series guardadas", fontWeight = FontWeight.Bold)
                        savedSeries.forEachIndexed { seriesIndex, series ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Serie guardada ${seriesIndex + 1}", fontWeight = FontWeight.Bold)
                                        Text("${series.size} cartones", style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = {
                                        activeIds.clear()
                                        series.take(count).forEach { ticket ->
                                            val existing = cards.firstOrNull { it.id == ticket.id }
                                            if (existing == null) cards.add(ticket.copy(marked = emptySet()))
                                            if (activeIds.size < count) activeIds.add(ticket.id)
                                        }
                                        saveCards(ctx, cards); persistActive(); page = "game"
                                    }) { Text("Usar serie") }
                                }
                            }
                        }
                    }
                    BigButton("+", "Crear nuevo cartón", "", Blue) { nameInput = ""; numbersInput = ""; editingId = null; page = "create" }
                }
                else -> HomePage(onChoose = { page = "choose" }, onRandom = { startGame("random") }, onCount = { page = "count" }, onCreate = { page = "create" }, onSaved = { page = "saved" })
            }
        }
    }

    if (options) AlertDialog(
        onDismissRequest = { options = false },
        title = { Text("Opciones", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("💾  Guardar cartones", Modifier.fillMaxWidth().clickable { saveSeries(ctx, cards.filter { it.id in activeIds }); message = "Serie guardada."; options = false }.padding(8.dp))
            HorizontalDivider()
            Text("¿Desea jugar con los mismos cartones?", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { cards.indices.forEach { i -> if (cards[i].id in activeIds) cards[i] = cards[i].copy(marked = emptySet()) }; saveCards(ctx, cards); options = false; message = "Marcas borradas. ¡Nueva partida!" }, modifier = Modifier.weight(1f)) { Text("Sí") }
                OutlinedButton(onClick = { options = false; page = "home" }, modifier = Modifier.weight(1f)) { Text("No") }
            }
            HorizontalDivider()
            Text("✖  Volver al menú principal", Modifier.fillMaxWidth().clickable { options = false; page = "home" }.padding(8.dp))
        } },
        confirmButton = { TextButton(onClick = { options = false }) { Text("Cerrar") } }
    )
}

@Composable
private fun HomePage(onChoose: () -> Unit, onRandom: () -> Unit, onCreate: () -> Unit, onSaved: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("👑", style = MaterialTheme.typography.displayLarge)
        Text("Mi Bingo", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Blue)
        Text("BINGO 90", style = MaterialTheme.typography.titleLarge, color = Red, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        BigButton("▦", "Elegir cartones", "", Blue, onChoose)
        BigButton("🎲", "Selección aleatoria", "", Green, onRandom)
        BigButton("✎", "Crea tus propios cartones", "", Color(0xFFED5A0A), onCreate)
        BigButton("▣", "Cartones guardados", "", Red, onSaved)
        Spacer(Modifier.height(4.dp))
        InfoPanel("Hasta 6 cartones a la vez · Sin conexión · Sin anuncios · Gratis")
    }
}

@Composable
private fun BigButton(icon: String, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Brush.verticalGradient(listOf(color, color.copy(red = (color.red * .78f).coerceAtLeast(0f), green = (color.green * .78f).coerceAtLeast(0f), blue = (color.blue * .78f).coerceAtLeast(0f))))).border(1.dp, Color.White.copy(alpha = .8f), RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center)
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoPanel(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color(0xFFFFE9A6)).border(1.dp, Color(0xFFD8BD65), RoundedCornerShape(9.dp)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("ⓘ", color = Color(0xFF725D28), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF493D24))
    }
}

@Composable
private fun TicketPanel(ticket: Ticket, position: Int, onToggle: (Int) -> Unit, onClear: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth().border(1.dp, Blue, RoundedCornerShape(5.dp)), shape = RoundedCornerShape(5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(27.dp).clip(RoundedCornerShape(50)).background(Blue), contentAlignment = Alignment.Center) { Text("$position", color = Color.White, fontWeight = FontWeight.Bold) }
                Text(ticket.name, Modifier.weight(1f).padding(start = 7.dp), fontWeight = FontWeight.Bold, color = Blue, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Quitar", style = MaterialTheme.typography.labelSmall) }
            }
            Column(Modifier.fillMaxWidth().border(1.dp, Blue).padding(1.dp)) {
                for (r in 0..2) Row(Modifier.fillMaxWidth()) {
                    for (c in 0..8) {
                        val n = ticket.grid[r][c]
                        val marked = n > 0 && n in ticket.marked
                        Box(Modifier.weight(1f).aspectRatio(1.15f).padding(.5.dp).background(if (marked) Color(0xFF75D65B) else Color.White).border(.45.dp, Color(0xFF91B9F8)).then(if (n > 0) Modifier.clickable { onToggle(n) } else Modifier), contentAlignment = Alignment.Center) {
                            if (n > 0) Text(n.toString(), color = if (marked) Color(0xFF123D12) else Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${ticket.marked.size}/15 marcados", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Quitar marcas", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

private fun nextTicketId(cards: List<Ticket>): Long = maxOf(System.currentTimeMillis(), (cards.maxOfOrNull { it.id } ?: 0L) + 1L)
private fun loadActiveIds(ctx: Context): List<Long> {
    val saved = ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).getString("activeIds", null) ?: return emptyList()
    return try { val a = JSONArray(saved); (0 until a.length()).map { a.getLong(it) } } catch (_: Exception) { emptyList() }
}
private fun saveActiveIds(ctx: Context, ids: List<Long>) { ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).edit().putString("activeIds", JSONArray(ids).toString()).apply() }
private fun loadCards(ctx: Context): List<Ticket> {
    val saved = ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).getString("cards", null)
    if (saved != null) try {
        val arr = JSONArray(saved); val out = mutableListOf<Ticket>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val rows = o.getJSONArray("grid")
            val grid = (0 until rows.length()).map { r -> val row = rows.getJSONArray(r); (0 until row.length()).map { c -> row.getInt(c) } }
            val marks = o.getJSONArray("marked")
            out.add(Ticket(o.getLong("id"), o.getString("name"), grid, (0 until marks.length()).map { marks.getInt(it) }.toSet()))
        }
        if (out.isNotEmpty()) return out
    } catch (_: Exception) {}
    val initial = mutableListOf<Ticket>()
    while (initial.size + 6 <= 1000) {
        val series = generateSeries()
        series.forEach { grid -> initial.add(Ticket(initial.size.toLong() + 1, "Cartón ${initial.size + 1}", grid)) }
    }
    while (initial.size < 1000) initial.add(Ticket(initial.size.toLong() + 1, "Cartón ${initial.size + 1}", generateGrid()))
    saveCards(ctx, initial); return initial
}
private fun saveCards(ctx: Context, cards: List<Ticket>) {
    val arr = JSONArray()
    cards.forEach { t ->
        val o = JSONObject().put("id", t.id).put("name", t.name)
        val rows = JSONArray(); t.grid.forEach { rows.put(JSONArray(it)) }
        o.put("grid", rows).put("marked", JSONArray(t.marked.toList())); arr.put(o)
    }
    ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).edit().putString("cards", arr.toString()).apply()
}
private fun saveSeries(ctx: Context, cards: List<Ticket>) {
    val all = loadSavedSeries(ctx).toMutableList()
    all.add(cards.map { it.copy(marked = emptySet()) })
    val outer = JSONArray()
    all.forEach { series ->
        val arr = JSONArray()
        series.forEach { t ->
            val o = JSONObject().put("id", t.id).put("name", t.name)
            val rows = JSONArray(); t.grid.forEach { rows.put(JSONArray(it)) }
            o.put("grid", rows).put("marked", JSONArray()); arr.put(o)
        }
        outer.put(arr)
    }
    ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).edit().putString("savedSeries", outer.toString()).apply()
}
private fun loadSavedSeries(ctx: Context): List<List<Ticket>> {
    val saved = ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).getString("savedSeries", null) ?: return emptyList()
    return try {
        val outer = JSONArray(saved)
        (0 until outer.length()).map { si ->
            val arr = outer.getJSONArray(si)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val rows = o.getJSONArray("grid")
                val grid = (0 until rows.length()).map { r ->
                    val row = rows.getJSONArray(r)
                    (0 until row.length()).map { c -> row.getInt(c) }
                }
                Ticket(o.getLong("id"), o.getString("name"), grid)
            }
        }
    } catch (_: Exception) { emptyList() }
}
private fun generateSeries(): List<List<List<Int>>> {
    // A full strip uses every number 1..90 exactly once, spread over six valid tickets.
    while (true) {
        val counts = Array(6) { IntArray(9) { 1 } }
        val extrasPerColumn = listOf(3, 4, 4, 4, 4, 4, 4, 4, 5)
        for (c in 0..8) {
            (0 until 6).shuffled().take(extrasPerColumn[c]).forEach { r -> counts[r][c]++ }
        }
        if ((0 until 6).any { r -> counts[r].sum() != 15 }) continue
        val ticketColumns = Array(6) { MutableList(9) { emptyList<Int>() } }
        for (c in 0..8) {
            val low = if (c == 0) 1 else c * 10
            val high = if (c == 8) 90 else c * 10 + 9
            val numbers = (low..high).shuffled()
            var index = 0
            for (r in 0 until 6) {
                val amount = counts[r][c]
                ticketColumns[r][c] = numbers.drop(index).take(amount).sorted()
                index += amount
            }
        }
        val grids = mutableListOf<List<List<Int>>>()
        var valid = true
        for (r in 0 until 6) {
            val grid = placeColumns(ticketColumns[r])
            if (grid == null) { valid = false; break }
            grids.add(grid)
        }
        if (valid && grids.size == 6) return grids
    }
}
private fun generateGrid(): List<List<Int>> {
    while (true) {
        val counts = MutableList(9) { Random.nextInt(1, 4) }
        while (counts.sum() > 15) { val c = counts.indices.filter { counts[it] > 1 }.random(); counts[c]-- }
        while (counts.sum() < 15) { val c = counts.indices.filter { counts[it] < 3 }.random(); counts[c]++ }
        val cols = (0..8).map { c -> val low = if (c == 0) 1 else c * 10; val high = if (c == 8) 90 else c * 10 + 9; (low..high).shuffled().take(counts[c]).sorted() }
        placeColumns(cols)?.let { return it }
    }
}
private fun makeGrid(nums: List<Int>): List<List<Int>>? {
    if (nums.size != 15 || nums.distinct().size != 15 || nums.any { it !in 1..90 }) return null
    val cols = (0..8).map { c -> nums.filter { (if (it < 10) 0 else if (it >= 80) 8 else it / 10) == c }.sorted() }
    if (cols.any { it.isEmpty() || it.size > 3 }) return null
    return placeColumns(cols)
}
private fun placeColumns(columns: List<List<Int>>): List<List<Int>>? {
    repeat(1000) {
        val grid = Array(3) { IntArray(9) }; val used = IntArray(3); var ok = true
        for (c in 0..8) {
            val rows = (0..2).filter { used[it] < 5 }.shuffled()
            if (rows.size < columns[c].size) { ok = false; break }
            rows.take(columns[c].size).sorted().forEachIndexed { i, r -> grid[r][c] = columns[c][i]; used[r]++ }
        }
        if (ok && used.all { it == 5 }) return grid.map { it.toList() }
    }
    return null
}
