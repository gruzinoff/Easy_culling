package app.easyculling.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.easyculling.collage.CollageLayout
import app.easyculling.collage.PageFormat
import app.easyculling.collage.cellRects
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val GRID_PRESETS = listOf(2 to 2, 2 to 3, 3 to 3, 3 to 4, 4 to 4)
private val RATIO_PRESETS = listOf(1f to 1f, 4f to 5f, 3f to 4f, 2f to 3f)

/** Choose page format, resolution, grid, cell ratio, gap and margin, with a live preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageLayoutScreen(
    initial: CollageLayout,
    title: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: (CollageLayout) -> Unit,
) {
    var l by remember { mutableStateOf(initial) }
    var customRatio by remember {
        mutableStateOf(RATIO_PRESETS.none { it.first / it.second == initial.cellW / initial.cellH })
    }
    BackHandler(onBack = onCancel)

    fun withFormat(f: PageFormat, landscape: Boolean = l.landscape): CollageLayout {
        val (w, h) = if (f == PageFormat.CUSTOM) min(l.pageWMm, l.pageHMm) to max(l.pageWMm, l.pageHMm) else f.wMm to f.hMm
        return l.copy(format = f, pageWMm = if (landscape) h else w, pageHMm = if (landscape) w else h)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(title) },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = { onConfirm(l) },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                ) { Text(confirmLabel) }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            // live preview
            Box(Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                LayoutPreview(l, Modifier.aspectRatio(l.pageWMm / l.pageHMm))
            }

            Label("Format")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PageFormat.entries.forEach { f ->
                    FilterChip(
                        selected = l.format == f,
                        onClick = { l = withFormat(f) },
                        label = { Text(f.label, fontSize = 12.sp, maxLines = 1) },
                    )
                }
            }
            if (l.format == PageFormat.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Width mm", l.pageWMm, Modifier.weight(1f)) { l = l.copy(pageWMm = it.coerceIn(20f, 600f)) }
                    NumberField("Height mm", l.pageHMm, Modifier.weight(1f)) { l = l.copy(pageHMm = it.coerceIn(20f, 600f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Segments(listOf("Portrait", "Landscape"), if (l.landscape) 1 else 0) { i ->
                val wantLandscape = i == 1
                if (wantLandscape != l.landscape) l = l.copy(pageWMm = l.pageHMm, pageHMm = l.pageWMm)
            }

            Label("Resolution")
            Segments(listOf("300 dpi", "600 dpi"), if (l.dpi >= 600) 1 else 0) { l = l.copy(dpi = if (it == 1) 600 else 300) }
            Text("${l.pagePxW} × ${l.pagePxH} px", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

            Label("Grid (across × down)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GRID_PRESETS.forEach { (c, r) ->
                    FilterChip(
                        selected = l.cols == c && l.rows == r,
                        onClick = { l = l.copy(cols = c, rows = r) },
                        label = { Text("$c×$r") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper("Across", l.cols) { l = l.copy(cols = it) }
                Spacer(Modifier.width(24.dp))
                Stepper("Down", l.rows) { l = l.copy(rows = it) }
            }

            Label("Photo ratio (width : height)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RATIO_PRESETS.forEach { (w, h) ->
                    FilterChip(
                        selected = !customRatio && l.cellW / l.cellH == w / h,
                        onClick = { customRatio = false; l = l.copy(cellW = w, cellH = h) },
                        label = { Text("${w.roundToInt()}:${h.roundToInt()}") },
                    )
                }
                FilterChip(selected = customRatio, onClick = { customRatio = true }, label = { Text("Custom") })
            }
            if (customRatio) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Width", l.cellW, Modifier.weight(1f)) { l = l.copy(cellW = it.coerceIn(0.1f, 100f)) }
                    NumberField("Height", l.cellH, Modifier.weight(1f)) { l = l.copy(cellH = it.coerceIn(0.1f, 100f)) }
                }
            }

            Label("Gap between photos: ${"%.1f".format(l.gapMm)} mm")
            Slider(value = l.gapMm, onValueChange = { l = l.copy(gapMm = (it * 2).roundToInt() / 2f) }, valueRange = 0f..10f)
            Label("Outer margin: ${"%.1f".format(l.marginMm)} mm")
            Slider(value = l.marginMm, onValueChange = { l = l.copy(marginMm = (it * 2).roundToInt() / 2f) }, valueRange = 0f..15f)
            Text(
                "Selphy borderless prints trim a few mm at the edges; a margin of 3–5 mm keeps photos safe.",
                color = Color.Gray, fontSize = 13.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Page with its cells drawn as grey boxes on the background colour. */
@Composable
fun LayoutPreview(l: CollageLayout, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color(l.background.toInt()))
        cellRects(l, size.width, size.height).forEach { r ->
            drawRect(Color(0xFF8A8A90), topLeft = Offset(r.x, r.y), size = Size(r.w, r.h))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Accent, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                label = { Text(o) },
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.Gray)
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(1)) }) { Icon(Icons.Filled.Remove, "Less") }
        Text("$value", fontSize = 18.sp)
        IconButton(onClick = { onChange((value + 1).coerceAtMost(6)) }) { Icon(Icons.Filled.Add, "More") }
    }
}

@Composable
private fun NumberField(label: String, value: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    var text by remember { mutableStateOf(if (value == value.roundToInt().toFloat()) value.roundToInt().toString() else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; t.replace(',', '.').toFloatOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
