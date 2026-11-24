package app.morningreport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val GUIDE_URL = "https://github.com/DenizTanisman/morning-report-app#ai-filtering-optional"

private val EDGE_HEIGHT = 52.dp
private val TAB_BAR_HEIGHT = 66.dp
private val TAB_BAR_BOTTOM = 16.dp

@Composable
fun App(model: AppModel, strings: Strings = Strings(systemLanguage() == "tr")) {
    GlassTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
            GlassBackground { Root(model) }
        }
    }
}

@Composable
private fun Root(model: AppModel) {
    val data by model.repo.data.collectAsState()
    LaunchedEffect(data.sections.map { it.id }) {
        if (data.sections.none { it.id == model.selected }) model.selected = data.sections.firstOrNull()?.id
    }
    val playing = model.playing
    val playingSource = playing?.let { p -> data.sections.flatMap { it.sources }.firstOrNull { it.id == p.sourceId }?.name } ?: ""
    PlayerDialog(playing, playingSource, onClose = { model.playing = null }) {
        Box(Modifier.fillMaxSize()) {
            if (data.sections.isEmpty()) EmptyState(model) else SectionsPager(model, data)
            Toast(model, Modifier.align(Alignment.BottomCenter))
        }
    }
    model.sectionDialog?.let { SectionDialogView(model, it) }
    model.sourceDialog?.let { SourceDialogView(model, it) }
    model.sourceMenu?.let { id -> data.sections.flatMap { it.sources }.firstOrNull { it.id == id }?.let { SourceMenuView(model, it) } ?: run { model.sourceMenu = null } }
    if (model.showSettings) SettingsView(model, data.ai)
}

// ================================================================== navigation layer

@Composable
private fun SectionsPager(model: AppModel, data: AppData) {
    val s = LocalStrings.current
    val sections = data.sections
    val haze = rememberHazeState()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = sections.indexOfFirst { it.id == model.selected }.coerceAtLeast(0)) { sections.size }
    // The model's selection and the pager follow each other (taps on the tab bar, swipes, the demo).
    LaunchedEffect(model.selected, sections.size) {
        val i = sections.indexOfFirst { it.id == model.selected }
        if (i >= 0 && i != pager.currentPage) pager.animateScrollToPage(i)
    }
    // Only a real page change (a swipe, or the end of a jump) moves the selection. Restarting this when the list
    // changes would push the old page back into the selection right after a new section was selected.
    val sectionsNow = androidx.compose.runtime.rememberUpdatedState(sections)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { i -> sectionsNow.value.getOrNull(i)?.let { if (it.id != model.selected) model.selected = it.id } }
    }
    val lists = remember { mutableStateMapOf<String, LazyListState>() }
    val current = sections.getOrNull(pager.currentPage)?.let { lists[it.id] }
    // The top bar's glass comes in once anything scrolls under it.
    val collapsed by remember(current) { derivedStateOf { current != null && (current.firstVisibleItemIndex > 0 || current.firstVisibleItemScrollOffset > 0) } }

    // The tab bar slides away while reading down and comes back on any scroll up.
    val density = LocalDensity.current
    var barHidden by remember { mutableStateOf(false) }
    val barScroll = remember(density) {
        val threshold = with(density) { 10.dp.toPx() }
        object : NestedScrollConnection {
            var travel = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y != 0f) {
                    travel = if (travel * consumed.y < 0f) consumed.y else travel + consumed.y
                    if (travel < -threshold) barHidden = true else if (travel > threshold) barHidden = false
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(pager.currentPage) { barHidden = false }
    val barGone by animateFloatAsState(if (barHidden && current?.canScrollBackward == true) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 380f), label = "tabBar")
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val top = insets.calculateTopPadding()
    val bottom = insets.calculateBottomPadding()
    val barTravel = with(density) { (TAB_BAR_HEIGHT + TAB_BAR_BOTTOM + bottom + 24.dp).toPx() }

    // The pager can be measured with a new page count before this list is recomposed (a section was just added or
    // removed), so it reads the latest list through a state and tolerates an index that is not there yet.
    val latest = androidx.compose.runtime.rememberUpdatedState(sections)
    var widthPx by remember { mutableStateOf(0) }
    val wide = with(density) { widthPx.toDp() } > 760.dp
    Box(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width }) {
        // No pages composed off screen: when a dialog closed after a source was added, focus could land on the
        // neighbouring page that was kept ready, and the pager scrolled there by itself (the source list then
        // looked as if the source went into the wrong section).
        HorizontalPager(pager, Modifier.fillMaxSize().hazeSource(haze).nestedScroll(barScroll), beyondViewportPageCount = 0,
            key = { latest.value.getOrNull(it)?.id ?: "page$it" }) { i ->
            val sec = latest.value.getOrNull(i) ?: return@HorizontalPager
            val list = rememberLazyListState()
            androidx.compose.runtime.DisposableEffect(sec.id, list) { lists[sec.id] = list; onDispose { } }
            SectionPage(model, data, sec, list, PaddingValues(top = top + EDGE_HEIGHT, bottom = bottom + TAB_BAR_HEIGHT + TAB_BAR_BOTTOM + 28.dp), wide)
        }
        TopBar(model, haze, glass = collapsed, top = top)
        GlassTabBar(
            haze, sections, pager.currentPage,
            onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } },
            onAdd = { model.sectionDialog = SectionDialog() },
            addLabel = s.newShort,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom).offset { IntOffset(0, (barGone * barTravel).roundToInt()) },
        )
    }
}

