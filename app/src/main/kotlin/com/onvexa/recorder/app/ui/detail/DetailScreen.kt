package com.onvexa.recorder.app.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onvexa.recorder.app.R
import com.onvexa.recorder.app.data.RecordingDetail
import com.onvexa.recorder.app.player.PlaybackState
import com.onvexa.recorder.app.ui.common.BulletList
import com.onvexa.recorder.app.ui.common.ChatMessages
import com.onvexa.recorder.app.ui.common.DisabledChatInput
import com.onvexa.recorder.app.ui.common.EmptyState
import com.onvexa.recorder.app.ui.common.SectionCard
import com.onvexa.recorder.app.ui.common.SimpleRichText
import com.onvexa.recorder.app.ui.common.SourceBadge
import com.onvexa.recorder.app.ui.common.StatusPill
import com.onvexa.recorder.app.ui.common.TagChip
import com.onvexa.recorder.app.ui.common.displayTitle
import com.onvexa.recorder.app.ui.common.formatDateTime
import com.onvexa.recorder.app.ui.common.speakerName
import com.onvexa.recorder.core.database.entity.SpeakerEntity
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.model.Source
import com.onvexa.recorder.core.model.States
import com.onvexa.recorder.core.model.activeSegmentIndex
import com.onvexa.recorder.core.model.formatClock
import com.onvexa.recorder.core.model.normalizeForSearch

private val tabTitles = listOf(
    R.string.tab_summary,
    R.string.tab_transcript,
    R.string.tab_mindmap,
    R.string.tab_tasks,
    R.string.tab_images,
    R.string.tab_ask,
)

@Composable
fun DetailScreen(onBack: () -> Unit, viewModel: DetailViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val detail = ui.detail

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (detail == null) {
            TopBar(title = "", onBack = onBack, menu = null)
            if (ui.loaded) {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.detail_not_found),
                    subtitle = "",
                )
            }
            return@Column
        }
        DetailContent(detail, playback, viewModel, onBack)
    }
}

@Composable
private fun ColumnScope.DetailContent(
    detail: RecordingDetail,
    playback: PlaybackState,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    TopBar(
        title = displayTitle(detail.recording.title),
        onBack = onBack,
        menu = MenuActions(onRename = { renaming = true }, onDelete = { deleting = true }),
        onTitleClick = { renaming = true },
    )
    MetaRow(detail)
    PlayerBar(detail, playback, viewModel)

    ScrollableTabRow(
        selectedTabIndex = tab,
        containerColor = OnvexaPalette.Background,
        contentColor = OnvexaPalette.PrimaryLight,
        edgePadding = 8.dp,
        divider = {},
    ) {
        tabTitles.forEachIndexed { index, title ->
            Tab(
                selected = tab == index,
                onClick = { tab = index },
                text = { Text(stringResource(title)) },
                selectedContentColor = OnvexaPalette.PrimaryLight,
                unselectedContentColor = OnvexaPalette.TextMuted,
            )
        }
    }

    Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
        when (tab) {
            0 -> SummaryTab(detail, viewModel::seekTo)
            1 -> TranscriptTab(detail, playback.positionMs, viewModel)
            2 -> MindMapTab(detail)
            3 -> TasksTab(detail, viewModel)
            4 -> ImagesTab(detail, viewModel::seekTo)
            else -> AskTab(detail, viewModel::seekTo)
        }
    }

    if (renaming) {
        TextDialog(
            title = stringResource(R.string.detail_rename_title),
            initial = detail.recording.title.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = OnvexaPalette.Surface,
            title = { Text(stringResource(R.string.detail_delete_title), color = OnvexaPalette.Text) },
            text = { Text(stringResource(R.string.detail_delete_message), color = OnvexaPalette.TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    viewModel.delete(onDone = onBack)
                }) { Text(stringResource(R.string.action_delete), color = OnvexaPalette.Error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private class MenuActions(val onRename: () -> Unit, val onDelete: () -> Unit)

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    menu: MenuActions?,
    onTitleClick: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.detail_back),
                tint = OnvexaPalette.Text,
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f).let { m ->
                if (onTitleClick != null) m.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTitleClick) else m
            }.padding(horizontal = 4.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleLarge,
            color = OnvexaPalette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (menu != null) {
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.detail_more),
                        tint = OnvexaPalette.Text,
                    )
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.detail_rename)) },
                        onClick = {
                            open = false
                            menu.onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = OnvexaPalette.Error) },
                        onClick = {
                            open = false
                            menu.onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaRow(detail: RecordingDetail) {
    val source = Source.fromKey(detail.recording.source)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SourceBadge(source, Modifier.size(28.dp))
        Text(
            text = formatDateTime(detail.recording.startedAt),
            style = MaterialTheme.typography.bodySmall,
            color = OnvexaPalette.TextMuted,
        )
        StatusPill(detail.recording.status)
        detail.tags.take(3).forEach { TagChip(it.name) }
    }
}

