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
    var selected by remember { mutableIntStateOf(0) }
    var createDialog by remember { mutableStateOf(false) }
    var listDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val current = cards.getOrNull(selected)

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bingo 90", fontWeight = FontWeight.Bold)
                Text("Cartones españoles · sin sacar bolas", style = MaterialTheme.typography.labelSmall)
            }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { listDialog = true }, modifier = Modifier.weight(1f)) { Text("Cartones (${cards.size})") }
                Button(onClick = { createDialog = true; input = ""; error = "" }, modifier = Modifier.weight(1f)) { Text("Crear cartón") }
            }
            Spacer(Modifier.height(12.dp))
            if (current != null) {
                Text(current.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${current.marked.size} de 15 números marcados")
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
                    for (r in 0..2) Row(Modifier.fillMaxWidth()) {
                        for (c in 0..8) {
                            val n = current.grid[r][c]
                            val marked = n in current.marked && n > 0
                            Box(Modifier.weight(1f).padding(2.dp).aspectRatio(0.86f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (n == 0) MaterialTheme.colorScheme.surface else if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .then(if (n > 0) Modifier.clickable {
                                    cards[selected] = current.copy(marked = if (marked) current.marked - n else current.marked + n)
                                    saveCards(ctx, cards)
                                } else Modifier), contentAlignment = Alignment.Center) {
                                if (n > 0) Text(n.toString(), fontWeight = FontWeight.Bold,
                                    color = if (marked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { selected = if (selected > 0) selected - 1 else cards.lastIndex }, modifier = Modifier.weight(1f)) { Text("← Anterior") }
                    OutlinedButton(onClick = { selected = if (selected < cards.lastIndex) selected + 1 else 0 }, modifier = Modifier.weight(1f)) { Text("Siguiente →") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { cards[selected] = current.copy(marked = emptySet()); saveCards(ctx, cards) }, modifier = Modifier.weight(1f)) { Text("Quitar marcas") }
                    TextButton(onClick = {
                        cards.add(Ticket(System.currentTimeMillis(), "Cartón ${cards.size + 1}", generateGrid()))
                        selected = cards.lastIndex
                        saveCards(ctx, cards)
                    }, modifier = Modifier.weight(1f)) { Text("Nuevo aleatorio") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Toca los números para marcarlos o desmarcarlos. Los cambios se guardan en este móvil.",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }

    if (createDialog) AlertDialog(
        onDismissRequest = { createDialog = false },
        title = { Text("Crear tu propio cartón") },
        text = { Column {
            Text("Introduce exactamente 15 números distintos del 1 al 90, separados por comas.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Ej.: 2, 7, 14, 18...") }, minLines = 3)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            val nums = input.split(Regex("[,;\\s]+")).mapNotNull { it.toIntOrNull() }
            val grid = makeGrid(nums)
            if (grid == null) error = "No válido: 15 números únicos, del 1 al 90 y 1–3 por columna."
            else {
                cards.add(Ticket(System.currentTimeMillis(), "Mi cartón ${cards.size + 1}", grid))
                selected = cards.lastIndex
                saveCards(ctx, cards)
                createDialog = false
            }
        }) { Text("Crear y guardar") } },
        dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancelar") } }
    )

    if (listDialog) AlertDialog(
        onDismissRequest = { listDialog = false },
        title = { Text("Cartones guardados") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            cards.forEachIndexed { i, card ->
                Row(Modifier.fillMaxWidth().clickable { selected = i; listDialog = false }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(card.name, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal)
                        Text("${card.marked.size}/15 marcados", style = MaterialTheme.typography.bodySmall)
                    }
                    if (i == selected) Text("Actual")
                }
                HorizontalDivider()
            }
        } },
        confirmButton = { TextButton(onClick = { listDialog = false }) { Text("Cerrar") } }
    )
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
    val columns = (0..8).map { c -> nums.filter { (if (it == 90) 8 else (it - 1) / 10) == c }.sorted() }
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
                grid[r][c] = columns[c][i]; rowsUsed[r]++
            }
        }
        if (ok && rowsUsed.all { it == 5 }) return grid.map { it.toList() }
    }
    return null
}