/**
 * The same on every page: the day on the left, refresh and settings on the right. At rest it is fully transparent;
 * once anything scrolls under it, a thin glass bar (backdrop blur, light tint, hairline) fades in behind it.
 */
@Composable
private fun TopBar(model: AppModel, haze: HazeState?, glass: Boolean, top: androidx.compose.ui.unit.Dp) {
    val p = LocalGlass.current
    val s = LocalStrings.current
    Box(Modifier.fillMaxWidth().height(top + EDGE_HEIGHT)) {
        if (haze != null) AnimatedVisibility(glass, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().hazeEffect(haze, HazeStyle(p.base, HazeTint(p.glassTint), blurRadius = 28.dp, noiseFactor = 0.08f))) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(0.5.dp).background(p.glassEdgeBottom))
            }
        }
        Row(Modifier.padding(top = top).fillMaxWidth().height(EDGE_HEIGHT).padding(start = 20.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DuoIconView(Duo.Sun, 22.dp, line = p.accent, accent = p.accent, backAlpha = 0.3f)
            Spacer(Modifier.width(10.dp))
            Text(dayLabel(nowMillis(), s.tr), style = GlassType.headline, color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            BarIconButton(Duo.Reload, s.refresh, spinning = model.refreshing) { model.refresh(s) }
            BarIconButton(Duo.Settings, s.settings) { model.showSettings = true }
        }
    }
}

/** A text-free round button for the top bar. While [spinning] the icon turns and takes the accent colour. */
@Composable
private fun BarIconButton(icon: DuoIcon, label: String, spinning: Boolean = false, onClick: () -> Unit) {
    val p = LocalGlass.current
    val turn = if (spinning) rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "turn") else null
    Box(
        Modifier.size(42.dp).clip(CircleShape).semantics { contentDescription = label; role = Role.Button }
            .clickable(onClickLabel = label) { if (!spinning) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        DuoIconView(icon, 24.dp, line = if (spinning) p.accent else p.label, accent = p.accent, backAlpha = 0.22f,
            modifier = Modifier.graphicsLayer { rotationZ = turn?.value ?: 0f })
    }
}