@Composable
private fun PlayerBar(detail: RecordingDetail, playback: PlaybackState, viewModel: DetailViewModel) {
    val duration = playback.durationMs.coerceAtLeast(1L)
    val marks = remember(detail.bookmarks, detail.attachments, detail.notes) {
        (detail.bookmarks.map { it.positionMs } +
            detail.attachments.map { it.positionMs } +
            detail.notes.map { it.positionMs })
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = OnvexaPalette.Surface,
        border = BorderStroke(1.dp, OnvexaPalette.Outline),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 10.dp)) {
                    marks.forEach { ms ->
                        val x = size.width * (ms.toFloat() / duration).coerceIn(0f, 1f)
                        drawCircle(OnvexaPalette.Accent, radius = 3.dp.toPx(), center = Offset(x, size.height / 2f))
                    }
                }
                Slider(
                    value = playback.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.padding(top = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = OnvexaPalette.PrimaryLight,
                        activeTrackColor = OnvexaPalette.PrimaryLight,
                        inactiveTrackColor = OnvexaPalette.SurfaceHigh,
                    ),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(formatClock(playback.positionMs), style = MaterialTheme.typography.labelMedium, color = OnvexaPalette.TextMuted)
                Spacer(Modifier.weight(1f))
                Text(formatClock(playback.durationMs), style = MaterialTheme.typography.labelMedium, color = OnvexaPalette.TextMuted)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = viewModel::cycleSpeed) {
                    Text(
                        stringResource(R.string.player_speed, speedLabel(playback.speed)),
                        color = OnvexaPalette.PrimaryLight,
                    )
                }
                IconButton(onClick = { viewModel.skip(-10_000) }) {
                    Icon(Icons.Default.Replay10, stringResource(R.string.player_back), tint = OnvexaPalette.Text)
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(OnvexaPalette.Primary, CircleShape)
                        .clickable(onClick = viewModel::togglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (playback.isPlaying) R.string.player_pause else R.string.player_play),
                        tint = OnvexaPalette.OnPrimary,
                    )
                }
                IconButton(onClick = { viewModel.skip(10_000) }) {
                    Icon(Icons.Default.Forward10, stringResource(R.string.player_forward), tint = OnvexaPalette.Text)
                }
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')

// ---------------------------------------------------------------- Resumen

