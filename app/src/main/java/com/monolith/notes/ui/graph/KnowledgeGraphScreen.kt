package com.monolith.notes.ui.graph

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.model.Note
import com.monolith.notes.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

enum class GraphFilterMode {
    ALL,
    CONNECTED_ONLY,
    HUBS_ONLY
}

@Composable
fun KnowledgeGraphScreen(
    notes: List<Note>,
    onBack: () -> Unit,
    onNavigateToNote: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    var clusterMode by remember { mutableStateOf(ClusterMode.FOLDER) }
    var filterMode by remember { mutableStateOf(GraphFilterMode.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var selectedNodeIndex by remember { mutableStateOf<Int?>(null) }
    var draggedNodeIndex by remember { mutableStateOf<Int?>(null) }

    // Canvas Transformation State (Pan & Zoom)
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Build Graph Data using KnowledgeGraphEngine
    val graphBundle = remember(notes, clusterMode) {
        KnowledgeGraphEngine.buildGraph(notes, clusterMode)
    }

    val allNodes = graphBundle.nodes
    val allEdges = graphBundle.edges
    val analytics = graphBundle.analytics

    // Active nodes taking filterMode into account
    val visibleNodeIndices = remember(graphBundle, filterMode) {
        allNodes.indices.filter { i ->
            val node = allNodes[i]
            when (filterMode) {
                GraphFilterMode.ALL -> true
                GraphFilterMode.CONNECTED_ONLY -> node.degree > 0
                GraphFilterMode.HUBS_ONLY -> node.isHub
            }
        }.toSet()
    }

    val visibleEdges = remember(allEdges, visibleNodeIndices) {
        allEdges.filter {
            visibleNodeIndices.contains(it.sourceIndex) && visibleNodeIndices.contains(it.targetIndex)
        }
    }

    // Neighbors of selected node for highlight aura & dimming
    val selectedNeighbors = remember(selectedNodeIndex, allEdges) {
        val target = selectedNodeIndex ?: return@remember emptySet<Int>()
        val set = HashSet<Int>()
        for (edge in allEdges) {
            if (edge.sourceIndex == target) set.add(edge.targetIndex)
            else if (edge.targetIndex == target) set.add(edge.sourceIndex)
        }
        set
    }

    // 120Hz Physics Simulation Loop with Adaptive Self-Tuning Parameters
    var physicsIteration by remember { mutableIntStateOf(0) }
    val nodeCount = allNodes.size

    LaunchedEffect(graphBundle, filterMode) {
        if (allNodes.isEmpty()) return@LaunchedEffect

        // Adaptive forces based on node density
        val kRepulse = (24000f + 1400f * sqrt(nodeCount.toFloat())).coerceIn(22000f, 60000f)
        val kSpring = 0.042f
        val restLength = (130f + 20f * log2(nodeCount.coerceAtLeast(2).toFloat())).coerceIn(120f, 240f)
        val damping = 0.84f
        val centerForce = (0.016f / sqrt(nodeCount.coerceAtLeast(1).toFloat())).coerceIn(0.004f, 0.024f)

        withContext(Dispatchers.Default) {
            for (step in 0 until 180) {
                KnowledgeGraphEngine.stepPhysics(
                    nodes = allNodes,
                    edges = visibleEdges,
                    kRepulse = kRepulse,
                    kSpring = kSpring,
                    restLength = restLength,
                    damping = damping,
                    centerForce = centerForce,
                    draggedIndex = draggedNodeIndex
                )

                if (step % 2 == 0) {
                    withContext(Dispatchers.Main) {
                        physicsIteration = step
                    }
                    delay(12) // Fluid convergence
                }
            }
            withContext(Dispatchers.Main) {
                physicsIteration++
            }
        }
    }

    // Traveling Energy Pulse Animation along Directed Links
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    // Back button handling
    BackHandler {
        if (selectedNodeIndex != null) {
            selectedNodeIndex = null
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    val primaryTextColor = iOSTextPrimary
    val themeAccent = iOSYellow

    // Text Paint for Canvas Node Labels
    val labelPaint = remember(primaryTextColor) {
        Paint().apply {
            color = primaryTextColor.toArgb()
            textSize = 30f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 2f, Color.Black.copy(alpha = 0.6f).toArgb())
        }
    }

    val selectedLabelPaint = remember(themeAccent) {
        Paint().apply {
            color = themeAccent.toArgb()
            textSize = 34f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 2f, Color.Black.toArgb())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(iOSBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Main Interactive Visual Canvas
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Transform: Pan & Pinch-to-Zoom
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.2f, 4.0f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                // Tap & Long-Press (Pin/Unpin)
                .pointerInput(allNodes, scale, offsetX, offsetY) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val graphX = (tapOffset.x - centerX - offsetX) / scale
                            val graphY = (tapOffset.y - centerY - offsetY) / scale

                            var closestIndex: Int? = null
                            var minDistance = Float.MAX_VALUE

                            for (i in visibleNodeIndices) {
                                val node = allNodes[i]
                                val dx = node.x - graphX
                                val dy = node.y - graphY
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist < (node.radius + 24f) && dist < minDistance) {
                                    minDistance = dist
                                    closestIndex = i
                                }
                            }

                            if (closestIndex != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedNodeIndex = closestIndex
                            } else {
                                selectedNodeIndex = null
                            }
                        },
                        onLongPress = { tapOffset ->
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val graphX = (tapOffset.x - centerX - offsetX) / scale
                            val graphY = (tapOffset.y - centerY - offsetY) / scale

                            for (i in visibleNodeIndices) {
                                val node = allNodes[i]
                                val dx = node.x - graphX
                                val dy = node.y - graphY
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist < (node.radius + 24f)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    node.isPinned = !node.isPinned
                                    break
                                }
                            }
                        }
                    )
                }
                // Direct Node Dragging & Flinging
                .pointerInput(allNodes, scale, offsetX, offsetY) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val graphX = (startOffset.x - centerX - offsetX) / scale
                            val graphY = (startOffset.y - centerY - offsetY) / scale

                            for (i in visibleNodeIndices) {
                                val node = allNodes[i]
                                val dx = node.x - graphX
                                val dy = node.y - graphY
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist < (node.radius + 26f)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    draggedNodeIndex = i
                                    selectedNodeIndex = i
                                    node.vx = 0f
                                    node.vy = 0f
                                    break
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val targetIndex = draggedNodeIndex
                            if (targetIndex != null && targetIndex in allNodes.indices) {
                                val node = allNodes[targetIndex]
                                val deltaX = dragAmount.x / scale
                                val deltaY = dragAmount.y / scale
                                node.x += deltaX
                                node.y += deltaY
                                node.vx = deltaX * 0.45f
                                node.vy = deltaY * 0.45f
                                physicsIteration++
                            }
                        },
                        onDragEnd = {
                            draggedNodeIndex = null
                        },
                        onDragCancel = {
                            draggedNodeIndex = null
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f
            val centerY = canvasHeight / 2f

            @Suppress("UNUSED_VARIABLE")
            val iter = physicsIteration

            fun toScreenX(gx: Float): Float = centerX + offsetX + (gx * scale)
            fun toScreenY(gy: Float): Float = centerY + offsetY + (gy * scale)

            // 1. Draw Directed Links
            for (edgeIndex in visibleEdges.indices) {
                val edge = visibleEdges[edgeIndex]
                val srcNode = allNodes[edge.sourceIndex]
                val dstNode = allNodes[edge.targetIndex]

                val isConnectedToSelected = selectedNodeIndex != null &&
                        (edge.sourceIndex == selectedNodeIndex || edge.targetIndex == selectedNodeIndex)

                val edgeColor = when {
                    isConnectedToSelected -> themeAccent
                    selectedNodeIndex != null -> Color(0xFF2C2C2E).copy(alpha = 0.25f)
                    else -> srcNode.clusterColor.copy(alpha = 0.32f)
                }

                val strokeWidth = when {
                    isConnectedToSelected -> 3.2f * scale
                    else -> 1.5f * scale
                }.coerceIn(1f, 7f)

                val sx = toScreenX(srcNode.x)
                val sy = toScreenY(srcNode.y)
                val tx = toScreenX(dstNode.x)
                val ty = toScreenY(dstNode.y)

                drawLine(
                    color = edgeColor,
                    start = Offset(sx, sy),
                    end = Offset(tx, ty),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // Directional Indicator Midway along the Link
                val midX = (sx + tx) / 2f
                val midY = (sy + ty) / 2f

                // Small directional dot indicator
                drawCircle(
                    color = if (isConnectedToSelected) themeAccent else srcNode.clusterColor.copy(alpha = 0.7f),
                    radius = (2.2f * scale).coerceIn(1.5f, 5f),
                    center = Offset(midX, midY)
                )

                // 2. Traveling Energy Pulse Particles
                if (isConnectedToSelected || selectedNodeIndex == null) {
                    val pulseOffset = (pulseProgress + (edgeIndex * 0.17f)) % 1f
                    val pulseX = sx + (tx - sx) * pulseOffset
                    val pulseY = sy + (ty - sy) * pulseOffset

                    drawCircle(
                        color = if (isConnectedToSelected) themeAccent else srcNode.clusterColor.copy(alpha = 0.85f),
                        radius = (3.0f * scale).coerceIn(2f, 6.5f),
                        center = Offset(pulseX, pulseY)
                    )
                }
            }

            // 3. Draw Nodes with Cluster Color & Glowing Auras
            for (i in visibleNodeIndices) {
                val node = allNodes[i]
                val sx = toScreenX(node.x)
                val sy = toScreenY(node.y)

                // Viewport culling
                if (sx < -120 || sx > canvasWidth + 120 || sy < -120 || sy > canvasHeight + 120) {
                    continue
                }

                val isSelected = selectedNodeIndex == i
                val isConnectedToSelected = selectedNeighbors.contains(i)
                val isQueryMatch = searchQuery.isNotBlank() &&
                        node.note.displayTitle.contains(searchQuery, ignoreCase = true)

                val nodeRadius = (node.radius * scale).coerceIn(5f, 65f)

                // Outer Radial Glowing Aura
                if (isSelected || isConnectedToSelected || isQueryMatch || node.isHub) {
                    val auraColor = when {
                        isSelected || isQueryMatch -> themeAccent.copy(alpha = 0.38f)
                        isConnectedToSelected -> node.clusterColor.copy(alpha = 0.28f)
                        else -> node.clusterColor.copy(alpha = 0.18f)
                    }
                    val auraExtra = when {
                        isSelected -> 14f * scale
                        node.isHub -> 10f * scale
                        else -> 6f * scale
                    }.coerceIn(4f, 26f)

                    drawCircle(
                        color = auraColor,
                        radius = nodeRadius + auraExtra,
                        center = Offset(sx, sy)
                    )
                }

                // Core Node Fill
                val fillColor = when {
                    isSelected -> themeAccent
                    isQueryMatch -> themeAccent
                    selectedNodeIndex != null && !isConnectedToSelected -> node.clusterColor.copy(alpha = 0.25f)
                    else -> node.clusterColor
                }

                drawCircle(
                    color = fillColor,
                    radius = nodeRadius,
                    center = Offset(sx, sy)
                )

                // Border Ring (Double Ring for Hubs)
                val borderColor = when {
                    isSelected -> Color.White
                    isQueryMatch -> themeAccent
                    node.isHub -> themeAccent.copy(alpha = 0.9f)
                    else -> Color.White.copy(alpha = 0.35f)
                }
                drawCircle(
                    color = borderColor,
                    radius = nodeRadius,
                    center = Offset(sx, sy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = (if (node.isHub) 2.2f else 1.2f) * scale)
                )

                // Pinned indicator dot inside core
                if (node.isPinned) {
                    drawCircle(
                        color = Color.White,
                        radius = (3f * scale).coerceIn(2f, 7f),
                        center = Offset(sx, sy)
                    )
                }

                // Title Labels
                val shouldDrawLabel = isSelected || isQueryMatch || isConnectedToSelected ||
                        (scale > 0.8f && node.degree > 0) || (scale > 1.2f)

                if (shouldDrawLabel) {
                    val label = node.note.displayTitle
                    val paint = if (isSelected || isQueryMatch) selectedLabelPaint else labelPaint
                    paint.textSize = (13.sp.toPx() * scale).coerceIn(16f, 38f)

                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        sx,
                        sy + nodeRadius + (16f * scale).coerceIn(12f, 28f),
                        paint
                    )
                }
            }
        }

        // Top Controls & Intelligence Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "< Notes" Back Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = themeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Notes",
                        color = themeAccent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Vault Analytics Pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(iOSCardBackground)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = themeAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${notes.size} Notes • ${visibleEdges.size} Links • ${analytics.hubCount} Hubs",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        )
                    }
                }

                // Header Actions: Search & Recenter View
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search Graph",
                            tint = if (isSearchActive) themeAccent else primaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                            selectedNodeIndex = null
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Recenter Graph",
                            tint = themeAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Search Bar Input Field (Animated Expand)
            AnimatedVisibility(
                visible = isSearchActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iOSCardBackground)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            // Auto-pan camera to matching node if found
                            if (query.isNotBlank()) {
                                val matchIndex = allNodes.indexOfFirst {
                                    it.note.displayTitle.contains(query, ignoreCase = true)
                                }
                                if (matchIndex != -1) {
                                    val matchNode = allNodes[matchIndex]
                                    selectedNodeIndex = matchIndex
                                    offsetX = -matchNode.x * scale
                                    offsetY = -matchNode.y * scale
                                }
                            }
                        },
                        placeholder = {
                            Text("Search notes in knowledge graph...", fontSize = 13.5.sp, color = iOSTextTertiary)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Intelligent Quick Filter Pills HUD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterPill(
                    label = "All (${notes.size})",
                    isSelected = filterMode == GraphFilterMode.ALL,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        filterMode = GraphFilterMode.ALL
                    }
                )
                FilterPill(
                    label = "Connected",
                    isSelected = filterMode == GraphFilterMode.CONNECTED_ONLY,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        filterMode = GraphFilterMode.CONNECTED_ONLY
                    }
                )
                FilterPill(
                    label = "Core Hubs (${analytics.hubCount})",
                    isSelected = filterMode == GraphFilterMode.HUBS_ONLY,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        filterMode = GraphFilterMode.HUBS_ONLY
                    }
                )

                // Cluster Mode Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(iOSCardBackground)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            clusterMode = if (clusterMode == ClusterMode.FOLDER) ClusterMode.TAG else ClusterMode.FOLDER
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (clusterMode == ClusterMode.FOLDER) "Clusters: Folders" else "Clusters: Tags",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = iOSTextSecondary
                    )
                }
            }
        }

        // Selected Note Intelligence & Detail Float Card
        val selectedNode = selectedNodeIndex?.let { if (it in allNodes.indices) allNodes[it] else null }
        AnimatedVisibility(
            visible = selectedNode != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            if (selectedNode != null) {
                val suggestions = analytics.suggestions[selectedNode.id] ?: emptyList()

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = iOSCardBackground,
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Title & Actions Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = selectedNode.note.displayTitle,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (selectedNode.isHub) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(themeAccent.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "CORE HUB",
                                                style = MonolithTypography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                ),
                                                color = themeAccent
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = "${selectedNode.degree} Links (${selectedNode.inDegree} in • ${selectedNode.outDegree} out)",
                                        fontSize = 12.sp,
                                        color = themeAccent
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(selectedNode.clusterColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 1.5.dp)
                                    ) {
                                        Text(
                                            text = selectedNode.clusterName,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = selectedNode.clusterColor
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateToNote(selectedNode.note)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = themeAccent,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Open",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                        }

                        // Text Snippet Preview
                        if (selectedNode.note.previewSnippet.isNotBlank() && selectedNode.note.previewSnippet != "No additional text") {
                            Text(
                                text = selectedNode.note.previewSnippet,
                                fontSize = 12.5.sp,
                                color = iOSTextSecondary,
                                maxLines = 2,
                                lineHeight = 17.sp
                            )
                        }

                        // Intelligent Connection Recommendations
                        if (suggestions.isNotEmpty()) {
                            HorizontalDivider(thickness = 0.5.dp, color = iOSDivider)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = themeAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Suggested Connection: ",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = themeAccent
                                )
                                val firstSuggestion = suggestions.first()
                                Text(
                                    text = "[[${firstSuggestion.targetNote.displayTitle}]] (${firstSuggestion.reason})",
                                    fontSize = 11.5.sp,
                                    color = iOSTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) iOSYellow else iOSCardBackground
    val contentColor = if (isSelected) Color.Black else iOSTextPrimary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}
