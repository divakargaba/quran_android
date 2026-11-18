package com.quran.mobile.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.library.R
import com.quran.mobile.feature.library.audio.PoemPlayer

/**
 * How a poem's recitation is repeated: each verse so many times, and a set of verses (from one
 * to another, the only ones recited then) so many times. [onApply] is given what is chosen, and
 * whether to recite at once.
 */
@Composable
fun RepeatOptionsDialog(
  verseCount: Int,
  settings: PoemPlayer.Settings,
  /** the set of verses offered when none is being repeated */
  defaultFirst: Int,
  defaultLast: Int,
  onApply: (PoemPlayer.Settings, Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  var verseRepeat by remember { mutableStateOf(settings.verseRepeat) }
  var range by remember { mutableStateOf(settings.hasRange) }
  var first by remember { mutableStateOf((settings.first ?: defaultFirst).toString()) }
  var last by remember { mutableStateOf((settings.last ?: defaultLast).toString()) }
  var rangeRepeat by remember { mutableStateOf(if (settings.hasRange) settings.rangeRepeat else PoemPlayer.INFINITE) }

  val from = first.toIntOrNull()?.takeIf { it in 1..verseCount }
  val to = last.toIntOrNull()?.takeIf { it in 1..verseCount }
  val valid = !range || (from != null && to != null)
  fun chosen(): PoemPlayer.Settings =
    if (range && from != null && to != null) {
      settings.copy(verseRepeat = verseRepeat, first = minOf(from, to), last = maxOf(from, to), rangeRepeat = rangeRepeat)
    } else {
      settings.copy(verseRepeat = verseRepeat).withoutRange()
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.library_repeat_options)) },
    text = {
      Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.library_play_each_verse), style = MaterialTheme.typography.titleSmall)
        Times(verseRepeat) { verseRepeat = it }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
          Text(
            stringResource(R.string.library_repeat_set),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
          )
          Switch(checked = range, onCheckedChange = { range = it })
        }
        if (range) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            VerseField(stringResource(R.string.library_from_verse), first, from == null, Modifier.weight(1f)) { first = it }
            VerseField(stringResource(R.string.library_to_verse), last, to == null, Modifier.weight(1f)) { last = it }
          }
          Text(
            stringResource(R.string.library_play_set),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp)
          )
          Times(rangeRepeat) { rangeRepeat = it }
        }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    confirmButton = {
      Row {
        TextButton(onClick = { onApply(chosen(), false) }, enabled = valid) {
          Text(stringResource(R.string.library_apply))
        }
        TextButton(onClick = { onApply(chosen(), true) }, enabled = valid) {
          Text(stringResource(R.string.library_apply_and_play))
        }
      }
    }
  )
}

/** the choice of how many times something is recited: once, twice... or without end */
@Composable
private fun Times(repeat: Int, onChoose: (Int) -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
    REPEATS.forEach { value ->
      FilterChip(
        selected = value == repeat,
        onClick = { onChoose(value) },
        label = { Text(timesLabel(value), maxLines = 1) }
      )
    }
  }
}

@Composable
private fun VerseField(label: String, value: String, wrong: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = { typed -> onChange(typed.filter(Char::isDigit).take(4)) },
    label = { Text(label, maxLines = 1) },
    isError = wrong,
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier
  )
}
