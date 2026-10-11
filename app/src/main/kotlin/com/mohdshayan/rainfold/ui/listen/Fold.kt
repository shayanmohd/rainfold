package com.mohdshayan.rainfold.ui.listen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mohdshayan.rainfold.R
import com.mohdshayan.rainfold.core.mix.LayerSpec
import com.mohdshayan.rainfold.core.spectrum.FoldCurve
import com.mohdshayan.rainfold.ui.EngineLabels
import com.mohdshayan.rainfold.ui.theme.RadiusLg
import kotlin.math.roundToInt

private const val POINTS = 48

/**
 * The Fold: one horizontal band per layer, stacked like folded sheets of rain. Each band's top edge
 * is that layer's spectrum (lows at left, from the same filter maths the engine plays), its height is
 * the volume. Drag a band up or down for volume, sideways for tone. Nothing moves unless touched.
 */
@Composable
fun Fold(
    layers: List<LayerSpec>,
    playable: List<Boolean>,
    onVolume: (key: Int, value: Float) -> Unit,
    onTone: (key: Int, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface)
    val context = LocalContext.current
    val names = layers.map { context.getString(EngineLabels.name(it.kind)) }
    val shapes = layers.map { layer -> remember(layer.kind, layer.tone) { FoldCurve.shape(layer.kind, layer.tone, POINTS) } }
    val latest by rememberUpdatedState(layers)
    val dragging = remember { mutableIntStateOf(-1) }

    Box(
        modifier
            .clip(RoundedCornerShape(RadiusLg))
            .background(colors.surfaceVariant),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val n = latest.size
                            if (n == 0) return@detectDragGestures
                            val top = 20.dp.toPx()
                            val slot = (size.height - top - 12.dp.toPx()) / n
                            dragging.intValue = ((start.y - top) / slot).toInt().coerceIn(0, n - 1)
                        },
                        onDragEnd = { dragging.intValue = -1 },
                        onDragCancel = { dragging.intValue = -1 },
                        onDrag = { change, drag ->
                            val i = dragging.intValue
                            val layer = latest.getOrNull(i) ?: return@detectDragGestures
                            change.consume()
                            val n = latest.size
                            val slot = (size.height - 32.dp.toPx()) / n
                            onVolume(layer.key, (layer.volume - drag.y / (slot * RISE)).coerceIn(0f, 1f))
                            if (kotlin.math.abs(drag.x) > 0.5f) {
                                onTone(layer.key, (layer.tone + drag.x / (size.width * 0.55f)).coerceIn(-1f, 1f))
                            }
                        },
                    )
                },
        ) {
            val n = layers.size
            if (n == 0) return@Canvas
            val left = 16.dp.toPx()
            val right = size.width - 16.dp.toPx()
            val top = 20.dp.toPx()
            val slot = (size.height - top - 12.dp.toPx()) / n
            val edgeWidth = 2.dp.toPx()
            // Drawn bottom band first, so the top band (strongest) lies over the rest.
            for (i in n - 1 downTo 0) {
                val layer = layers[i]
                val shape = shapes[i]
                val on = playable.getOrElse(i) { true }
                val bottom = top + (i + 1) * slot
                val rise = slot * RISE * (0.08f + 0.92f * layer.volume)
                val pts = List(POINTS) { j ->
                    val x = left + (right - left) * j / (POINTS - 1)
                    Offset(x, bottom - rise * (0.16f + 0.84f * shape[j]))
                }
                val edge = Path().apply { smoothInto(pts) }
                val fill = Path().apply {
                    smoothInto(pts)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
                val alpha = (0.40f - i * 0.055f).coerceAtLeast(0.14f)
                if (on) {
                    drawPath(fill, colors.primary.copy(alpha = alpha))
                    drawPath(edge, colors.primary, style = Stroke(width = if (dragging.intValue == i) edgeWidth * 1.6f else edgeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    drawPath(fill, colors.onSurfaceVariant.copy(alpha = 0.10f))
                    drawPath(
                        edge,
                        colors.onSurfaceVariant,
                        style = Stroke(width = edgeWidth, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
                    )
                }
                val label = measurer.measure(names.getOrElse(i) { "" }, labelStyle)
                drawText(label, topLeft = Offset(left + 4.dp.toPx(), bottom - label.size.height - 6.dp.toPx()))
            }
        }
        if (layers.isEmpty()) {
            androidx.compose.material3.Text(
                stringResource(R.string.layers_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center).padding(24.dp),
            )
        }
        // The accessible route: one node per band, its volume as an adjustable value.
        Column(Modifier.fillMaxSize().padding(top = 20.dp, bottom = 12.dp)) {
            layers.forEachIndexed { i, layer ->
                val name = names.getOrElse(i) { "" }
                val desc = if (playable.getOrElse(i) { true }) {
                    stringResource(R.string.fold_band, name, (layer.volume * 100).roundToInt(), stringResource(toneWord(layer.tone)))
                } else {
                    stringResource(R.string.fold_band_muted, name)
                }
                val darker = stringResource(R.string.tone_darker)
                val brighter = stringResource(R.string.tone_brighter)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .semantics {
                            contentDescription = desc
                            progressBarRangeInfo = ProgressBarRangeInfo(layer.volume, 0f..1f, steps = 19)
                            setProgress { v ->
                                onVolume(layer.key, v.coerceIn(0f, 1f))
                                true
                            }
                            customActions = listOf(
                                CustomAccessibilityAction(darker) { onTone(layer.key, (layer.tone - 0.25f).coerceIn(-1f, 1f)); true },
                                CustomAccessibilityAction(brighter) { onTone(layer.key, (layer.tone + 0.25f).coerceIn(-1f, 1f)); true },
                            )
                        },
                )
            }
        }
    }
}

/** Fold-shaped skeleton while the mix loads: two still, flat bands in the rule colour. */
@Composable
fun FoldSkeleton(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(RadiusLg))
            .background(colors.surfaceVariant),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val slot = (size.height - 32.dp.toPx()) / 2
            for (i in 0..1) {
                val bottom = 20.dp.toPx() + (i + 1) * slot
                drawRect(
                    colors.outlineVariant,
                    topLeft = Offset(16.dp.toPx(), bottom - slot * 0.6f),
                    size = androidx.compose.ui.geometry.Size(size.width - 32.dp.toPx(), slot * 0.6f),
                )
            }
        }
    }
}

fun toneWord(tone: Float): Int = when {
    tone < -0.15f -> R.string.tone_darker
    tone > 0.15f -> R.string.tone_brighter
    else -> R.string.tone_even
}

/** Height of a band at full volume, in slots: bands rise into the slot above, like overlapping folds. */
private const val RISE = 1.45f

private fun Path.smoothInto(points: List<Offset>) {
    moveTo(points.first().x, points.first().y)
    for (k in 1 until points.size) {
        val a = points[k - 1]
        val b = points[k]
        val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
        quadraticTo(a.x, a.y, mid.x, mid.y)
    }
    lineTo(points.last().x, points.last().y)
}
