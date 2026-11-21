package app.morningreport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val GUIDE_URL = "https://github.com/DenizTanisman/morning-report-app#ai-filtering-optional"

private val Dawn = Color(0xFFF08A4B)

private fun scheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Dawn, onPrimary = Color(0xFF241307), background = Color(0xFF0F1420), surface = Color(0xFF0F1420),
    surfaceVariant = Color(0xFF1A2130), onSurface = Color(0xFFE9ECF2), onSurfaceVariant = Color(0xFF9AA3B5),
    outline = Color(0xFF2C3546), secondaryContainer = Color(0xFF2A2018), onSecondaryContainer = Color(0xFFFFD9C2),
) else lightColorScheme(
    primary = Color(0xFFD9692B), onPrimary = Color.White, background = Color(0xFFF8F6F2), surface = Color(0xFFF8F6F2),
    surfaceVariant = Color(0xFFEFEBE4), onSurface = Color(0xFF1B1D22), onSurfaceVariant = Color(0xFF666B75),
    outline = Color(0xFFDCD6CC), secondaryContainer = Color(0xFFFCE3D3), onSecondaryContainer = Color(0xFF5A2A0E),
)

@Composable
fun App(model: AppModel, strings: Strings = Strings(systemLanguage() == "tr")) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme(dark)) {
        CompositionLocalProvider(LocalStrings provides strings) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Root(model)
            }
        }
    }
}

@Composable
private fun Root(model: AppModel) {
    val s = LocalStrings.current
    val data by model.repo.data.collectAsState()
    // Keep the selection valid when sections come and go.
    LaunchedEffect(data.sections.map { it.id }) {
        if (data.sections.none { it.id == model.selected }) model.selected = data.sections.firstOrNull()?.id
    }
    // The background runs under the system bars; the content stays clear of them.
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth > 760.dp
        Column(Modifier.fillMaxSize()) {
            TopBar(model, wide)
            if (data.sections.isEmpty()) EmptyState(model)
            else {
                SectionTabs(model, data)
                val section = data.sections.firstOrNull { it.id == model.selected } ?: data.sections.first()
                SectionPage(model, data, section, wide)
            }
        }
        Toast(model, Modifier.align(Alignment.BottomCenter))
    }
    model.sectionDialog?.let { SectionDialogView(model, it) }
    model.sourceDialog?.let { SourceDialogView(model, it) }
    model.sourceMenu?.let { id -> data.sections.flatMap { it.sources }.firstOrNull { it.id == id }?.let { SourceMenuView(model, it) } ?: run { model.sourceMenu = null } }
    if (model.showSettings) SettingsView(model, data.ai)
}

// ------------------------------------------------------------------ top

@Composable
private fun TopBar(model: AppModel, wide: Boolean) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(Icon.Sun, c.primary, 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(s.appName, fontSize = if (wide) 24.sp else 21.sp, fontWeight = FontWeight.Bold, color = c.onSurface, modifier = Modifier.weight(1f))
        if (model.refreshing) CircularProgressIndicator(Modifier.size(20.dp).padding(2.dp), strokeWidth = 2.dp, color = c.primary)
        else IconButton(Icon.Refresh, s.refresh) { model.refresh(s) }
        IconButton(Icon.Tune, s.settings) { model.showSettings = true }
    }
}

@Composable
private fun IconButton(icon: Icon, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, MaterialTheme.colorScheme.onSurfaceVariant, 22.dp) }
}

@Composable
private fun EmptyState(model: AppModel) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(c.primary.copy(alpha = 0.28f), c.primary.copy(alpha = 0.04f)))),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icon.Sun, c.primary, 52.dp) }
            Spacer(Modifier.height(22.dp))
            Text(s.emptyTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(s.emptyBody, fontSize = 15.sp, color = c.onSurfaceVariant, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { model.sectionDialog = SectionDialog() }) {
                LineIcon(Icon.Plus, c.onPrimary, 18.dp); Spacer(Modifier.width(8.dp)); Text(s.createSection)
            }
        }
    }
}

