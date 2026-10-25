package es.rujibe.bingo90

import android.content.Context
import android.os.Bundle
import android.view.View
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
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
        hideNavigationBar()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White, secondary = Yellow, background = Yellow, surface = Cream, error = Red)) {
                MiBingoApp()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    @Suppress("DEPRECATION")
    private fun hideNavigationBar() {
        // Immersive sticky is compatible with the app's minSdk 23 and
        // lets Android briefly reveal the navigation bar on an upward swipe.
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

    }
}

@Composable
private fun MiBingoApp() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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
    var editorGrid by remember { mutableStateOf(List(3) { List(9) { 0 } }) }
    var editingCell by remember { mutableIntStateOf(-1) }
    val selectedSavedIds = remember { mutableStateListOf<Long>() }
    val selectedChooseIds = remember { mutableStateListOf<Long>() }
    var chooseSearch by remember { mutableStateOf("") }

    fun persistActive() = saveActiveIds(ctx, activeIds.toList())
    fun addTicket(ticket: Ticket) {
        if (activeIds.size < MAX_ACTIVE && ticket.id !in activeIds) activeIds.add(ticket.id)
        persistActive()
    }
    fun startGame(mode: String) {
        pendingMode = mode
        activeIds.clear()
        if (mode == "series") {
            selectedChooseIds.take(count).forEach { activeIds.add(it) }
            if (activeIds.isEmpty()) cards.shuffled().take(count).forEach { activeIds.add(it.id) }
        } else {
            cards.shuffled().take(count).forEach { activeIds.add(it.id) }
        }
        if (activeIds.isEmpty()) {
            val t = Ticket(nextTicketId(cards), "Cartón 1", generateGrid())
            cards.add(t); saveCards(ctx, cards); activeIds.add(t.id)
        }
        cards.indices.forEach { i -> if (cards[i].id in activeIds) cards[i] = cards[i].copy(marked = emptySet()) }
        saveCards(ctx, cards); persistActive()
        page = "game"
        (ctx as? ComponentActivity)?.requestedOrientation =
            if (activeIds.size > 4) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    val compactSix = page == "game" && landscape && activeIds.size > 4
    Box((if (page == "game" && activeIds.size > 3) Modifier.fillMaxSize().background(if (compactSix) Color(0xFF064AA8) else Color.White) else Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DarkRed, Red, Color(0xFFB70E18)))).padding(5.dp).border(2.dp, Yellow, RoundedCornerShape(13.dp)).padding(5.dp))) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(if (compactSix) 0.dp else 9.dp)).then(if (compactSix) Modifier.background(Color(0xFF064AA8)) else Modifier.background(Brush.verticalGradient(listOf(Cream, Yellow))))) {
            if (!(page == "game" && landscape && activeIds.size > 4)) Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFE51B24), Red))).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page != "home") Text("‹", Modifier.clickable { (ctx as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; page = "home" }.padding(end = 14.dp), color = Color.White, style = MaterialTheme.typography.headlineLarge)
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
                    onCreate = { nameInput = ""; numbersInput = ""; message = ""; editingId = null; editorGrid = List(3) { List(9) { 0 } }; page = "create" },
                    onSaved = { page = "saved" }
                )
                "choose" -> Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Elige hasta 6 de los 1.000 cartones disponibles (${selectedChooseIds.size}/${count} seleccionados)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..6).forEach { n ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (count == n) Blue else Color.White).border(1.dp, Blue, RoundedCornerShape(8.dp)).clickable { count = n; while (selectedChooseIds.size > n) selectedChooseIds.removeAt(selectedChooseIds.lastIndex) }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                Text("$n", color = if (count == n) Color.White else Blue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    OutlinedTextField(value = chooseSearch, onValueChange = { chooseSearch = it.filter { ch -> ch.isDigit() } }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Buscar por número de cartón (1–1000)") })
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(cards.filter { it.id in 1L..1000L && (chooseSearch.isBlank() || it.name.filter { ch -> ch.isDigit() }.contains(chooseSearch)) }, key = { it.id }) { ticket ->
                            Card(
                                modifier = Modifier.fillMaxWidth().border(1.dp, if (ticket.id in selectedChooseIds) Green else Blue, RoundedCornerShape(6.dp)),
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = if (ticket.id in selectedChooseIds) Color(0xFFF0FFF0) else Color.White)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = ticket.id in selectedChooseIds, onCheckedChange = { checked ->
                                        if (checked && selectedChooseIds.size < count) selectedChooseIds.add(ticket.id)
                                        if (!checked) selectedChooseIds.remove(ticket.id)
                                    }, enabled = ticket.id in selectedChooseIds || selectedChooseIds.size < count)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(ticket.name, color = Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        Text(if (ticket.id in 1L..1000L) "Serie ${((ticket.id - 1) / 6) + 1} · Cartón ${((ticket.id - 1) % 6) + 1} de 6" else "Cartón personalizado", color = DarkRed, style = MaterialTheme.typography.labelSmall)
                                        TicketPreviewGrid(ticket.grid)
                                    }
                                    Text(if (ticket.id in selectedChooseIds) "✓" else "", color = Green, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                    }
                    Button(onClick = { startGame("series") }, enabled = selectedChooseIds.size == count, modifier = Modifier.fillMaxWidth()) { Text("Jugar con ${selectedChooseIds.size} cartones seleccionados") }
                }
                "random" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("¿Cuántos cartones aleatorios quieres?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..6).forEach { n ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (count == n) Green else Color.White).border(1.dp, Green, RoundedCornerShape(8.dp)).clickable { count = n }.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                                Text("$n", color = if (count == n) Color.White else Green, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    BigButton("🎲", "Jugar", "$count cartones aleatorios", Green) { startGame("random") }
                }
                "game" -> Column(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 1.dp)) {
                    if (compactSix) {
                        Row(
                            Modifier.fillMaxWidth().height(25.dp).background(Color(0xFF064AA8)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "‹ Volver al menú",
                                Modifier.clickable {
                                    (ctx as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    page = "home"
                                }.padding(horizontal = 7.dp, vertical = 2.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                            Text("Mi Bingo 90", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text(
                                "Opciones",
                                Modifier.clickable { options = true }.padding(horizontal = 7.dp, vertical = 2.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Cartones: ${activeIds.size}/6", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { options = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Opciones") }
                            TextButton(onClick = {
                                val toSave = cards.filter { it.id in activeIds }
                                toSave.forEach { ticket ->
                                    val signature = ticket.grid.flatten().filter { it > 0 }.sorted().joinToString(",")
                                    val alreadySaved = cards.any { it.id !in (1L..1000L) && it.grid.flatten().filter { n -> n > 0 }.sorted().joinToString(",") == signature }
                                    if (ticket.id in 1L..1000L && !alreadySaved) {
                                        cards.add(ticket.copy(id = nextTicketId(cards), name = "Guardado - " + ticket.name, marked = emptySet()))
                                    }
                                }
                                saveSeries(ctx, toSave)
                                saveCards(ctx, cards)
                                message = "Cartones guardados. Puedes guardar más cuando quieras."
                            }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Guardar") }
                        }
                        if (message.isNotBlank()) Text(message, color = Green, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    if (activeIds.size > 3) {
                        if (landscape && activeIds.size > 4) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                activeIds.take(6).toList().chunked(2).forEach { rowIds ->
                                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        rowIds.forEach { id ->
                                            val ticketIndex = cards.indexOfFirst { it.id == id }
                                            if (ticketIndex >= 0) {
                                                val ticket = cards[ticketIndex]
                                                TicketPanel(ticket, activeIds.indexOf(id) + 1, compact = true, modifier = Modifier.weight(1f).fillMaxHeight(), onToggle = { n ->
                                                    val j = cards.indexOfFirst { it.id == id }
                                                    if (j >= 0) { val old = cards[j]; cards[j] = old.copy(marked = if (n in old.marked) old.marked - n else old.marked + n); saveCards(ctx, cards) }
                                                }, onClear = {
                                                    val j = cards.indexOfFirst { it.id == id }
                                                    if (j >= 0) { cards[j] = cards[j].copy(marked = emptySet()); saveCards(ctx, cards) }
                                                }, onRemove = {
                                                    activeIds.remove(id); persistActive()
                                                    if (activeIds.size <= 4) (ctx as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                })
                                            }
                                        }
                                        if (rowIds.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }                        } else {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                activeIds.take(4).toList().forEachIndexed { index, id ->
                                    val ticketIndex = cards.indexOfFirst { it.id == id }
                                    if (ticketIndex >= 0) {
                                        val ticket = cards[ticketIndex]
                                        TicketPanel(ticket, index + 1, compact = true, modifier = Modifier.weight(1f).fillMaxWidth(), onToggle = { n ->
                                            val j = cards.indexOfFirst { it.id == id }
                                            if (j >= 0) { val old = cards[j]; cards[j] = old.copy(marked = if (n in old.marked) old.marked - n else old.marked + n); saveCards(ctx, cards) }
                                        }, onClear = {
                                            val j = cards.indexOfFirst { it.id == id }
                                            if (j >= 0) { cards[j] = cards[j].copy(marked = emptySet()); saveCards(ctx, cards) }
                                        }, onRemove = { activeIds.remove(id); persistActive() })
                                    }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            activeIds.toList().forEachIndexed { index, id ->
                                val i = cards.indexOfFirst { it.id == id }
                                if (i >= 0) {
                                    val ticket = cards[i]
                                    TicketPanel(ticket, index + 1, compact = false, onToggle = { n ->
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
                    }
                }
                "create" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nombre del cartón:", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Mi cartón favorito") }, singleLine = true)
                    Text("Toca una casilla para elegir el número. Debe haber 15 números y 5 por fila.", style = MaterialTheme.typography.bodySmall)
                    Column(Modifier.fillMaxWidth().border(2.dp, Blue, RoundedCornerShape(5.dp)).background(Color.White).padding(2.dp)) {
                        for (r in 0..2) Row(Modifier.fillMaxWidth()) {
                            for (c in 0..8) {
                                val n = editorGrid[r][c]
                                Box(Modifier.weight(1f).aspectRatio(.78f).padding(.5.dp).clip(RoundedCornerShape(3.dp)).background(if (n > 0) Color(0xFFEAF3FF) else Color(0xFFF8FAFC)).border(1.dp, if (n > 0) Blue else Color(0xFFB8C9DD), RoundedCornerShape(3.dp)).clickable { editingCell = r * 9 + c }, contentAlignment = Alignment.Center) {
                                    Text(if (n > 0) n.toString() else "·", color = Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editorGrid = List(3) { List(9) { 0 } }; message = "" }, modifier = Modifier.weight(1f)) { Text("Vaciar") }
                        Button(onClick = { editorGrid = generateGrid(); message = "Cartón aleatorio creado; puedes editarlo." }, modifier = Modifier.weight(1f)) { Text("Aleatorio") }
                    }
                    if (message.isNotBlank()) Text(message, color = if (message.startsWith("¡Cartón")) Green else Red)
                    Button(onClick = {
                        val nums = editorGrid.flatten().filter { it > 0 }
                        val valid = nums.size == 15 && nums.distinct().size == 15 && (0..2).all { r -> editorGrid[r].count { it > 0 } == 5 } &&
                            (0..8).all { c -> (0..2).map { r -> editorGrid[r][c] }.filter { it > 0 }.all { n -> n in (if (c == 0) 1 else c * 10)..(if (c == 8) 90 else c * 10 + 9) } }
                        if (!valid) message = "Completa 15 números únicos, 5 por fila y en la columna correcta."
                        else {
                            val ticket = Ticket(editingId ?: nextTicketId(cards), nameInput.ifBlank { "Mi cartón ${cards.count { it.id !in (1L..1000L) } + 1}" }, editorGrid.map { it.toList() })
                            if (editingId != null) { val i = cards.indexOfFirst { it.id == editingId }; if (i >= 0) cards[i] = ticket else cards.add(ticket) } else cards.add(ticket)
                            saveCards(ctx, cards); message = "¡Cartón guardado correctamente!"; editingId = ticket.id
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Guardar cartón") }
                    InfoPanel("Los cartones se guardan en este móvil para futuras partidas sin conexión.")
                }
                "saved" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elige hasta 6 cartones guardados", fontWeight = FontWeight.Bold)
                    cards.filter { it.id !in (1L..1000L) }.forEach { ticket ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = ticket.id in selectedSavedIds, onCheckedChange = { checked ->
                                    if (checked && selectedSavedIds.size < MAX_ACTIVE) selectedSavedIds.add(ticket.id)
                                    if (!checked) selectedSavedIds.remove(ticket.id)
                                }, enabled = ticket.id in selectedSavedIds || selectedSavedIds.size < MAX_ACTIVE)
                                Column(Modifier.weight(1f)) { Text(ticket.name, fontWeight = FontWeight.Bold); Text("Cartón guardado", style = MaterialTheme.typography.bodySmall) }
                                TextButton(onClick = { nameInput = ticket.name; numbersInput = ""; editorGrid = ticket.grid.map { it.toList() }; editingId = ticket.id; message = ""; page = "create" }) { Text("Editar") }
                            }
                        }
                    }
                    if (cards.none { it.id !in (1L..1000L) }) Text("Todavía no has guardado cartones propios.")
                    Button(onClick = {
                        activeIds.clear()
                        selectedSavedIds.take(MAX_ACTIVE).forEach { activeIds.add(it) }
                        cards.indices.forEach { i -> if (cards[i].id in activeIds) cards[i] = cards[i].copy(marked = emptySet()) }
                        saveCards(ctx, cards); persistActive(); page = "game"
                    }, enabled = selectedSavedIds.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Jugar (${selectedSavedIds.size}/6)") }
                    BigButton("+", "Crear nuevo cartón", "", Blue) { nameInput = ""; numbersInput = ""; editorGrid = List(3) { List(9) { 0 } }; editingId = null; message = ""; page = "create" }
                }
                else -> HomePage(onChoose = { page = "choose" }, onRandom = { page = "random" }, onCreate = { page = "create" }, onSaved = { page = "saved" })
            }
        }
    }


    if (editingCell >= 0 && page == "create") {
        val row = editingCell / 9
        val col = editingCell % 9
        val low = if (col == 0) 1 else col * 10
        val high = if (col == 8) 90 else col * 10 + 9
        val existing = editorGrid.flatten().filter { it > 0 && it != editorGrid[row][col] }.toSet()
        AlertDialog(onDismissRequest = { editingCell = -1 }, title = { Text("Elige número ($low–$high)") },
            text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { editorGrid = editorGrid.mapIndexed { r, rowValues -> rowValues.mapIndexed { c, v -> if (r == row && c == col) 0 else v } }; editingCell = -1 }) { Text("Vaciar casilla") }
                (low..high).chunked(5).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        group.forEach { number ->
                            TextButton(onClick = { if (number !in existing) editorGrid = editorGrid.mapIndexed { r, rowValues -> rowValues.mapIndexed { c, v -> if (r == row && c == col) number else v } }; editingCell = -1 }, enabled = number !in existing, modifier = Modifier.weight(1f)) { Text(number.toString()) }
                        }
                        repeat(5-group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { editingCell = -1 }) { Text("Cerrar") } })
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
private fun TicketPreviewGrid(grid: List<List<Int>>) {
    Column(Modifier.fillMaxWidth().border(1.dp, Blue, RoundedCornerShape(3.dp)).padding(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        for (r in 0..2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (c in 0..8) {
                    val n = grid[r][c]
                    Box(
                        Modifier.weight(1f).aspectRatio(1.35f)
                            .background(if (n > 0) Color(0xFFF1F7FF) else Color(0xFFF8FAFC))
                            .border(.35.dp, Color(0xFFB7D0F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (n > 0) Text(n.toString(), color = Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketPanel(ticket: Ticket, position: Int, compact: Boolean = false, modifier: Modifier = Modifier, onToggle: (Int) -> Unit, onClear: () -> Unit, onRemove: () -> Unit) {
    Card(modifier.fillMaxWidth().border(1.dp, if (compact) Color(0xFFB47A40) else Blue, RoundedCornerShape(5.dp)), shape = RoundedCornerShape(5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxSize().padding(if (compact) 0.dp else 5.dp)) {
            if (compact) {
                Row(
                    Modifier.fillMaxWidth().height(21.dp).background(
                        Brush.horizontalGradient(listOf(Color(0xFFF39A2D), Color(0xFFFFFF71), Color(0xFF26A653)))
                    ).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("B", color = Red, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("I", color = Blue, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("N", color = Purple, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("G", color = Green, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("O", color = DarkRed, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("90", color = Blue, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text("CARTÓN $position", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(27.dp).clip(RoundedCornerShape(50)).background(Blue), contentAlignment = Alignment.Center) { Text("$position", color = Color.White, fontWeight = FontWeight.Bold) }
                    Text(ticket.name, Modifier.weight(1f).padding(start = 7.dp), fontWeight = FontWeight.Bold, color = Blue, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Quitar", style = MaterialTheme.typography.labelSmall) }
                }
            }
            Column(Modifier.fillMaxWidth().then(if (compact) Modifier.weight(1f) else Modifier).border(if (compact) .5.dp else 1.dp, if (compact) Color(0xFFB6B6B6) else Blue).padding(if (compact) 0.dp else 1.dp)) {
                for (r in 0..2) Row(Modifier.fillMaxWidth().then(if (compact) Modifier.weight(1f) else Modifier)) {
                    for (c in 0..8) {
                        val n = ticket.grid[r][c]
                        val marked = n > 0 && n in ticket.marked
                        Box(Modifier.weight(1f).fillMaxHeight().padding(if (compact) .25.dp else .5.dp).background(if (marked) Color(0xFF75D65B) else Color.White).border(if (compact) .35.dp else .45.dp, if (compact) Color(0xFFB6B6B6) else Color(0xFF91B9F8)).then(if (n > 0) Modifier.clickable { onToggle(n) } else Modifier), contentAlignment = Alignment.Center) {
                            if (n > 0) Text(n.toString(), color = if (marked) Color(0xFF123D12) else if (compact) Color(0xFF171717) else Blue, fontWeight = FontWeight.Bold, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
            if (!compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
    val signatures = mutableSetOf<String>()
    while (initial.size + 6 <= 1000) {
        val series = generateSeries()
        val uniqueSeries = series.map { grid -> grid to grid.flatten().filter { it > 0 }.sorted().joinToString(",") }
        if (uniqueSeries.any { it.second in signatures } || uniqueSeries.map { it.second }.distinct().size != 6) continue
        uniqueSeries.forEach { (grid, signature) ->
            signatures.add(signature)
            initial.add(Ticket(initial.size.toLong() + 1, "Cartón ${initial.size + 1}", grid))
        }
    }
    while (initial.size < 1000) {
        val grid = generateGrid()
        val signature = grid.flatten().filter { it > 0 }.sorted().joinToString(",")
        if (signatures.add(signature)) initial.add(Ticket(initial.size.toLong() + 1, "Cartón ${initial.size + 1}", grid))
    }
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