/** Floating Liquid Glass capsule: backdrop blur, a light top edge, a lens that slides under the selected section. */
@Composable
private fun GlassTabBar(haze: HazeState, sections: List<Section>, selected: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit, addLabel: String, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val barWidth = minOf(maxWidth - 40.dp, 620.dp)
        val count = sections.size + 1
        val tabW = maxOf(76.dp, (barWidth - 10.dp) / count)
        Box(
            Modifier.padding(bottom = TAB_BAR_BOTTOM).width(barWidth).height(TAB_BAR_HEIGHT)
                .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(shape)
                .hazeEffect(haze, HazeStyle(p.base, HazeTint(p.glassTint), blurRadius = 28.dp, noiseFactor = 0.08f))
                .border(1.dp, Brush.verticalGradient(listOf(p.glassEdgeTop, p.glassEdgeBottom)), shape)
                .padding(5.dp),
        ) {
            val scroll = rememberScrollState()
            val x by animateDpAsState(tabW * selected, spring(dampingRatio = 0.78f, stiffness = 420f), label = "lens")
            // Keep the selected tab in view when there are more sections than fit.
            val density = LocalDensity.current
            LaunchedEffect(selected, tabW) {
                val target = with(density) { (tabW * selected).roundToPx() } - scroll.viewportSize / 3
                scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
            }
            Box(Modifier.fillMaxHeight().horizontalScroll(scroll)) {
                Box(Modifier.offset(x = x).width(tabW).fillMaxHeight().clip(shape).background(p.selection).border(0.5.dp, p.glassEdgeBottom, shape))
                Row(Modifier.fillMaxHeight()) {
                    sections.forEachIndexed { i, sec ->
                        val on = i == selected
                        TabCell(tabW, onClick = { onSelect(i) }) {
                            DuoIconView(Duo.forSection(sec.title), 24.dp, line = if (on) p.accent else p.label.copy(alpha = 0.78f), accent = p.accent, backAlpha = if (on) 0.38f else 0.14f)
                            Text(sec.title, style = GlassType.caption, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (on) p.accent else p.label.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
                        }
                    }
                    TabCell(tabW, onClick = onAdd) {
                        DuoIconView(Duo.Plus, 24.dp, line = p.label.copy(alpha = 0.78f), accent = p.accent, backAlpha = 0.14f)
                        Text(addLabel, style = GlassType.caption, fontWeight = FontWeight.Medium, color = p.label.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCell(width: androidx.compose.ui.unit.Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.width(width).fillMaxHeight().clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) { content() }
}

// ================================================================== empty start

@Composable
private fun EmptyState(model: AppModel) {
    val s = LocalStrings.current
    val p = LocalGlass.current
    val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 440.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(112.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.30f), p.accent.copy(alpha = 0.02f))))
                        .border(0.5.dp, p.hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { DuoIconView(Duo.Sun, 60.dp, line = p.accent, accent = p.accent, backAlpha = 0.35f) }
                Spacer(Modifier.height(26.dp))
                Text(s.emptyTitle, style = GlassType.largeTitle, color = p.label, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(s.emptyBody, style = GlassType.body, color = p.secondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(30.dp))
                // The light runs round the one thing to press.
                Box(Modifier.padding(CometRoom)) {
                    Box(Modifier.cometLight(true, p.accent, radius = 26.dp)) {
                        GlassButton(s.createSection, strong = true, icon = Duo.Plus, big = true) { model.sectionDialog = SectionDialog() }
                    }
                }
            }
        }
        TopBar(model, null, glass = false, top = top)
    }
}

// ================================================================== a section

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionPage(model: AppModel, data: AppData, section: Section, list: LazyListState, pad: PaddingValues, wide: Boolean) {
    val s = LocalStrings.current
    val p = LocalGlass.current
    val ids = section.sources.map { it.id }.toSet()
    val byId = section.sources.associateBy { it.id }
    val ai = data.ai
    fun shown(i: Item) = !ai.enabled || i.score == null || i.score >= ai.threshold
    val all = data.items.filter { it.sourceId in ids }
    val videos = all.filter { byId[it.sourceId]?.kind == SourceKind.YOUTUBE && shown(it) }
    val news = all.filter { byId[it.sourceId]?.kind == SourceKind.NEWS && shown(it) }
    val hidden = all.filterNot(::shown)
    var videosOpen by remember(section.id) { mutableStateOf(true) }
    var newsOpen by remember(section.id) { mutableStateOf(true) }
    var hiddenOpen by remember(section.id) { mutableStateOf(false) }
    val refresh = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = model.refreshing, onRefresh = { model.refresh(s) }, state = refresh, modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(refresh, model.refreshing, Modifier.align(Alignment.TopCenter).padding(top = pad.calculateTopPadding()),
                containerColor = p.card.compositeOver(p.base), color = p.accent)
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = pad, horizontalAlignment = Alignment.CenterHorizontally) {
            item(key = "head") { Constrained { SectionHeader(model, data, section) } }
            item(key = "sources") { SourceStrip(model, section) }
            if (section.sources.isEmpty()) item(key = "nosrc") { Constrained { Hint(s.noSources) } }
            else if (all.isEmpty()) item(key = "noitems") { Constrained { Hint(s.noItems) } }

            if (videos.isNotEmpty()) {
                item(key = "vh") { Constrained { AccordionHeader(Duo.Watch, s.videos, videos.size, videosOpen) { videosOpen = !videosOpen } } }
                item(key = "vrow") {
                    AnimatedVisibility(videosOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { VideoRow(model, videos, byId, wide) }
                }
            }
            if (news.isNotEmpty()) {
                item(key = "nh") { Constrained { AccordionHeader(Duo.Agenda, s.articles, news.size, newsOpen) { newsOpen = !newsOpen } } }
                if (newsOpen) items(news, key = { "n" + it.id }) { Constrained { NewsCard(it, byId[it.sourceId]) } }
            }
            if (hidden.isNotEmpty()) {
                item(key = "hh") { Constrained { FoldTile(hidden.size, s.hiddenByAiLabel, hiddenOpen) { hiddenOpen = !hiddenOpen } } }
                if (hiddenOpen) items(hidden, key = { "h" + it.id }) { Constrained { NewsCard(it, byId[it.sourceId], dim = true) } }
            }
        }
    }
}

/** Keeps reading width comfortable on a desktop window. */
@Composable
private fun Constrained(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 16.dp)) { content() }
    }
}