// ------------------------------------------------------------------ sections

@Composable
private fun SectionTabs(model: AppModel, data: AppData) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(data.sections, key = { it.id }) { sec ->
            val on = sec.id == model.selected
            val count = data.items.count { i -> sec.sources.any { it.id == i.sourceId } }
            Pill(on, onClick = { model.selected = sec.id }) {
                Text(sec.title, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) c.onPrimary else c.onSurface)
                if (count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("$count", fontSize = 12.sp, color = if (on) c.onPrimary.copy(alpha = 0.75f) else c.onSurfaceVariant)
                }
            }
        }
        item {
            Pill(false, dashed = true, onClick = { model.sectionDialog = SectionDialog() }) {
                LineIcon(Icon.Plus, c.primary, 16.dp); Spacer(Modifier.width(4.dp)); Text(s.newSection, color = c.primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Pill(on: Boolean, dashed: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape)
            .background(if (on) c.primary else c.surfaceVariant.copy(alpha = if (dashed) 0f else 1f))
            .then(if (dashed) Modifier.border(1.dp, c.primary.copy(alpha = 0.5f), CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionPage(model: AppModel, data: AppData, section: Section, wide: Boolean) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    val ids = section.sources.map { it.id }.toSet()
    val byId = section.sources.associateBy { it.id }
    val ai = data.ai
    fun shown(i: Item) = !ai.enabled || i.score == null || i.score >= ai.threshold
    val all = data.items.filter { it.sourceId in ids }
    val videos = all.filter { byId[it.sourceId]?.kind == SourceKind.YOUTUBE }
    val news = all.filter { byId[it.sourceId]?.kind == SourceKind.NEWS }
    var hiddenOpen by remember(section.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    PullToRefreshBox(isRefreshing = model.refreshing, onRefresh = { model.refresh(s) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "head") { Constrained { SectionHeader(model, data, section) } }
            item(key = "sources") { SourceStrip(model, section) }
            if (section.sources.isEmpty()) item(key = "nosrc") { Constrained { Hint(s.noSources) } }
            else if (all.isEmpty()) item(key = "noitems") { Constrained { Hint(s.noItems) } }

            val v = videos.filter(::shown)
            if (v.isNotEmpty()) {
                item(key = "vh") { Constrained { BlockTitle(s.videos, v.size) } }
                item(key = "vrow") { VideoRow(v, byId, wide) }
            }
            val n = news.filter(::shown)
            if (n.isNotEmpty()) {
                item(key = "nh") { Constrained { BlockTitle(s.articles, n.size) } }
                items(n, key = { "n" + it.id }) { Constrained { NewsRow(it, byId[it.sourceId]) } }
            }
            val hidden = all.filterNot(::shown)
            if (hidden.isNotEmpty()) {
                item(key = "hh") {
                    Constrained {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { hiddenOpen = !hiddenOpen }.padding(vertical = 14.dp, horizontal = 4.dp)) {
                            Text((if (hiddenOpen) "▾ " else "▸ ") + s.hiddenByAi(hidden.size), color = c.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }
                if (hiddenOpen) items(hidden, key = { "h" + it.id }) { Constrained { NewsRow(it, byId[it.sourceId], dim = true) } }
            }
        }
    }
}

/** Keeps reading width comfortable on a desktop window. */
@Composable
private fun Constrained(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 980.dp).fillMaxWidth().padding(horizontal = 20.dp)) { content() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, modifier = Modifier.padding(vertical = 28.dp))
}

@Composable
private fun SectionHeader(model: AppModel, data: AppData, section: Section) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(section.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box {
            IconButton(Icon.More, s.rename) { menu = true }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(s.rename) }, onClick = { menu = false; model.sectionDialog = SectionDialog(section.id, section.title) })
                if (data.sections.first().id != section.id) DropdownMenuItem(text = { Text(s.moveLeft) }, onClick = { menu = false; model.repo.moveSection(section.id, -1) })
                if (data.sections.last().id != section.id) DropdownMenuItem(text = { Text(s.moveRight) }, onClick = { menu = false; model.repo.moveSection(section.id, 1) })
                DropdownMenuItem(text = { Text(s.delete, color = Color(0xFFE0533D)) }, onClick = { menu = false; confirm = true })
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(section.title) },
        text = { Text(s.deleteSectionQ) },
        confirmButton = { TextButton(onClick = { confirm = false; model.repo.deleteSection(section.id) }) { Text(s.delete, color = Color(0xFFE0533D)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(s.cancel) } },
    )
}

@Composable
private fun SourceStrip(model: AppModel, section: Section) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyRow(
            Modifier.widthIn(max = 980.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(section.sources, key = { it.id }) { src ->
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(c.surfaceVariant)
                        .clickable { model.sourceMenu = src.id }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineIcon(if (src.kind == SourceKind.YOUTUBE) Icon.Play else Icon.Feed, if (src.kind == SourceKind.YOUTUBE) Color(0xFFE5483C) else c.primary, 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(src.name, fontSize = 14.sp, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                    src.time?.let {
                        Spacer(Modifier.width(8.dp))
                        LineIcon(Icon.Clock, c.onSurfaceVariant, 13.dp)
                        Spacer(Modifier.width(3.dp))
                        Text(it, fontSize = 12.sp, color = c.onSurfaceVariant)
                    }
                    if (src.lastError != null) { Spacer(Modifier.width(6.dp)); Text("!", color = Color(0xFFE0533D), fontWeight = FontWeight.Bold) }
                }
            }
            item {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, c.primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { model.sourceDialog = SourceDialog(section.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineIcon(Icon.Plus, c.primary, 14.dp); Spacer(Modifier.width(4.dp))
                    Text(s.addSource, fontSize = 14.sp, color = c.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun BlockTitle(text: String, count: Int) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text.uppercase(), fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("$count", fontSize = 12.sp, color = c.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun VideoRow(videos: List<Item>, byId: Map<String, Source>, wide: Boolean) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val now = nowMillis()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyRow(
            Modifier.widthIn(max = 980.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(videos, key = { it.id }) { v ->
                Column(Modifier.width(if (wide) 290.dp else 248.dp).clip(RoundedCornerShape(12.dp)).clickable { runCatching { uri.openUri(v.link) } }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
                        RemoteImage(v.thumbnail?.let(::betterThumb), c.surfaceVariant, Modifier.fillMaxSize())
                        Box(
                            Modifier.align(Alignment.Center).size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(Icon.Play, Color.White, 20.dp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(v.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("${byId[v.sourceId]?.name ?: ""} · ${s.ago(now - v.published)}", fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun NewsRow(item: Item, source: Source?, dim: Boolean = false) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val alpha = if (dim) 0.55f else 1f
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { runCatching { uri.openUri(item.link) } }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(source?.name ?: "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.primary.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("  ·  " + s.ago(nowMillis() - item.published), fontSize = 12.sp, color = c.onSurfaceVariant.copy(alpha = alpha))
                    item.score?.let { Text("  ·  AI $it", fontSize = 12.sp, color = c.onSurfaceVariant.copy(alpha = alpha)) }
                }
                Spacer(Modifier.height(4.dp))
                Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface.copy(alpha = alpha), lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.summary, fontSize = 14.sp, color = c.onSurfaceVariant.copy(alpha = alpha), lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            item.thumbnail?.let {
                Spacer(Modifier.width(14.dp))
                RemoteImage(it, c.surfaceVariant, Modifier.size(78.dp).clip(RoundedCornerShape(10.dp)))
            }
        }
        HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun SectionDialogView(model: AppModel, d: SectionDialog) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { model.sectionDialog = null },
        title = { Text(if (d.renameId == null) s.newSection else s.rename) },
        text = {
            Column {
                OutlinedTextField(
                    d.text, { d.text = it }, singleLine = true, label = { Text(s.sectionName) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { model.confirmSection(s) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (d.renameId == null) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(s.suggestions) { name ->
                            Text(name, fontSize = 13.sp, color = c.onSecondaryContainer, modifier = Modifier.clip(CircleShape)
                                .background(c.secondaryContainer).clickable { d.text = name }.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { model.confirmSection(s) }, enabled = d.text.isNotBlank()) { Text(if (d.renameId == null) s.create else s.save) } },
        dismissButton = { TextButton(onClick = { model.sectionDialog = null }) { Text(s.cancel) } },
    )
}

@Composable
private fun SourceDialogView(model: AppModel, d: SourceDialog) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { if (!d.busy) model.sourceDialog = null },
        title = { Text(s.addSource) },
        text = {
            Column(Modifier.widthIn(min = 300.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surfaceVariant).padding(3.dp)) {
                    listOf(SourceKind.NEWS to s.news, SourceKind.YOUTUBE to s.youtube).forEach { (k, label) ->
                        val on = d.kind == k
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) c.surface else Color.Transparent)
                                .clickable(enabled = !d.busy) { d.kind = k; d.error = null }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LineIcon(if (k == SourceKind.YOUTUBE) Icon.Play else Icon.Feed, if (k == SourceKind.YOUTUBE) Color(0xFFE5483C) else c.primary, 15.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(label, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = c.onSurface)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    d.input, { d.input = it; d.error = null }, singleLine = true, enabled = !d.busy,
                    placeholder = { Text(if (d.kind == SourceKind.NEWS) s.newsHint else s.youtubeHint, fontSize = 14.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { model.confirmSource(s) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(Icon.Clock, c.onSurfaceVariant, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(s.atTime, modifier = Modifier.weight(1f), fontSize = 15.sp)
                    Switch(d.timeOn, { d.timeOn = it; d.error = null }, enabled = !d.busy)
                }
                AnimatedVisibility(d.timeOn) {
                    Column {
                        OutlinedTextField(
                            d.time, { d.time = it.take(5); d.error = null }, singleLine = true, enabled = !d.busy,
                            placeholder = { Text(s.timeHint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(120.dp).padding(top = 8.dp),
                        )
                        Text(s.atTimeHelp, fontSize = 12.sp, color = c.onSurfaceVariant, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                d.error?.let { Text(it, color = Color(0xFFE0533D), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = { model.confirmSource(s) }, enabled = !d.busy && d.input.isNotBlank()) {
                if (d.busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.onPrimary)
                    Spacer(Modifier.width(8.dp)); Text(s.checking)
                } else Text(s.add)
            }
        },
        dismissButton = { TextButton(onClick = { model.sourceDialog = null }, enabled = !d.busy) { Text(s.cancel) } },
    )
}

@Composable
private fun SourceMenuView(model: AppModel, src: Source) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    var timeOn by remember(src.id) { mutableStateOf(src.time != null) }
    var time by remember(src.id) { mutableStateOf(src.time ?: "08:00") }
    var err by remember(src.id) { mutableStateOf<String?>(null) }
    fun saveAndClose() {
        val t = if (timeOn) Schedule.normalize(time) ?: run { err = s.error("bad_time"); return } else null
        if (t != src.time) model.repo.setSourceTime(src.id, t)
        model.sourceMenu = null
    }
    AlertDialog(
        onDismissRequest = { model.sourceMenu = null },
        title = { Text(src.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(src.siteUrl, fontSize = 13.sp, color = c.primary, modifier = Modifier.clickable { runCatching { uri.openUri(src.siteUrl) } })
                Spacer(Modifier.height(6.dp))
                val last = if (src.lastChecked == 0L) s.never else s.ago(nowMillis() - src.lastChecked)
                Text("${s.lastChecked}: $last", fontSize = 13.sp, color = c.onSurfaceVariant)
                src.lastError?.let { Text("${s.failed}: $it", fontSize = 13.sp, color = Color(0xFFE0533D)) }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.sourceTime, modifier = Modifier.weight(1f))
                    Switch(timeOn, { timeOn = it; err = null })
                }
                if (timeOn) OutlinedTextField(time, { time = it.take(5); err = null }, singleLine = true, modifier = Modifier.width(120.dp).padding(top = 6.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                else Text(s.noTime, fontSize = 13.sp, color = c.onSurfaceVariant)
                err?.let { Text(it, color = Color(0xFFE0533D), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { model.repo.removeSource(src.id); model.sourceMenu = null }) { Text(s.removeSource, color = Color(0xFFE0533D)) }
            }
        },
        confirmButton = { TextButton(onClick = ::saveAndClose) { Text(s.save) } },
        dismissButton = { TextButton(onClick = { model.sourceMenu = null }) { Text(s.cancel) } },
    )
}

@Composable
private fun SettingsView(model: AppModel, ai: AiSettings) {
    val s = LocalStrings.current
    val c = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(ai) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { model.repo.setAi(draft.copy(lastError = ai.lastError)); model.showSettings = false },
        title = { Text(s.settings) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).widthIn(min = 300.dp, max = 560.dp)) {
                Text(s.aiTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(s.aiBody, fontSize = 13.sp, color = c.onSurfaceVariant, lineHeight = 18.sp)
                Text(s.aiHowTo + " ↗", fontSize = 13.sp, color = c.primary, modifier = Modifier.padding(top = 6.dp).clickable { runCatching { uri.openUri(GUIDE_URL) } })
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.aiEnabled, modifier = Modifier.weight(1f))
                    Switch(draft.enabled, { draft = draft.copy(enabled = it) })
                }
                if (draft.enabled) {
                    OutlinedTextField(draft.interests, { draft = draft.copy(interests = it) }, label = { Text(s.aiInterests) },
                        placeholder = { Text(s.aiInterestsHint, fontSize = 13.sp) }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(s.aiThreshold(draft.threshold), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                    Slider(draft.threshold.toFloat(), { draft = draft.copy(threshold = it.toInt()) }, valueRange = 0f..10f, steps = 9)
                    OutlinedTextField(draft.endpoint, { draft = draft.copy(endpoint = it.trim()) }, label = { Text(s.aiEndpoint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.model, { draft = draft.copy(model = it.trim()) }, label = { Text(s.aiModel) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    OutlinedTextField(draft.apiKey, { draft = draft.copy(apiKey = it.trim()) }, label = { Text(s.aiKey) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(enabled = !testing, onClick = {
                        model.repo.setAi(draft)
                        testing = true; result = null
                        scope.launch {
                            val e = model.repo.rescoreAll()
                            result = e ?: s.aiOk
                            testing = false
                        }
                    }) {
                        if (testing) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                        Text(s.aiTest)
                    }
                    (result ?: ai.lastError)?.let { Text(it, fontSize = 13.sp, color = if (it == s.aiOk) c.primary else Color(0xFFE0533D), modifier = Modifier.padding(top = 8.dp)) }
                }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = c.outline)
                Spacer(Modifier.height(12.dp))
                Text(s.about, fontSize = 13.sp, color = c.onSurfaceVariant, lineHeight = 18.sp)
            }
        },
        confirmButton = { TextButton(onClick = { model.repo.setAi(draft.copy(lastError = ai.lastError)); model.showSettings = false }) { Text(s.close) } },
    )
}

@Composable
private fun Toast(model: AppModel, modifier: Modifier) {
    val text = model.toast
    LaunchedEffect(text) { if (text != null) { delay(2400); model.toast = null } }
    AnimatedVisibility(text != null, modifier.padding(bottom = 28.dp), enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Text(
            text ?: "", color = Color.White, fontSize = 14.sp,
            modifier = Modifier.clip(CircleShape).background(Color(0xE6202633)).padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
