package app.easyculling.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.easyculling.app
import app.easyculling.data.Photo
import coil3.request.ImageRequest
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.io.File

/**
 * Two photos side by side (stacked when the phone is upright), each with its own stars,
 * zoom and bin button.
 */
@Composable
fun CompareScreen(sid: String, a: Long, b: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repo
    val sessions by repo.sessions.collectAsState()
    val photos = sessions.firstOrNull { it.id == sid }?.photos?.associateBy { it.id } ?: emptyMap()
    val pa = photos[a]
    val pb = photos[b]
    BackHandler(onBack = onBack)
    if (pa == null || pb == null || pa.inBin || pb.inBin) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val sa = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f))
    val sb = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f))

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    ComparePane(sid, pa, sa, Modifier.weight(1f).fillMaxSize())
                    Box(Modifier.width(2.dp).fillMaxSize().background(Color.DarkGray))
                    ComparePane(sid, pb, sb, Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ComparePane(sid, pa, sa, Modifier.weight(1f).fillMaxWidth())
                    Box(Modifier.fillMaxWidth().padding(vertical = 1.dp).background(Color.DarkGray))
                    ComparePane(sid, pb, sb, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Compare", color = Color.White, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ComparePane(sid: String, p: Photo, zoom: ZoomableState, modifier: Modifier) {
    val context = LocalContext.current
    val repo = context.app.repo
    Box(modifier) {
        ZoomableAsyncImage(
            model = remember(p.path) { ImageRequest.Builder(context).data(File(p.path)).build() },
            contentDescription = p.name,
            state = rememberZoomableImageState(zoom),
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                IconButton(enabled = !p.isCulled, onClick = { repo.moveToBin(sid, listOf(p.id)) }) {
                    Icon(Icons.Outlined.Delete, "Move to bin", tint = if (p.isCulled) Color.Gray else Color.White)
                }
            }
            StarBar(
                rating = p.rating,
                onRate = { n -> repo.setRating(sid, listOf(p.id), if (p.rating == n) 0 else n) },
                starSize = 28.dp,
                touchSize = 44.dp,
            )
        }
    }
}