@Composable
private fun Hint(text: String) {
    val p = LocalGlass.current
    Text(text, style = GlassType.body, color = p.secondary, modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().contentCard().padding(18.dp))
}

@Composable
private fun SectionHeader(model: AppModel, data: AppData, section: Section) {
    val s = LocalStrings.current
    val p = LocalGlass.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        DuoIconView(Duo.forSection(section.title), 30.dp, line = p.label, accent = p.accent, backAlpha = 0.35f)
        Spacer(Modifier.width(12.dp))
        Text(section.title, style = GlassType.largeTitle, color = p.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box {
            Box(Modifier.size(40.dp).clip(CircleShape).background(p.card).border(0.5.dp, p.hairline, CircleShape)
                .semantics { contentDescription = s.sectionMenu; role = Role.Button }.clickable { menu = true }, contentAlignment = Alignment.Center) {
                DuoIconView(Duo.More, 22.dp, line = p.label, accent = p.accent, backAlpha = 0f)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(s.rename) }, onClick = { menu = false; model.sectionDialog = SectionDialog(section.id, section.title) })
                if (data.sections.first().id != section.id) DropdownMenuItem(text = { Text(s.moveLeft) }, onClick = { menu = false; model.repo.moveSection(section.id, -1) })
                if (data.sections.last().id != section.id) DropdownMenuItem(text = { Text(s.moveRight) }, onClick = { menu = false; model.repo.moveSection(section.id, 1) })
                DropdownMenuItem(text = { Text(s.delete, color = p.danger) }, onClick = { menu = false; confirm = true })
            }
        }
    }
    if (confirm) GlassDialog(
        title = section.title, onDismiss = { confirm = false },
        buttons = {
            GlassButton(s.cancel) { confirm = false }
            GlassButton(s.delete, strong = true, danger = true) { confirm = false; model.repo.deleteSection(section.id) }
        },
    ) { Text(s.deleteSectionQ, style = GlassType.body, color = p.label) }
}

