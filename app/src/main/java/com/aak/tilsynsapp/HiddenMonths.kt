package com.aak.tilsynsapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// "Skjul i bestemte måneder": the months a recurring Vejman permission is left off
// the weekly list. TilsynAPI does the leaving off and knows the rule (a parklet is
// left off from October to March); the app only shows and changes the months.

private val MONTH_NAMES = listOf(
    "januar", "februar", "marts", "april", "maj", "juni",
    "juli", "august", "september", "oktober", "november", "december"
)

/**
 * The months as Danish text. Three or more months in a row become a span, also
 * across the new year: [10, 11, 12, 1, 2, 3] is "oktober til marts", and
 * [1, 2, 5] is "januar, februar og maj". No months is "".
 */
fun describeMonths(months: Collection<Int>): String {
    val chosen = months.filter { it in 1..12 }.toSet()
    if (chosen.isEmpty()) return ""
    if (chosen.size == 12) return "hele året"

    // Start where a run starts, so a run across the new year stays in one piece.
    val start = (1..12).first { it in chosen && previousMonth(it) !in chosen }
    val runs = mutableListOf<List<Int>>()
    var run = mutableListOf<Int>()
    for (offset in 0 until 12) {
        val month = (start - 1 + offset) % 12 + 1
        if (month in chosen) {
            run.add(month)
        } else if (run.isNotEmpty()) {
            runs.add(run)
            run = mutableListOf()
        }
    }
    if (run.isNotEmpty()) runs.add(run)

    val parts = runs.flatMap { r ->
        if (r.size >= 3) listOf("${monthName(r.first())} til ${monthName(r.last())}")
        else r.map(::monthName)
    }
    return if (parts.size == 1) parts[0]
    else parts.dropLast(1).joinToString(", ") + " og " + parts.last()
}

/** What the app says once the months are saved. */
fun hiddenMonthsSavedText(months: Collection<Int>): String =
    if (months.isEmpty()) "Tilladelsen er med på tilsynslisten i alle måneder."
    else "Tilladelsen er ikke med på tilsynslisten i disse måneder: ${describeMonths(months)}."

private fun monthName(month: Int) = MONTH_NAMES[month - 1]

private fun previousMonth(month: Int) = if (month == 1) 12 else month - 1

private fun Set<Int>.toggled(month: Int) = if (month in this) this - month else this + month

/**
 * Tick the months the permission is left off the weekly list. It opens with the
 * months that apply now, and "Brug standard" ticks the rule's own months again.
 */
@Composable
fun HiddenMonthsDialog(
    item: TilsynItem,
    saving: Boolean,
    onSave: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val standard = item.defaultHiddenMonths.orEmpty().toSet()
    var chosen by remember(item.id) { mutableStateOf(item.effectiveHiddenMonths.orEmpty().toSet()) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Skjul i bestemte måneder") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Sæt flueben ved de måneder, hvor tilladelsen ikke skal med på den ugentlige tilsynsliste.")
                if (standard.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val parklet = if (item.isParklet == true) "Tilladelsen er en parklet. " else ""
                    Text(
                        "${parklet}Som standard er den ikke med på listen i disse måneder: ${describeMonths(standard)}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                (1..12).forEach { month ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !saving) { chosen = chosen.toggled(month) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = month in chosen,
                            onCheckedChange = { chosen = chosen.toggled(month) },
                            enabled = !saving
                        )
                        Text(monthName(month).replaceFirstChar { it.uppercase() })
                    }
                }
                if (standard.isNotEmpty() && chosen != standard) {
                    TextButton(onClick = { chosen = standard }, enabled = !saving) {
                        Text("Brug standard")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(chosen.sorted()) }, enabled = !saving) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Gem")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Annullér") }
        }
    )
}