@Composable
private fun SummaryTab(detail: RecordingDetail, onSeek: (Long) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val summaries = detail.summaries
    if (summaries.isEmpty()) {
        val failed = detail.recording.status == States.FAILED
        EmptyState(
            icon = Icons.Default.Search,
            title = stringResource(if (failed) R.string.summary_empty_failed else R.string.summary_empty_processing),
            subtitle = stringResource(if (failed) R.string.summary_empty_failed_hint else R.string.summary_empty_processing_hint),
        )
        return
    }
    val summary = summaries[selected.coerceIn(0, summaries.lastIndex)]
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (summaries.size > 1) {
            item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(summaries) { index, s ->
                        FilterChip(
                            selected = index == selected,
                            onClick = { selected = index },
                            label = { Text(s.templateName ?: stringResource(R.string.summary_default_name)) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = OnvexaPalette.TextMuted,
                                selectedContainerColor = OnvexaPalette.SurfaceHigh,
                                selectedLabelColor = OnvexaPalette.PrimaryLight,
                            ),
                        )
                    }
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.summary_executive)) {
                SimpleRichText(summary.executive)
            }
        }
        if (summary.detailed.isNotBlank()) {
            item {
                SectionCard(stringResource(R.string.summary_detailed)) { SimpleRichText(summary.detailed) }
            }
        }
        if (summary.topics.isNotEmpty()) {
            item { SectionCard(stringResource(R.string.summary_topics)) { BulletList(summary.topics) } }
        }
        if (summary.decisions.isNotEmpty()) {
            item { SectionCard(stringResource(R.string.summary_decisions)) { BulletList(summary.decisions) } }
        }
        if (summary.questions.isNotEmpty()) {
            item { SectionCard(stringResource(R.string.summary_questions)) { BulletList(summary.questions) } }
        }
        if (detail.notes.isNotEmpty() || detail.bookmarks.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.summary_marks)) {
                    detail.bookmarks.forEach { b ->
                        MarkRow(b.positionMs, b.label ?: stringResource(R.string.summary_highlight), onSeek)
                    }
                    detail.notes.forEach { n -> MarkRow(n.positionMs, n.text, onSeek) }
                }
            }
        }
    }
}

@Composable
private fun MarkRow(positionMs: Long, text: String, onSeek: (Long) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape).clickable { onSeek(positionMs) }.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(formatClock(positionMs), style = MaterialTheme.typography.labelMedium, color = OnvexaPalette.PrimaryLight)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = OnvexaPalette.Text)
    }
}

// ---------------------------------------------------------------- Transcripción

@Composable
private fun TranscriptTab(detail: RecordingDetail, positionMs: Long, viewModel: DetailViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<SpeakerEntity?>(null) }
    val segments = detail.segments
    if (segments.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Search,
            title = stringResource(R.string.transcript_empty),
            subtitle = stringResource(R.string.transcript_empty_hint),
        )
        return
    }
    val speakers = remember(detail.speakers) { detail.speakers.associateBy { it.id } }
    val speakerOrder = remember(detail.speakers) { detail.speakers.map { it.id } }
    val starts = remember(segments) { segments.map { it.startMs } }
    val active = activeSegmentIndex(starts, positionMs)
    val needle = normalizeForSearch(query).trim()
    val visible = remember(segments, needle) {
        segments.withIndex().filter { needle.isEmpty() || normalizeForSearch(it.value.text).contains(needle) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(active, needle) {
        if (needle.isEmpty() && active >= 0) listState.animateScrollToItem(active + 1)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text(stringResource(R.string.transcript_search_hint), color = OnvexaPalette.TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnvexaPalette.TextMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, stringResource(R.string.library_clear_search), tint = OnvexaPalette.TextMuted)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OnvexaPalette.Text,
                    unfocusedTextColor = OnvexaPalette.Text,
                    focusedBorderColor = OnvexaPalette.PrimaryLight,
                    unfocusedBorderColor = OnvexaPalette.Outline,
                    cursorColor = OnvexaPalette.PrimaryLight,
                ),
            )
        }
        items(visible, key = { it.value.id }) { (index, segment) ->
            val speaker = segment.speakerId?.let { speakers[it] }
            val color = speaker?.let {
                OnvexaPalette.MindMapBranches[speakerOrder.indexOf(it.id).coerceAtLeast(0) % OnvexaPalette.MindMapBranches.size]
            } ?: OnvexaPalette.PrimaryLight
            val isActive = index == active
            val shape = RoundedCornerShape(14.dp)
            Surface(
                modifier = Modifier.fillMaxWidth().clip(shape).clickable { viewModel.seekTo(segment.startMs) },
                shape = shape,
                color = if (isActive) OnvexaPalette.SurfaceHigh else OnvexaPalette.Surface,
                border = BorderStroke(1.dp, if (isActive) color else OnvexaPalette.Outline),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (speaker != null) {
                            Text(
                                text = speakerName(speaker.displayName, speaker.label),
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { editing = speaker },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = color,
                            )
                        }
                        Text(
                            text = formatClock(segment.startMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = OnvexaPalette.TextMuted,
                        )
                    }
                    Text(segment.text, style = MaterialTheme.typography.bodyMedium, color = OnvexaPalette.Text)
                }
            }
        }
    }

    editing?.let { speaker ->
        TextDialog(
            title = stringResource(R.string.transcript_rename_speaker),
            initial = speaker.displayName.orEmpty(),
            onDismiss = { editing = null },
            onConfirm = {
                viewModel.renameSpeaker(speaker.id, it)
                editing = null
            },
        )
    }
}