@Composable
private fun SourceStrip(model: AppModel, section: Section) {
    val s = LocalStrings.current
    val p = LocalGlass.current
    val pill = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyRow(
            Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // First, so it never scrolls out of sight behind the sources.
            item(key = "add") {
                Row(
                    Modifier.clip(pill).background(p.accent.copy(alpha = 0.14f)).border(0.5.dp, p.accent.copy(alpha = 0.45f), pill)
                        .clickable { model.sourceDialog = SourceDialog(section.id) }.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DuoIconView(Duo.Plus, 16.dp, line = p.accent, accent = p.accent, backAlpha = 0.2f)
                    Spacer(Modifier.width(6.dp))
                    Text(s.addSource, style = GlassType.footnote, fontWeight = FontWeight.SemiBold, color = p.accent)
                }
            }
            items(section.sources, key = { it.id }) { src ->
                Row(Modifier.contentCard(pill).clickable { model.sourceMenu = src.id }.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (src.kind == SourceKind.YOUTUBE) DuoIconView(Duo.Play, 16.dp, line = p.youtube, accent = p.youtube, backAlpha = 0.5f)
                    else DuoIconView(Duo.Feed, 16.dp, line = p.accent, accent = p.accent, backAlpha = 0.25f)
                    Spacer(Modifier.width(7.dp))
                    Text(src.name, style = GlassType.footnote, fontWeight = FontWeight.SemiBold, color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 190.dp))
                    src.time?.let {
                        Spacer(Modifier.width(8.dp))
                        DuoIconView(Duo.Clock, 14.dp, line = p.secondary, accent = p.accent, backAlpha = 0.2f)
                        Spacer(Modifier.width(3.dp))
                        Text(it, style = GlassType.caption, color = p.secondary)
                    }
                    if (src.lastError != null) { Spacer(Modifier.width(6.dp)); Text("!", color = p.danger, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** One collapsible block of a section: icon, title, count and the sunrise mark. */
@Composable
private fun AccordionHeader(icon: DuoIcon, title: String, count: Int, open: Boolean, onClick: () -> Unit) {
    val p = LocalGlass.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp).clip(shape).background(if (open) p.selection else p.card)
            .border(0.5.dp, p.hairline, shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DuoIconView(icon, 22.dp, line = if (open) p.accent else p.label, accent = p.accent, backAlpha = if (open) 0.38f else 0.18f)
        Spacer(Modifier.width(12.dp))
        Text(title, style = GlassType.headline, color = if (open) p.accent else p.label, modifier = Modifier.weight(1f))
        Text("$count", style = GlassType.footnote, fontWeight = FontWeight.SemiBold, color = p.secondary)
        Spacer(Modifier.width(10.dp))
        SunriseToggle(open, line = p.secondary, accent = p.accent)
    }
}

/** The count, what it counts, and the open/close mark. */
@Composable
private fun FoldTile(count: Int, label: String, open: Boolean, onClick: () -> Unit) {
    val p = LocalGlass.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape).background(if (open) p.selection else p.card).border(0.5.dp, p.hairline, shape)
            .clickable(onClick = onClick).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$count", style = GlassType.headline.copy(fontSize = 20.sp, lineHeight = 24.sp), color = if (open) p.accent else p.label)
            Text(label, style = GlassType.footnote, fontWeight = FontWeight.Medium, color = p.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SunriseToggle(open, line = p.secondary, accent = p.accent)
    }
}

private fun openItem(model: AppModel, item: Item, open: (String) -> Unit) {
    if (inAppVideo && youtubeId(item.link) != null) model.playing = item else runCatching { open(item.link) }
}

@Composable
private fun VideoRow(model: AppModel, videos: List<Item>, byId: Map<String, Source>, wide: Boolean) {
    val s = LocalStrings.current
    val p = LocalGlass.current
    val uri = LocalUriHandler.current
    val now = nowMillis()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyRow(
            Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(videos, key = { it.id }) { v ->
                Column(Modifier.width(if (wide) 290.dp else 252.dp).contentCard().clickable { openItem(model, v, uri::openUri) }.padding(10.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))) {
                        RemoteImage(v.thumbnail?.let(::betterThumb), p.selection, Modifier.fillMaxSize())
                        Box(
                            Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { DuoIconView(Duo.Play, 20.dp, line = Color.White, accent = Color.White, backAlpha = 0.9f) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(v.title, style = GlassType.headline, color = p.label, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                    Text("${byId[v.sourceId]?.name ?: ""} · ${s.ago(now - v.published)}", style = GlassType.caption, color = p.secondary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun NewsCard(item: Item, source: Source?, dim: Boolean = false) {
    val s = LocalStrings.current
    val p = LocalGlass.current
    val uri = LocalUriHandler.current
    val a = if (dim) 0.55f else 1f
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).contentCard().clickable { runCatching { uri.openUri(item.link) } }.padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = GlassType.headline, color = p.label.copy(alpha = a), maxLines = 3, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(source?.name, s.ago(nowMillis() - item.published), item.score?.let { "AI $it" }).joinToString(" · ")
            Text(meta, style = GlassType.caption, color = p.secondary.copy(alpha = p.secondary.alpha * a), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            if (item.summary.isNotBlank()) Text(item.summary, style = GlassType.body, color = p.label.copy(alpha = 0.86f * a), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
        item.thumbnail?.let {
            Spacer(Modifier.width(12.dp))
            RemoteImage(it, p.selection, Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)))
        }
    }
}

// ================================================================== dialogs

/** A glass-styled dialog card. [lit] runs the comet light round it (while something is being looked up). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassDialog(title: String, onDismiss: () -> Unit, lit: Boolean = false, buttons: @Composable RowScope.() -> Unit, content: @Composable () -> Unit) {
    val p = LocalGlass.current
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(Modifier.padding(CometRoom)) {
            Column(
                Modifier.cometLight(lit, p.accent, radius = 28.dp).clip(RoundedCornerShape(28.dp))
                    .background(p.card.compositeOver(p.base)).border(0.5.dp, p.hairline, RoundedCornerShape(28.dp))
                    .padding(22.dp),
            ) {
                Text(title, style = GlassType.title2, color = p.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                content()
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = buttons)
            }
        }
    }
}

/** Capsule button: [strong] = accent fill, otherwise the selection tint. */
@Composable
private fun GlassButton(text: String, strong: Boolean = false, enabled: Boolean = true, danger: Boolean = false, icon: DuoIcon? = null, big: Boolean = false, busy: Boolean = false, onClick: () -> Unit) {
    val p = LocalGlass.current
    val shape = RoundedCornerShape(50)
    val fill = when { !strong -> p.selection; danger -> p.danger; else -> p.accent }
    val ink = if (strong) (if (p.dark && !danger) Color(0xFF06233A) else Color.White) else p.label
    Row(
        Modifier.clip(shape).background(fill.copy(alpha = if (enabled) fill.alpha else fill.alpha * 0.45f)).border(0.5.dp, p.hairline, shape)
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(horizontal = if (big) 24.dp else 18.dp, vertical = if (big) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ink); Spacer(Modifier.width(8.dp)) }
        else icon?.let { DuoIconView(it, 18.dp, line = ink, accent = ink, backAlpha = 0.25f); Spacer(Modifier.width(8.dp)) }
        Text(text, style = if (big) GlassType.headline else GlassType.footnote.copy(fontWeight = FontWeight.SemiBold), color = ink.copy(alpha = if (enabled) 1f else 0.6f))
    }
}

@Composable
private fun Segmented(options: List<Pair<SourceKind, String>>, selected: SourceKind, enabled: Boolean, onPick: (SourceKind) -> Unit) {
    val p = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Row(Modifier.fillMaxWidth().clip(shape).background(p.selection).border(0.5.dp, p.hairline, shape).padding(3.dp)) {
        options.forEach { (k, label) ->
            val on = k == selected
            Row(
                Modifier.weight(1f).clip(shape).background(if (on) p.card.compositeOver(p.base) else Color.Transparent)
                    .clickable(enabled = enabled) { onPick(k) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (k == SourceKind.YOUTUBE) DuoIconView(Duo.Play, 16.dp, line = p.youtube, accent = p.youtube, backAlpha = 0.5f)
                else DuoIconView(Duo.Feed, 16.dp, line = p.accent, accent = p.accent, backAlpha = 0.25f)
                Spacer(Modifier.width(6.dp))
                Text(label, style = GlassType.footnote, fontWeight = FontWeight.SemiBold, color = if (on) p.label else p.secondary)
            }
        }
    }
}

@Composable
private fun SectionDialogView(model: AppModel, d: SectionDialog) {
    val s = LocalStrings.current
    val p = LocalGlass.current
    GlassDialog(
        title = if (d.renameId == null) s.newSection else s.rename, onDismiss = { model.sectionDialog = null },
        buttons = {
            GlassButton(s.cancel) { model.sectionDialog = null }
            GlassButton(if (d.renameId == null) s.create else s.save, strong = true, enabled = d.text.isNotBlank()) { model.confirmSection(s) }
        },
    ) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { if (!Demo.running) { delay(150); runCatching { focus.requestFocus() } } }
        // Renaming opens with the old name selected, so typing replaces it.
        var field by remember { mutableStateOf(TextFieldValue(d.text, TextRange(0, d.text.length))) }
        // Text set from outside the field (the demo recording) shows up in it, with the cursor at the end.
        LaunchedEffect(d.text) { if (field.text != d.text) field = TextFieldValue(d.text, TextRange(d.text.length)) }
        OutlinedTextField(
            field, { field = it; d.text = it.text }, singleLine = true, label = { Text(s.sectionName) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { model.confirmSection(s) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (d.renameId == null) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.suggestions) { name ->
                    Row(Modifier.contentCard(RoundedCornerShape(50)).clickable { d.text = name; field = TextFieldValue(name, TextRange(name.length)) }.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        DuoIconView(Duo.forSection(name), 16.dp, line = p.label, accent = p.accent, backAlpha = 0.3f)
                        Spacer(Modifier.width(5.dp))
                        Text(name, style = GlassType.footnote, color = p.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceDialogView(model: AppModel, d: SourceDialog) {
    val s = LocalStrings.current
    val p = LocalGlass.current
    GlassDialog(
        title = s.addSource, onDismiss = { if (!d.busy) model.sourceDialog = null }, lit = d.busy,
        buttons = {
            GlassButton(s.cancel, enabled = !d.busy) { model.sourceDialog = null }
            GlassButton(if (d.busy) s.checking else s.add, strong = true, enabled = d.input.isNotBlank(), busy = d.busy) { model.confirmSource(s) }
        },
    ) {
        Column(Modifier.widthIn(min = 280.dp)) {
            Segmented(listOf(SourceKind.NEWS to s.news, SourceKind.YOUTUBE to s.youtube), d.kind, !d.busy) { d.kind = it; d.error = null }
            Spacer(Modifier.height(14.dp))
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { if (!Demo.running) { delay(150); runCatching { focus.requestFocus() } } }
            OutlinedTextField(
                d.input, { d.input = it; d.error = null }, singleLine = true, enabled = !d.busy,
                placeholder = { Text(if (d.kind == SourceKind.NEWS) s.newsHint else s.youtubeHint, style = GlassType.footnote) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { model.confirmSource(s) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(14.dp))
            // The whole row toggles, not only the switch.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(d.timeOn, enabled = !d.busy, role = Role.Switch) { d.timeOn = it; d.error = null },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DuoIconView(Duo.Clock, 20.dp, line = p.label, accent = p.accent, backAlpha = 0.25f)
                Spacer(Modifier.width(10.dp))
                Text(s.atTime, style = GlassType.body, color = p.label, modifier = Modifier.weight(1f))
                Switch(d.timeOn, null, enabled = !d.busy)
            }
            AnimatedVisibility(d.timeOn) {
                Column {
                    OutlinedTextField(d.time, { d.time = it.take(5); d.error = null }, singleLine = true, enabled = !d.busy,
                        placeholder = { Text(s.timeHint) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp).padding(top = 8.dp))
                    Text(s.atTimeHelp, style = GlassType.caption, color = p.secondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
            d.error?.let { Text(it, style = GlassType.footnote, color = p.danger, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

@Composable
private fun SourceMenuView(model: AppModel, src: Source) {
    val s = LocalStrings.current
    val p = LocalGlass.current
    val uri = LocalUriHandler.current
    var timeOn by remember(src.id) { mutableStateOf(src.time != null) }
    var time by remember(src.id) { mutableStateOf(src.time ?: "08:00") }
    var err by remember(src.id) { mutableStateOf<String?>(null) }
    fun saveAndClose() {
        val t = if (timeOn) Schedule.normalize(time) ?: run { err = s.error("bad_time"); return } else null
        if (t != src.time) model.repo.setSourceTime(src.id, t)
        model.sourceMenu = null
    }
    GlassDialog(
        title = src.name, onDismiss = { model.sourceMenu = null },
        buttons = {
            GlassButton(s.removeSource, danger = true) { model.repo.removeSource(src.id); model.sourceMenu = null }
            Spacer(Modifier.weight(1f))
            GlassButton(s.save, strong = true) { saveAndClose() }
        },
    ) {
        Text(src.siteUrl + " ↗", style = GlassType.footnote, color = p.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { runCatching { uri.openUri(src.siteUrl) } })
        val last = if (src.lastChecked == 0L) s.never else s.ago(nowMillis() - src.lastChecked)
        Text("${s.lastChecked}: $last", style = GlassType.footnote, color = p.secondary, modifier = Modifier.padding(top = 4.dp))
        src.lastError?.let { Text("${s.failed}: $it", style = GlassType.footnote, color = p.danger) }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(timeOn, role = Role.Switch) { timeOn = it; err = null },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuoIconView(Duo.Clock, 20.dp, line = p.label, accent = p.accent, backAlpha = 0.25f)
            Spacer(Modifier.width(10.dp))
            Text(s.sourceTime, style = GlassType.body, color = p.label, modifier = Modifier.weight(1f))
            Switch(timeOn, null)
        }
        if (timeOn) OutlinedTextField(time, { time = it.take(5); err = null }, singleLine = true, modifier = Modifier.width(120.dp).padding(top = 6.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        else Text(s.noTime, style = GlassType.footnote, color = p.secondary, modifier = Modifier.padding(start = 30.dp))
        err?.let { Text(it, style = GlassType.footnote, color = p.danger, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun SettingsView(model: AppModel, ai: AiSettings) {
    val s = LocalStrings.current
    val p = LocalGlass.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(ai) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val close = { model.repo.setAi(draft.copy(lastError = ai.lastError)); model.showSettings = false }
    GlassDialog(title = s.settings, onDismiss = close, lit = testing, buttons = { GlassButton(s.close, strong = true) { close() } }) {
        Column(Modifier.verticalScroll(rememberScrollState()).widthIn(min = 280.dp, max = 560.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DuoIconView(Duo.Sparkle, 22.dp, line = p.accent, accent = p.accent, backAlpha = 0.35f)
                Spacer(Modifier.width(10.dp))
                Text(s.aiTitle, style = GlassType.headline, color = p.label)
            }
            Text(s.aiBody, style = GlassType.footnote, color = p.secondary, modifier = Modifier.padding(top = 6.dp))
            Text(s.aiHowTo + " ↗", style = GlassType.footnote, fontWeight = FontWeight.SemiBold, color = p.accent,
                modifier = Modifier.padding(top = 8.dp).clickable { runCatching { uri.openUri(GUIDE_URL) } })
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(draft.enabled, role = Role.Switch) { draft = draft.copy(enabled = it) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.aiEnabled, style = GlassType.body, color = p.label, modifier = Modifier.weight(1f))
                Switch(draft.enabled, null)
            }
            AnimatedVisibility(draft.enabled) {
                Column {
                    OutlinedTextField(draft.interests, { draft = draft.copy(interests = it) }, label = { Text(s.aiInterests) },
                        placeholder = { Text(s.aiInterestsHint, style = GlassType.footnote) }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(s.aiThreshold(draft.threshold), style = GlassType.body, color = p.label, modifier = Modifier.padding(top = 12.dp))
                    Slider(draft.threshold.toFloat(), { draft = draft.copy(threshold = it.toInt()) }, valueRange = 0f..10f, steps = 9)
                    OutlinedTextField(draft.endpoint, { draft = draft.copy(endpoint = it.trim()) }, label = { Text(s.aiEndpoint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.model, { draft = draft.copy(model = it.trim()) }, label = { Text(s.aiModel) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    OutlinedTextField(draft.apiKey, { draft = draft.copy(apiKey = it.trim()) }, label = { Text(s.aiKey) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    GlassButton(s.aiTest, busy = testing, icon = Duo.Sparkle) {
                        model.repo.setAi(draft); testing = true; result = null
                        scope.launch { result = model.repo.rescoreAll() ?: s.aiOk; testing = false }
                    }
                    (result ?: ai.lastError)?.let { Text(it, style = GlassType.footnote, color = if (it == s.aiOk) p.accent else p.danger, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(p.hairline))
            Text(s.about, style = GlassType.footnote, color = p.secondary, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun Toast(model: AppModel, modifier: Modifier) {
    val p = LocalGlass.current
    val text = model.toast
    val bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(text) { if (text != null) { delay(2400); model.toast = null } }
    AnimatedVisibility(text != null, modifier.padding(bottom = bottom + TAB_BAR_HEIGHT + TAB_BAR_BOTTOM + 14.dp), enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Text(text ?: "", style = GlassType.footnote, fontWeight = FontWeight.SemiBold, color = p.label,
            modifier = Modifier.clip(CircleShape).background(p.card.compositeOver(p.base)).border(0.5.dp, p.hairline, CircleShape).padding(horizontal = 18.dp, vertical = 10.dp))
    }
}
