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
private data class TicketPackage(val id: Long, val name: String, val tickets: List<Ticket>)

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
    val ticketPackages = remember {
        val loaded = loadTicketPackages(ctx)
        val ids = cards.map { it.id } + loaded.flatMap { it.tickets.map { t -> t.id } }
        if (ids.size != ids.distinct().size) {
            var nextId = maxOf(System.currentTimeMillis(), (ids.maxOrNull() ?: 0L) + 1L)
            val migrated = loaded.map { p -> p.copy(tickets = p.tickets.map { it.copy(id = nextId++) }) }
            saveTicketPackages(ctx, migrated)
            mutableStateListOf<TicketPackage>().apply { addAll(migrated) }
        } else mutableStateListOf<TicketPackage>().apply { addAll(loaded) }
    }
    // Remove legacy individual copies created by the old "Guardar cartones" action
    // when the same ticket already belongs to a saved package. Keep user-created singles.
    val packageSignatures = ticketPackages.flatMap { it.tickets }.map {
        it.grid.flatten().filter { n -> n > 0 }.sorted().joinToString(",")
    }.toSet()
    // Old builds copied package tickets into the standalone library. Remove matching
    // copies regardless of their legacy display name, while keeping unrelated singles.
    val legacyDuplicates = cards.filter { ticket ->
        ticket.grid.flatten().filter { it > 0 }.sorted().joinToString(",") in packageSignatures
    }
    if (legacyDuplicates.isNotEmpty()) {
        cards.removeAll(legacyDuplicates.toSet())
        saveCards(ctx, cards)
    }
    val activeIds = remember { mutableStateListOf<Long>().apply {
        addAll(loadActiveIds(ctx).filter { id -> cards.any { it.id == id } || ticketPackages.any { p -> p.tickets.any { it.id == id } } }.distinct().take(MAX_ACTIVE))
    } }
    var page by remember { mutableStateOf("home") }
    var count by remember { mutableIntStateOf(6) }
    var pendingMode by remember { mutableStateOf("series") }
    var options by remember { mutableStateOf(false) }
    var confirmClearMarks by remember { mutableStateOf(false) }
    var canUndoMarks by remember { mutableStateOf(hasMarksSnapshot(ctx)) }
    var nameInput by remember { mutableStateOf("") }
    var groupNameInput by remember { mutableStateOf("") }
    var numbersInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editorGrid by remember { mutableStateOf(List(3) { List(9) { 0 } }) }
    var editingCell by remember { mutableIntStateOf(-1) }
    val selectedSavedIds = remember { mutableStateListOf<Long>() }
    val selectedPackageTicketKeys = remember { mutableStateListOf<String>() }
    val expandedPackageIds = remember { mutableStateListOf<Long>() }
    var packageNameInput by remember { mutableStateOf("") }
    var showSavePackageDialog by remember { mutableStateOf(false) }
    var deleteSavedCardId by remember { mutableStateOf<Long?>(null) }
    var deletePackageTicketKey by remember { mutableStateOf<String?>(null) }
    var deletePackageId by remember { mutableStateOf<Long?>(null) }
    val selectedChooseIds = remember { mutableStateListOf<Long>() }
    var chooseSearch by remember { mutableStateOf("") }

    fun allTickets(): List<Ticket> = cards.toList() + ticketPackages.flatMap { it.tickets }
    fun nextAvailableTicketId(): Long = nextTicketId(allTickets())
    fun findTicket(id: Long): Ticket? = cards.firstOrNull { it.id == id } ?: ticketPackages.firstNotNullOfOrNull { p -> p.tickets.firstOrNull { it.id == id } }
    fun replaceTicket(ticket: Ticket) {
        val ci = cards.indexOfFirst { it.id == ticket.id }
        if (ci >= 0) cards[ci] = ticket else {
            val pi = ticketPackages.indexOfFirst { p -> p.tickets.any { it.id == ticket.id } }
            if (pi >= 0) { val p = ticketPackages[pi]; ticketPackages[pi] = p.copy(tickets = p.tickets.map { if (it.id == ticket.id) ticket else it }) }
        }
    }
    fun saveAllTickets() { saveCards(ctx, cards); saveTicketPackages(ctx, ticketPackages) }
    fun persistActive() = saveActiveIds(ctx, activeIds.toList())
    fun snapshotMarks() { saveMarksSnapshot(ctx, allTickets()); canUndoMarks = true }
    fun undoMarks() {
        val snapshot = loadMarksSnapshot(ctx)
        if (snapshot != null) {
            cards.indices.forEach { i -> snapshot[cards[i].id]?.let { cards[i] = cards[i].copy(marked = it) } }
            ticketPackages.indices.forEach { pi -> val p = ticketPackages[pi]; ticketPackages[pi] = p.copy(tickets = p.tickets.map { t -> snapshot[t.id]?.let { t.copy(marked = it) } ?: t }) }
            saveAllTickets(); clearMarksSnapshot(ctx); canUndoMarks = false
            message = "Se han recuperado las marcas anteriores."
        }
    }
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
            // Generate a fresh, complete strip for this random game. This avoids
            // relying on saved ticket IDs/order, which can differ between devices.
            // A strip has six tickets and each number 1..90 appears exactly once.
            val randomStrip = generateSeries()
            randomStrip.shuffled().take(count.coerceIn(1, MAX_ACTIVE)).forEachIndexed { index, grid ->
                val ticket = Ticket(nextAvailableTicketId(), "Aleatorio ${index + 1}", grid)
                cards.add(ticket)
                activeIds.add(ticket.id)
            }
        }
        if (activeIds.isEmpty()) {
            val t = Ticket(nextTicketId(cards), "Cartón 1", generateGrid())
            cards.add(t); saveCards(ctx, cards); activeIds.add(t.id)
        }
        snapshotMarks()
        cards.indices.forEach { i -> if (cards[i].id in activeIds) cards[i] = cards[i].copy(marked = emptySet()) }
        ticketPackages.indices.forEach { pi -> val p = ticketPackages[pi]; ticketPackages[pi] = p.copy(tickets = p.tickets.map { if (it.id in activeIds) it.copy(marked = emptySet()) else it }) }
        saveAllTickets(); persistActive()
        page = "game"
        (ctx as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    val compactSix = page == "game" && landscape && activeIds.size > 4
    Box((if (page == "game" && activeIds.size > 3) Modifier.fillMaxSize().background(if (compactSix) Color(0xFF064AA8) else Color.White) else Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DarkRed, Red, Color(0xFFB70E18)))).padding(5.dp).border(2.dp, Yellow, RoundedCornerShape(13.dp)).padding(5.dp))) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(if (compactSix) 0.dp else 9.dp)).then(if (compactSix) Modifier.background(Color(0xFF064AA8)) else Modifier.background(Brush.verticalGradient(listOf(Cream, Yellow))))) {
            if (!(page == "game" && landscape && activeIds.size > 4)) Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFE51B24), Red))).padding(horizontal = 12.dp, vertical = if (page == "game") 3.dp else 10.dp),
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
                if (page == "game") Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canUndoMarks) Text("↶", Modifier.clickable { undoMarks() }.padding(horizontal = 5.dp), color = Yellow, style = MaterialTheme.typography.headlineMedium)
                    Text("↻", Modifier.clickable { options = true }.padding(4.dp), color = Yellow, style = MaterialTheme.typography.headlineMedium)
                }
            }

            when (page) {
                "home" -> HomePage(
                    onChoose = { page = "choose" },
                    onResume = { page = "game" },
                    showResume = activeIds.isNotEmpty(),
                    onRandom = { page = "random" },
                    onCreate = { nameInput = ""; groupNameInput = ""; numbersInput = ""; message = ""; editingId = null; editorGrid = List(3) { List(9) { 0 } }; page = "create" },
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
                "game" -> Column(
                    Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(31.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${activeIds.size} cartones",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium
                        )
                        TextButton(
                            onClick = { options = true },
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)
                        ) { Text("Opciones", maxLines = 1) }
                        TextButton(
                            onClick = {
                                packageNameInput = ""
                                showSavePackageDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)
                        ) { Text("Guardar", maxLines = 1) }
                    }
                    if (message.isNotBlank()) {
                        Text(message, color = Green, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val visibleCount = activeIds.size.coerceIn(1, MAX_ACTIVE)
                        val spacing = 2.dp
                        // Reserve no artificial bottom margin: distribute the entire available
                        // height between tickets, accounting for each card's blue header.
                        val ticketHeight = ((maxHeight - spacing * (visibleCount - 1)) / visibleCount)
                            .coerceAtLeast(1.dp)
                        val headerHeight = (ticketHeight.value * 0.16f).coerceIn(12f, 19f)
                        val numberFontSize = ((ticketHeight.value - headerHeight) / 3f * 0.64f)
                            .coerceIn(11f, 20f)
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            activeIds.take(MAX_ACTIVE).toList().forEachIndexed { index, id ->
                                val ticket = findTicket(id)
                                if (ticket != null) {
                                    TicketPanel(
                                        ticket,
                                        index + 1,
                                        compact = true,
                                        numberFontSize = numberFontSize,
                                        compactHeaderHeight = headerHeight,
                                        modifier = Modifier.fillMaxWidth(0.90f)
                                            .widthIn(max = 540.dp)
                                            .height(ticketHeight),
                                        onToggle = { n ->
                                            findTicket(id)?.let { old ->
                                                snapshotMarks()
                                                replaceTicket(old.copy(marked = if (n in old.marked) old.marked - n else old.marked + n))
                                                saveAllTickets()
                                            }
                                        },
                                        onClear = {
                                            findTicket(id)?.let { old ->
                                                snapshotMarks()
                                                replaceTicket(old.copy(marked = emptySet()))
                                                saveAllTickets()
                                            }
                                        },
                                        onRemove = {
                                            activeIds.remove(id)
                                            persistActive()
                                            (ctx as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                "create" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nombre del cartón:", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Mi cartón favorito") }, singleLine = true)
                    OutlinedTextField(value = groupNameInput, onValueChange = { groupNameInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Grupo o paquete (opcional)") }, placeholder = { Text("Ej. Familia, Amigos...") }, singleLine = true)
                    Text("Usa el mismo nombre para agrupar cartones. Déjalo vacío para guardarlo suelto.", style = MaterialTheme.typography.bodySmall)
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
                            val ticket = Ticket(editingId ?: nextAvailableTicketId(), nameInput.ifBlank { "Mi cartón ${allTickets().count { it.id !in (1L..1000L) } + 1}" }, editorGrid.map { it.toList() })
                            val group = groupNameInput.trim()
                            val oldPi = ticketPackages.indexOfFirst { p -> p.tickets.any { it.id == ticket.id } }
                            if (group.isBlank()) {
                                if (oldPi >= 0) { val p = ticketPackages[oldPi]; val remaining = p.tickets.filterNot { it.id == ticket.id }; if (remaining.isEmpty()) ticketPackages.removeAt(oldPi) else ticketPackages[oldPi] = p.copy(tickets = remaining) }
                                val ci = cards.indexOfFirst { it.id == ticket.id }; if (ci >= 0) cards[ci] = ticket else cards.add(ticket)
                            } else {
                                val target = ticketPackages.indexOfFirst { it.name.equals(group, true) }
                                if (oldPi >= 0 && target >= 0 && ticketPackages[oldPi].id == ticketPackages[target].id) {
                                    val p = ticketPackages[oldPi]; ticketPackages[oldPi] = p.copy(tickets = p.tickets.map { if (it.id == ticket.id) ticket else it })
                                } else {
                                    if (oldPi >= 0) { val p = ticketPackages[oldPi]; val rem = p.tickets.filterNot { it.id == ticket.id }; if (rem.isEmpty()) ticketPackages.removeAt(oldPi) else ticketPackages[oldPi] = p.copy(tickets = rem) }
                                    cards.removeAll { it.id == ticket.id }
                                    val dest = ticketPackages.indexOfFirst { it.name.equals(group, true) }
                                    if (dest >= 0) { val p = ticketPackages[dest]; ticketPackages[dest] = p.copy(tickets = p.tickets + ticket) }
                                    else ticketPackages.add(TicketPackage(System.currentTimeMillis(), group, listOf(ticket)))
                                }
                            }
                            saveAllTickets(); message = if (group.isBlank()) "¡Cartón guardado correctamente!" else "¡Cartón guardado en el grupo «$group»!"; editingId = ticket.id
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Guardar cartón") }
                    InfoPanel("Los cartones se guardan en este móvil para futuras partidas sin conexión.")
                }
                "saved" -> {
                    val savedCards = cards.filter { it.id !in (1L..1000L) }
                    val selectedCount = selectedSavedIds.size + selectedPackageTicketKeys.size
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Paquetes y cartones guardados", fontWeight = FontWeight.Bold)
                        Text("Seleccionados: $selectedCount/$MAX_ACTIVE. Puedes combinar cartones de paquetes distintos y cartones sueltos.", style = MaterialTheme.typography.bodySmall)
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (ticketPackages.isNotEmpty()) {
                                item(key = "package-heading") { Text("PAQUETES", fontWeight = FontWeight.ExtraBold, color = DarkRed) }
                                items(ticketPackages, key = { "pkg-${it.id}" }) { pkg ->
                                    val packageKeys = pkg.tickets.indices.map { "${pkg.id}:$it" }
                                    val allSelected = packageKeys.isNotEmpty() && packageKeys.all { it in selectedPackageTicketKeys }
                                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth().padding(6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = allSelected,
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            var remaining = (MAX_ACTIVE - selectedSavedIds.size - selectedPackageTicketKeys.size).coerceAtLeast(0)
                                                            packageKeys.forEach { key ->
                                                                if (key !in selectedPackageTicketKeys && remaining > 0) {
                                                                    selectedPackageTicketKeys.add(key)
                                                                    remaining--
                                                                }
                                                            }
                                                        } else {
                                                            selectedPackageTicketKeys.removeAll(packageKeys.toSet())
                                                        }
                                                    }
                                                )
                                                Column(Modifier.weight(1f)) {
                                                    Text(pkg.name, fontWeight = FontWeight.Bold)
                                                    Text("${pkg.tickets.size} cartones · selección directa", style = MaterialTheme.typography.bodySmall)
                                                }
                                                TextButton(onClick = {
                                                    if (pkg.id in expandedPackageIds) expandedPackageIds.remove(pkg.id) else expandedPackageIds.add(pkg.id)
                                                }) { Text(if (pkg.id in expandedPackageIds) "Ocultar" else "Desplegar") }
                                                TextButton(onClick = { deletePackageId = pkg.id }) {
                                                    Text("Eliminar", color = Red, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            if (pkg.id in expandedPackageIds) {
                                                pkg.tickets.forEachIndexed { index, ticket ->
                                                    val key = "${pkg.id}:$index"
                                                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = key in selectedPackageTicketKeys,
                                                            onCheckedChange = { checked ->
                                                                if (checked && key !in selectedPackageTicketKeys && selectedCount < MAX_ACTIVE) selectedPackageTicketKeys.add(key)
                                                                if (!checked) selectedPackageTicketKeys.remove(key)
                                                            },
                                                            enabled = key in selectedPackageTicketKeys || selectedCount < MAX_ACTIVE
                                                        )
                                                        Column(Modifier.weight(1f)) {
                                                            Text(ticket.name, fontWeight = FontWeight.Medium)
                                                            TicketPreviewGrid(ticket.grid)
                                                        }
                                                        TextButton(onClick = { nameInput = ticket.name; groupNameInput = pkg.name; numbersInput = ""; editorGrid = ticket.grid.map { it.toList() }; editingId = ticket.id; message = ""; page = "create" }) { Text("Editar") }
                                                        TextButton(onClick = { deletePackageTicketKey = key }) {
                                                            Text("Eliminar", color = Red, style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "singles-heading") { Text("CARTONES SUELTOS", fontWeight = FontWeight.ExtraBold, color = DarkRed) }
                            items(savedCards, key = { "single-${it.id}" }) { ticket ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = ticket.id in selectedSavedIds,
                                            onCheckedChange = { checked ->
                                                if (checked && selectedCount < MAX_ACTIVE) selectedSavedIds.add(ticket.id)
                                                if (!checked) selectedSavedIds.remove(ticket.id)
                                            },
                                            enabled = ticket.id in selectedSavedIds || selectedCount < MAX_ACTIVE
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(ticket.name, fontWeight = FontWeight.Bold)
                                            Text("Cartón guardado", style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = {
                                            nameInput = ticket.name
                                            groupNameInput = ""
                                            numbersInput = ""
                                            editorGrid = ticket.grid.map { it.toList() }
                                            editingId = ticket.id
                                            message = ""
                                            page = "create"
                                        }) { Text("Editar") }
                                        TextButton(onClick = { deleteSavedCardId = ticket.id }) {
                                            Text("Eliminar", color = Red, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        if (ticketPackages.isEmpty() && savedCards.isEmpty()) Text("Todavía no has guardado cartones ni paquetes.")
                        Button(
                            onClick = {
                                activeIds.clear()
                                selectedSavedIds.forEach { id ->
                                    if (activeIds.size < MAX_ACTIVE && id !in activeIds) activeIds.add(id)
                                }
                                selectedPackageTicketKeys.toList().forEach { key ->
                                    if (activeIds.size < MAX_ACTIVE) {
                                        val parts = key.split(":")
                                        val pkgId = parts.getOrNull(0)?.toLongOrNull()
                                        val ticketIndex = parts.getOrNull(1)?.toIntOrNull()
                                        val pkg = ticketPackages.firstOrNull { it.id == pkgId }
                                        val source = pkg?.tickets?.getOrNull(ticketIndex ?: -1)
                                        if (source != null && source.id !in activeIds) activeIds.add(source.id)
                                    }
                                }
                                snapshotMarks()
                                cards.indices.forEach { i -> if (cards[i].id in activeIds) cards[i] = cards[i].copy(marked = emptySet()) }
                                ticketPackages.indices.forEach { pi -> val p = ticketPackages[pi]; ticketPackages[pi] = p.copy(tickets = p.tickets.map { if (it.id in activeIds) it.copy(marked = emptySet()) else it }) }
                                saveAllTickets(); persistActive()
                                selectedSavedIds.clear()
                                selectedPackageTicketKeys.clear()
                                page = "game"
                                (ctx as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            },
                            enabled = selectedCount in 1..MAX_ACTIVE,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Jugar con $selectedCount cartones") }
                        BigButton("+", "Crear nuevo cartón", "", Blue) {
                            nameInput = ""
                            groupNameInput = ""
                            numbersInput = ""
                            editorGrid = List(3) { List(9) { 0 } }
                            editingId = null
                            message = ""
                            page = "create"
                        }
                    }
                }
                else -> HomePage(onChoose = { page = "choose" }, onResume = { page = "game" }, showResume = activeIds.isNotEmpty(), onRandom = { page = "random" }, onCreate = { page = "create" }, onSaved = { page = "saved" })
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
    if (deleteSavedCardId != null) AlertDialog(
        onDismissRequest = { deleteSavedCardId = null },
        title = { Text("Eliminar cartón guardado") },
        text = {
            val savedName = cards.firstOrNull { it.id == deleteSavedCardId }?.name ?: "este cartón"
            Text("¿Quieres eliminar «$savedName»? Esta acción no se puede deshacer.")
        },
        confirmButton = {
            TextButton(onClick = {
                val id = deleteSavedCardId
                if (id != null) {
                    cards.removeAll { it.id == id }
                    selectedSavedIds.remove(id)
                    activeIds.remove(id)
                    persistActive()
                    saveCards(ctx, cards)
                }
                deleteSavedCardId = null
            }) { Text("Eliminar", color = Red) }
        },
        dismissButton = { TextButton(onClick = { deleteSavedCardId = null }) { Text("Cancelar") } }
    )
    if (deletePackageTicketKey != null) {
        val key = deletePackageTicketKey
        val parts = key?.split(":")
        val packageId = parts?.getOrNull(0)?.toLongOrNull()
        val ticketIndex = parts?.getOrNull(1)?.toIntOrNull()
        val pkg = ticketPackages.firstOrNull { it.id == packageId }
        val ticketName = pkg?.tickets?.getOrNull(ticketIndex ?: -1)?.name ?: "este cartón"
        AlertDialog(
            onDismissRequest = { deletePackageTicketKey = null },
            title = { Text("Eliminar cartón del paquete") },
            text = { Text("¿Quieres eliminar «$ticketName» del paquete «${pkg?.name ?: ""}»? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    if (packageId != null && ticketIndex != null) {
                        val packagePosition = ticketPackages.indexOfFirst { it.id == packageId }
                        if (packagePosition >= 0) {
                            val currentPackage = ticketPackages[packagePosition]
                            if (ticketIndex in currentPackage.tickets.indices) {
                                val updatedTickets = currentPackage.tickets.toMutableList().apply { removeAt(ticketIndex) }
                                ticketPackages[packagePosition] = currentPackage.copy(tickets = updatedTickets)
                                // Clear this package's selected keys so shifted indices cannot select the wrong card.
                                selectedPackageTicketKeys.removeAll { it.startsWith("$packageId:") }
                                saveTicketPackages(ctx, ticketPackages)
                            }
                        }
                    }
                    deletePackageTicketKey = null
                }) { Text("Eliminar", color = Red) }
            },
            dismissButton = { TextButton(onClick = { deletePackageTicketKey = null }) { Text("Cancelar") } }
        )
    }
    if (deletePackageId != null) AlertDialog(
        onDismissRequest = { deletePackageId = null },
        title = { Text("Eliminar paquete") },
        text = {
            val packageName = ticketPackages.firstOrNull { it.id == deletePackageId }?.name ?: "este paquete"
            Text("¿Quieres eliminar el paquete «$packageName» completo y todos sus cartones guardados?")
        },
        confirmButton = {
            TextButton(onClick = {
                val id = deletePackageId
                if (id != null) {
                    ticketPackages.removeAll { it.id == id }
                    selectedPackageTicketKeys.removeAll { it.startsWith("$id:") }
                    expandedPackageIds.remove(id)
                    saveTicketPackages(ctx, ticketPackages)
                }
                deletePackageId = null
            }) { Text("Eliminar", color = Red) }
        },
        dismissButton = { TextButton(onClick = { deletePackageId = null }) { Text("Cancelar") } }
    )
    if (showSavePackageDialog) AlertDialog(
        onDismissRequest = { showSavePackageDialog = false },
        title = { Text("Guardar paquete") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se guardará un paquete para seleccionar estos cartones directamente más adelante, sin crear copias sueltas.")
                OutlinedTextField(
                    value = packageNameInput,
                    onValueChange = { packageNameInput = it },
                    label = { Text("Nombre del paquete") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var newPackageTicketId = nextAvailableTicketId()
                val selected = allTickets().filter { it.id in activeIds }.mapIndexed { index, ticket ->
                    ticket.copy(id = newPackageTicketId++, name = "Cartón ${index + 1}", marked = emptySet(), grid = ticket.grid.map { it.toList() })
                }
                if (selected.isNotEmpty() && packageNameInput.isNotBlank()) {
                    ticketPackages.add(TicketPackage(System.currentTimeMillis(), packageNameInput.trim(), selected))
                    saveTicketPackages(ctx, ticketPackages)
                    message = "Paquete «${packageNameInput.trim()}» guardado."
                }
                showSavePackageDialog = false
            }, enabled = packageNameInput.isNotBlank() && activeIds.isNotEmpty()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = { showSavePackageDialog = false }) { Text("Cancelar") } }
    )
    if (options) AlertDialog(
        onDismissRequest = { options = false },
        title = { Text("Opciones", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                onClick = {
                    showSavePackageDialog = true
                    packageNameInput = ""
                    options = false
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) { Text("📦 Guardar como paquete") }
            TextButton(
                onClick = {
                    showSavePackageDialog = true
                    packageNameInput = ""
                    options = false
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) { Text("📦 Guardar cartones como paquete") }
            HorizontalDivider()
            Text("¿Desea jugar con los mismos cartones?", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { confirmClearMarks = true }, modifier = Modifier.weight(1f)) { Text("Sí") }
                OutlinedButton(onClick = { options = false; page = "home" }, modifier = Modifier.weight(1f)) { Text("No") }
            }
            HorizontalDivider()
            TextButton(
                onClick = {
                    options = false
                    page = "home"
                    (ctx as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) { Text("✖ Volver al menú principal") }
        } },
        confirmButton = { TextButton(onClick = { options = false }) { Text("Cerrar") } }
    )
    if (confirmClearMarks) AlertDialog(
        onDismissRequest = { confirmClearMarks = false },
        title = { Text("¿Borrar las marcas?") },
        text = { Text("Se quitarán todos los números marcados de los cartones activos. Podrás recuperarlos con Deshacer.") },
        confirmButton = { TextButton(onClick = {
            snapshotMarks()
            cards.indices.forEach { i -> if (cards[i].id in activeIds) cards[i] = cards[i].copy(marked = emptySet()) }
            ticketPackages.indices.forEach { pi -> val p = ticketPackages[pi]; ticketPackages[pi] = p.copy(tickets = p.tickets.map { if (it.id in activeIds) it.copy(marked = emptySet()) else it }) }
            saveAllTickets()
            confirmClearMarks = false
            options = false
            message = "Marcas borradas. ¡Nueva partida!"
        }) { Text("Borrar marcas") } },
        dismissButton = { TextButton(onClick = { confirmClearMarks = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun HomePage(onChoose: () -> Unit, onResume: () -> Unit, showResume: Boolean, onRandom: () -> Unit, onCreate: () -> Unit, onSaved: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("👑", style = MaterialTheme.typography.displayLarge)
        Text("Mi Bingo", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Blue)
        Text("BINGO 90", style = MaterialTheme.typography.titleLarge, color = Red, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        if (showResume) BigButton("▶", "Continuar partida", "Recupera tus cartones y números marcados", Red, onResume)
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
                        Modifier.weight(1f).height(22.dp)
                            .background(Color.White)
                            .border(.35.dp, Color(0xFFB7D0F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (n > 0) Text(n.toString(), color = Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp), maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketPanel(ticket: Ticket, position: Int, compact: Boolean = false, numberFontSize: Float = 15f, compactHeaderHeight: Float = 21f, modifier: Modifier = Modifier, onToggle: (Int) -> Unit, onClear: () -> Unit, onRemove: () -> Unit) {
    Card(modifier.fillMaxWidth().border(1.dp, Blue, RoundedCornerShape(5.dp)), shape = RoundedCornerShape(5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxSize().padding(if (compact) 0.dp else 5.dp)) {
            if (compact) {
                Row(
                    Modifier.fillMaxWidth().height(compactHeaderHeight.dp).background(
                        Blue
                    ).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("B", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("I", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("N", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("G", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("O", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text("90", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
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
            Column(Modifier.fillMaxWidth().then(if (compact) Modifier.weight(1f) else Modifier).border(if (compact) .5.dp else 1.dp, Blue).padding(if (compact) 0.dp else 1.dp)) {
                for (r in 0..2) Row(Modifier.fillMaxWidth().then(if (compact) Modifier.weight(1f) else Modifier)) {
                    for (c in 0..8) {
                        val n = ticket.grid[r][c]
                        val marked = n > 0 && n in ticket.marked
                        Box(Modifier.weight(1f).fillMaxHeight().padding(if (compact) .25.dp else .5.dp).background(
                            when {
                                marked -> Green
                                n > 0 -> Color.White
                                else -> Color.White
                            }
                        ).border(if (compact) .35.dp else .45.dp, Color(0xFF91B9F8)).then(if (n > 0) Modifier.clickable { onToggle(n) } else Modifier), contentAlignment = Alignment.Center) {
                            if (n > 0) Text(
                                n.toString(),
                                color = if (marked) Color.White else Blue,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) numberFontSize.sp else 15.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
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
private fun hasMarksSnapshot(ctx: Context): Boolean =
    ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).contains("undo_marks")

private fun saveMarksSnapshot(ctx: Context, cards: List<Ticket>) {
    val root = JSONObject()
    cards.forEach { ticket -> root.put(ticket.id.toString(), JSONArray(ticket.marked.toList())) }
    ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).edit()
        .putString("undo_marks", root.toString()).apply()
}

private fun loadMarksSnapshot(ctx: Context): Map<Long, Set<Int>>? {
    val raw = ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).getString("undo_marks", null) ?: return null
    return try {
        val root = JSONObject(raw)
        val result = mutableMapOf<Long, Set<Int>>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = root.getJSONArray(key)
            result[key.toLong()] = (0 until arr.length()).map { arr.getInt(it) }.toSet()
        }
        result
    } catch (_: Exception) { null }
}

private fun clearMarksSnapshot(ctx: Context) {
    ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).edit().remove("undo_marks").apply()
}

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
private fun saveTicketPackages(ctx: Context, packages: List<TicketPackage>) {
    val outer = JSONArray()
    packages.forEach { pkg ->
        val obj = JSONObject().put("id", pkg.id).put("name", pkg.name)
        val arr = JSONArray()
        pkg.tickets.forEach { ticket ->
            val item = JSONObject().put("id", ticket.id).put("name", ticket.name)
            val rows = JSONArray()
            ticket.grid.forEach { rows.put(JSONArray(it)) }
            item.put("grid", rows).put("marked", JSONArray(ticket.marked.toList()))
            arr.put(item)
        }
        obj.put("tickets", arr)
        outer.put(obj)
    }
    ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).edit().putString("ticketPackages", outer.toString()).apply()
}
private fun loadTicketPackages(ctx: Context): List<TicketPackage> {
    val saved = ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).getString("ticketPackages", null) ?: return emptyList()
    return try {
        val outer = JSONArray(saved)
        (0 until outer.length()).map { pi ->
            val obj = outer.getJSONObject(pi)
            val arr = obj.getJSONArray("tickets")
            val tickets = (0 until arr.length()).map { ti ->
                val item = arr.getJSONObject(ti)
                val rows = item.getJSONArray("grid")
                val grid = (0 until rows.length()).map { r ->
                    val row = rows.getJSONArray(r)
                    (0 until row.length()).map { c -> row.getInt(c) }
                }
                val marks = item.optJSONArray("marked")
                Ticket(item.optLong("id", ti.toLong()), item.getString("name"), grid, if (marks == null) emptySet() else (0 until marks.length()).map { marks.getInt(it) }.toSet())
            }
            TicketPackage(obj.getLong("id"), obj.getString("name"), tickets)
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