// ---------------------------------------------------------------- Mapa mental

@Composable
private fun MindMapTab(detail: RecordingDetail) {
    val root = detail.mindMap
    if (root == null) {
        EmptyState(
            icon = Icons.Default.Search,
            title = stringResource(R.string.mindmap_empty),
            subtitle = stringResource(R.string.mindmap_empty_hint),
        )
    } else {
        MindMapView(root, Modifier.fillMaxSize())
    }
}

// ---------------------------------------------------------------- Tareas

@Composable
private fun TasksTab(detail: RecordingDetail, viewModel: DetailViewModel) {
    if (detail.actions.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Search,
            title = stringResource(R.string.tasks_empty_recording),
            subtitle = "",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(detail.actions, key = { it.id }) { item ->
            val done = item.status == "done"
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = OnvexaPalette.Surface,
                border = BorderStroke(1.dp, OnvexaPalette.Outline),
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = done,
                        onCheckedChange = { viewModel.setActionDone(item.id, it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = OnvexaPalette.Primary,
                            uncheckedColor = OnvexaPalette.TextMuted,
                            checkmarkColor = OnvexaPalette.OnPrimary,
                        ),
                    )
                    Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (done) OnvexaPalette.TextMuted else OnvexaPalette.Text,
                            textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                        )
                        val owner = item.assignee ?: stringResource(R.string.task_unassigned)
                        val due = item.dueDate?.let { stringResource(R.string.task_due, it) }
                        Text(
                            text = listOfNotNull(owner, due).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnvexaPalette.TextMuted,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Imágenes

@Composable
private fun ImagesTab(detail: RecordingDetail, onSeek: (Long) -> Unit) {
    if (detail.attachments.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Image,
            title = stringResource(R.string.images_empty),
            subtitle = stringResource(R.string.images_empty_hint),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(detail.attachments, key = { it.id }) { attachment ->
            val shape = RoundedCornerShape(14.dp)
            Surface(
                modifier = Modifier.fillMaxWidth().clip(shape).clickable { onSeek(attachment.positionMs) },
                shape = shape,
                color = OnvexaPalette.Surface,
                border = BorderStroke(1.dp, OnvexaPalette.Outline),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(72.dp).background(OnvexaPalette.SurfaceHigh, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Image, null, tint = OnvexaPalette.PrimaryLight)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = formatClock(attachment.positionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = OnvexaPalette.PrimaryLight,
                        )
                        Text(
                            text = attachment.description ?: stringResource(R.string.images_no_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (attachment.description == null) OnvexaPalette.TextMuted else OnvexaPalette.Text,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Preguntar

@Composable
private fun AskTab(detail: RecordingDetail, onSeek: (Long) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (detail.chat.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.ask_recording_empty),
                    subtitle = stringResource(R.string.ask_recording_empty_hint),
                )
            }
        } else {
            ChatMessages(detail.chat, Modifier.weight(1f).fillMaxWidth(), onCitation = onSeek)
        }
        DisabledChatInput()
    }
}

// ---------------------------------------------------------------- Diálogo de texto

@Composable
internal fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OnvexaPalette.Surface,
        title = { Text(title, color = OnvexaPalette.Text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OnvexaPalette.Text,
                    unfocusedTextColor = OnvexaPalette.Text,
                    focusedBorderColor = OnvexaPalette.PrimaryLight,
                    unfocusedBorderColor = OnvexaPalette.Outline,
                    cursorColor = OnvexaPalette.PrimaryLight,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
