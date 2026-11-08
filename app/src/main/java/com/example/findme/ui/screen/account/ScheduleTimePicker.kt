package com.example.findme.ui.screen.account

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.findme.ui.theme.DarkCardBg
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.launch

private val hourLabels = (1..12).map(Int::toString)
private val minuteLabels = (0..59).map { it.toString().padStart(2, '0') }
private val periods = listOf("a. m.", "p. m.")
private val wheelRowHeight = 44.dp

@Composable
internal fun ScheduleTimePicker(title: String, initial: LocalTime, dismiss: () -> Unit, apply: (LocalTime) -> Unit) {
    val hours = rememberLazyListState((initial.hour + 11) % 12)
    val minutes = rememberLazyListState(initial.minute)
    val period = rememberLazyListState(initial.hour / 12)
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(horizontal = 12.dp).widthIn(max = 360.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = DarkCardBg
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("Desliza para elegir la hora", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(wheelRowHeight)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)))
                    Row(Modifier.fillMaxWidth()) {
                        TimeWheel(hourLabels, hours, Modifier.weight(1f))
                        TimeWheel(minuteLabels, minutes, Modifier.weight(1f))
                        TimeWheel(periods, period, Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Cancelar") }
                    Button(
                        onClick = {
                            val hour = (hours.centeredIndex() + 1) % 12 + period.centeredIndex() * 12
                            apply(LocalTime.of(hour, minutes.centeredIndex()))
                        },
                        enabled = !hours.isScrollInProgress && !minutes.isScrollInProgress && !period.isScrollInProgress
                    ) { Text("Aplicar hora") }
                }
            }
        }
    }
}

@Composable
private fun TimeWheel(labels: List<String>, state: LazyListState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val centered by remember(state) { derivedStateOf { state.centeredIndex() } }
    LazyColumn(
        modifier = modifier.height(wheelRowHeight * 5),
        state = state,
        contentPadding = PaddingValues(vertical = wheelRowHeight * 2),
        flingBehavior = rememberSnapFlingBehavior(state)
    ) {
        items(labels.size) { index ->
            val selected = index == centered
            Box(
                Modifier.fillMaxWidth().height(wheelRowHeight)
                    .selectable(selected, role = Role.RadioButton,
                        onClick = { scope.launch { state.animateScrollToItem(index) } }),
                contentAlignment = Alignment.Center
            ) {
                val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (abs(centered - index) == 1) .72f else .42f)
                Text(labels[index], color = color,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

private fun LazyListState.centeredIndex(): Int {
    val layout = layoutInfo
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
    return layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
        ?: firstVisibleItemIndex
}
