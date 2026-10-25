package es.rujibe.bingo90

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private const val MAX_ACTIVE = 6
private data class Ticket(val id: Long, val name: String, val grid: List<List<Int>>, val marked: Set<Int> = emptySet())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { BingoScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BingoScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cards = remember { mutableStateListOf<Ticket>().apply { addAll(loadCards(ctx)) } }
    val activeIds = remember { mutableStateListOf<Long>().apply {
        addAll(loadActiveIds(ctx).filter { id -> cards.any { it.id == id } }.distinct().take(MAX_ACTIVE))
        if (isEmpty() && cards.isNotEmpty()) add(cards.first().id)
    } }
    var createDialog by remember { mutableStateOf(false) }
    var listDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showSavedOnly by remember { mutableStateOf(false) }

    fun persistActive() = saveActiveIds(ctx, activeIds.toList())
    fun addToGame(ticket: Ticket): Boolean {
        if (activeIds.contains(ticket.id)) return true
        if (activeIds.size >= MAX_ACTIVE) return false
        activeIds.add(ticket.id)
        persistActive()
        return true
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bingo 90", fontWeight = FontWeight.Bold)
                Text("Hasta 6 cartones · sin sacar bolas", style = MaterialTheme.typography.labelSmall)
            }
        })
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Cartones en la partida: ${activeIds.size}/$MAX_ACTIVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    if (activeIds.size < MAX_ACTIVE) {
                        val ticket = cards.filter { it.id !in activeIds }.randomOrNull()
                            ?: Ticket(System.currentTimeMillis(), "Aleatorio", generateGrid()).also { cards.add(it); saveCards(ctx, cards) }
                        addToGame(ticket)
                    } else {
                        error = "Ya tienes 6 cartones en la partida. Quita uno para añadir otro."
                    }
                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Text("Añadir aleatorio", textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = { listDialog = true; showSavedOnly = false }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Text("Elegir guardado", textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = { createDialog = true; input = ""; error = "" }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Text("Crear", textAlign = TextAlign.Center)
                }
            }
            if (error.isNotBlank() && !createDialog) {
                Spacer(Modifier.height(6.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            if (activeIds.isEmpty()) {
                Text("Añade un cartón aleatorio, elige uno guardado o crea el tuyo.", textAlign = TextAlign.Center)
            }
            activeIds.toList().forEachIndexed { index, id ->
                val cardIndex = cards.indexOfFirst { it.id == id }
                if (cardIndex >= 0) {
                    val ticket = cards[cardIndex]
                    TicketPanel(
                        ticket = ticket,
                        position = index + 1,
                        onToggle = { n ->
                            val now = cards.indexOfFirst { it.id == id }
                            if (now >= 0) {
                                val old = cards[now]
                                cards[now] = old.copy(marked = if (n in old.marked) old.marked - n else old.marked + n)
                                saveCards(ctx, cards)
                            }
                        },
                        onClear = {
                            val now = cards.indexOfFirst { it.id == id }
                            if (now >= 0) { cards[now] = cards[now].copy(marked = emptySet()); saveCards(ctx, cards) }
                        },
                        onRemove = { activeIds.remove(id); persistActive() }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            Text("Toca los números para marcarlos o desmarcarlos. Los cartones y las marcas se guardan en este móvil.",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
    }

    if (createDialog) AlertDialog(
        onDismissRequest = { createDialog = false },
        title = { Text("Crear tu propio cartón") },
        text = { Column {
            Text("Introduce exactamente 15 números distintos del 1 al 90, separados por comas. Se guardará y se añadirá a la partida si hay sitio.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = input, onValueChange = { input = it; error = "" }, label = { Text("Ej.: 2, 7, 14, 18...") }, minLines = 3)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            val parts = input.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
            val nums = parts.mapNotNull { it.toIntOrNull() }
            val grid = if (parts.size == 15 && nums.size == 15) makeGrid(nums) else null
            if (grid == null) error = "No válido: 15 números únicos, del 1 al 90 y 1–3 por columna."
            else {
                val ticket = Ticket(nextTicketId(cards), "Mi cartón ${cards.size + 1}", grid)
                cards.add(ticket)
                saveCards(ctx, cards)
                if (!addToGame(ticket)) error = "Cartón guardado, pero la partida ya tiene 6. Quita uno para añadirlo."
                else createDialog = false
            }
        }) { Text("Crear y guardar") } },
        dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancelar") } }
    )

    if (listDialog) AlertDialog(
        onDismissRequest = { listDialog = false },
        title = { Text("Elegir entre cartones guardados") },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            Text("En partida: ${activeIds.size}/$MAX_ACTIVE", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            cards.forEach { card ->
                val active = card.id in activeIds
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable {
                        if (active) { activeIds.remove(card.id); persistActive() }
                        else if (activeIds.size < MAX_ACTIVE) { activeIds.add(card.id); persistActive() }
                        else error = "La partida ya tiene 6 cartones. Quita uno primero."
                    }.padding(6.dp)) {
                        Text(card.name, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        Text("${card.marked.size}/15 marcados", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        if (active) { activeIds.remove(card.id); persistActive() }
                        else if (activeIds.size < MAX_ACTIVE) { activeIds.add(card.id); persistActive() }
                        else error = "La partida ya tiene 6 cartones. Quita uno primero."
                    }) { Text(if (active) "Quitar" else "Añadir") }
                }
                HorizontalDivider()
            }
        } },
        confirmButton = { TextButton(onClick = { listDialog = false }) { Text("Listo") } }
    )
}

@Composable
private fun TicketPanel(
    ticket: Ticket,
    position: Int,
    onToggle: (Int) -> Unit,
    onClear: () -> Unit,
    onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$position. ${ticket.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${ticket.marked.size}/15 marcados", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onRemove) { Text("Quitar") }
            }
            Spacer(Modifier.height(5.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)) {
                for (r in 0..2) Row(Modifier.fillMaxWidth()) {
                    for (c in 0..8) {
                        val n = ticket.grid[r][c]
                        val marked = n > 0 && n in ticket.marked
                        Box(
                            Modifier.weight(1f).padding(1.dp).aspectRatio(0.86f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (n == 0) MaterialTheme.colorScheme.surface else if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .then(if (n > 0) Modifier.clickable { onToggle(n) } else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (n > 0) Text(
                                n.toString(), fontWeight = FontWeight.Bold,
                                color = if (marked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClear) { Text("Quitar marcas") }
            }
        }
    }
}

private fun nextTicketId(cards: List<Ticket>): Long {
    val maxId = cards.maxOfOrNull { it.id } ?: 0L
    return maxOf(System.currentTimeMillis(), maxId + 1L)
}

private fun loadActiveIds(ctx: Context): List<Long> {
    val saved = ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).getString("activeIds", null) ?: return emptyList()
    return try {
        val arr = JSONArray(saved)
        (0 until arr.length()).map { arr.getLong(it) }
    } catch (_: Exception) { emptyList() }
}

private fun saveActiveIds(ctx: Context, ids: List<Long>) {
    ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).edit()
        .putString("activeIds", JSONArray(ids).toString()).apply()
}

private fun loadCards(ctx: Context): List<Ticket> {
    val saved = ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).getString("cards", null)
    if (saved != null) try {
        val arr = JSONArray(saved)
        val result = mutableListOf<Ticket>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val rows = o.getJSONArray("grid")
            val grid = (0 until rows.length()).map { r -> val row = rows.getJSONArray(r); (0 until row.length()).map { c -> row.getInt(c) } }
            val m = o.getJSONArray("marked")
            result.add(Ticket(o.getLong("id"), o.getString("name"), grid, (0 until m.length()).map { m.getInt(it) }.toSet()))
        }
        if (result.isNotEmpty()) return result
    } catch (_: Exception) { }
    val initial = mutableListOf<Ticket>()
    val seen = HashSet<String>()
    while (initial.size < 1000) {
        val g = generateGrid()
        val key = g.flatten().joinToString(",")
        if (seen.add(key)) initial.add(Ticket(initial.size.toLong() + 1, "Cartón ${initial.size + 1}", g))
    }
    saveCards(ctx, initial)
    return initial
}

private fun saveCards(ctx: Context, cards: List<Ticket>) {
    val arr = JSONArray()
    cards.forEach { card ->
        val o = JSONObject().put("id", card.id).put("name", card.name)
        val rows = JSONArray()
        card.grid.forEach { rows.put(JSONArray(it)) }
        o.put("grid", rows).put("marked", JSONArray(card.marked.toList()))
        arr.put(o)
    }
    ctx.getSharedPreferences("bingo90", Context.MODE_PRIVATE).edit().putString("cards", arr.toString()).apply()
}

private fun generateGrid(): List<List<Int>> {
    while (true) {
        val counts = MutableList(9) { Random.nextInt(1, 4) }
        while (counts.sum() > 15) { val c = counts.indices.filter { counts[it] > 1 }.random(); counts[c]-- }
        while (counts.sum() < 15) { val c = counts.indices.filter { counts[it] < 3 }.random(); counts[c]++ }
        val columns = (0..8).map { c ->
            val low = if (c == 0) 1 else c * 10
            val high = if (c == 8) 90 else c * 10 + 9
            (low..high).shuffled().take(counts[c]).sorted()
        }
        val grid = placeColumns(columns)
        if (grid != null) return grid
    }
}

private fun makeGrid(nums: List<Int>): List<List<Int>>? {
    if (nums.size != 15 || nums.distinct().size != 15 || nums.any { it !in 1..90 }) return null
    val columns = (0..8).map { c ->
        nums.filter { (if (it < 10) 0 else if (it >= 80) 8 else it / 10) == c }.sorted()
    }
    if (columns.any { it.isEmpty() || it.size > 3 }) return null
    return placeColumns(columns)
}

private fun placeColumns(columns: List<List<Int>>): List<List<Int>>? {
    repeat(1000) {
        val grid = Array(3) { IntArray(9) }
        val rowsUsed = IntArray(3)
        var ok = true
        for (c in 0..8) {
            val available = (0..2).filter { rowsUsed[it] < 5 }.shuffled()
            if (available.size < columns[c].size) { ok = false; break }
            available.take(columns[c].size).sorted().forEachIndexed { i, r ->
                grid[r][c] = columns[c][i]
                rowsUsed[r]++
            }
        }
        if (ok && rowsUsed.all { it == 5 }) return grid.map { it.toList() }
    }
    return null
}
