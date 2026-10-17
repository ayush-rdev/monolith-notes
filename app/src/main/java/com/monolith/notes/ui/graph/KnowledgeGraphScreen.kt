package com.monolith.notes.ui.graph

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Obsidian-grade Personal Knowledge Graph.
 * Features unified touch processor (focal-centered pinch zoom & 1-finger node drag/pan),
 * zero-fluff professional top bar, Vault Intelligence Sheet ('i'), and frustum-culled rendering.
 */
@Composable
fun KnowledgeGraphScreen(
    notes: List<Note>,
    onBack: () -> Unit,
    onNavigateToNote: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showIntelligenceSheet by remember { mutableStateOf(false) }

    var selectedNodeIndex by remember { mutableStateOf<Int?>(null) }
    var draggedNodeIndex by remember { mutableStateOf<Int?>(null) }

    // Canvas Transformation State (Pan & Zoom)
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var defaultScale by remember { mutableFloatStateOf(0.6f) }

    // Build Graph Data using KnowledgeGraphEngine
    val graphBundle = remember(notes) {
        KnowledgeGraphEngine.buildGraph(notes, ClusterMode.FOLDER)
    }

    val allNodes = graphBundle.nodes
    val allEdges = graphBundle.edges
    val analytics = graphBundle.analytics

    // Top 4 landmark hubs with highest connectivity (for uncluttered overview labels)
    val topLandmarkIds = remember(allNodes) {
        allNodes.sortedByDescending { it.degree }.take(4).map { it.id }.toSet()
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

    // 120Hz Physics Simulation Loop with Early Equilibrium Convergence
    var physicsIteration by remember { mutableIntStateOf(0) }
    val nodeCount = allNodes.size

    LaunchedEffect(graphBundle) {
        if (allNodes.isEmpty()) return@LaunchedEffect

        val kRepulse = (14000f + 600f * sqrt(nodeCount.toFloat())).coerceIn(12000f, 32000f)
        val kSpring = 0.036f
        val restLength = (80f + 12f * log2(nodeCount.coerceAtLeast(2).toFloat())).coerceIn(75f, 150f)
        val damping = 0.80f
        val centerForce = (0.012f / sqrt(nodeCount.coerceAtLeast(1).toFloat())).coerceIn(0.003f, 0.016f)

        withContext(Dispatchers.Default) {
            // Pre-stabilize physics silently off-screen so the graph doesn't explode when opened
            for (step in 0 until 40) {
                KnowledgeGraphEngine.stepPhysics(
                    nodes = allNodes,
                    edges = allEdges,
                    kRepulse = kRepulse,
                    kSpring = kSpring,
                    restLength = restLength,
                    damping = damping,
                    centerForce = centerForce,
                    draggedIndex = draggedNodeIndex
                )
            }

            // Compute Bounding Box and set Auto-Fit Scale so whole galaxy fits viewport
            if (allNodes.isNotEmpty()) {
                var minX = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (n in allNodes) {
                    if (n.x < minX) minX = n.x
                    if (n.x > maxX) maxX = n.x
                    if (n.y < minY) minY = n.y
                    if (n.y > maxY) maxY = n.y
                }
                val spanX = (maxX - minX).coerceAtLeast(200f) + 160f
                val spanY = (maxY - minY).coerceAtLeast(200f) + 240f
                val autoFitScale = minOf(950f / spanX, 1600f / spanY).coerceIn(0.25f, 0.85f)

                withContext(Dispatchers.Main) {
                    defaultScale = autoFitScale
                    scale = autoFitScale
                    offsetX = 0f
                    offsetY = 0f
                    physicsIteration++
                }
            }

            // Smoothly converge remaining steps
            for (step in 40 until 120) {
                val energy = KnowledgeGraphEngine.stepPhysics(
                    nodes = allNodes,
                    edges = allEdges,
                    kRepulse = kRepulse,
                    kSpring = kSpring,
                    restLength = restLength,
                    damping = damping,
                    centerForce = centerForce,
                    draggedIndex = draggedNodeIndex
                )

                if (step > 50 && energy < 0.035f && draggedNodeIndex == null) {
                    break
                }

                if (step % 2 == 0) {
                    withContext(Dispatchers.Main) {
                        physicsIteration = step
                    }
                    delay(16)
                }
            }
            withContext(Dispatchers.Main) {
                physicsIteration++
            }
        }
    }

    // Traveling Energy Pulse Animation along Directed Links (Active only when a node is selected)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
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
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 2f, Color.Black.copy(alpha = 0.6f).toArgb())
        }
    }

    val selectedLabelPaint = remember(themeAccent) {
        Paint().apply {
            color = themeAccent.toArgb()
            textSize = 32f
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
        // Main Interactive Visual Canvas with Unified Obsidian Touch Engine
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position
                        var isTransforming = false
                        var activeDraggedNode: Int? = null

                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val centerX = canvasW / 2f
                        val centerY = canvasH / 2f

                        // Convert touch coordinate to graph space
                        val currentScale = scale
                        val currentOffX = offsetX
                        val currentOffY = offsetY
                        val graphDownX = (downPos.x - centerX - currentOffX) / currentScale
                        val graphDownY = (downPos.y - centerY - currentOffY) / currentScale

                        // Hit-test nodes (with a comfortable 26px touch target margin)
                        for (i in allNodes.indices) {
                            val node = allNodes[i]
                            val dx = node.x - graphDownX
                            val dy = node.y - graphDownY
                            val dist = sqrt(dx * dx + dy * dy)
                            if (dist < (node.radius + 26f)) {
                                activeDraggedNode = i
                                draggedNodeIndex = i
                                node.vx = 0f
                                node.vy = 0f
                                break
                            }
                        }

                        var pastTouchSlop = false

                        do {
                            val event = awaitPointerEvent()
                            val pressedPointers = event.changes.filter { it.pressed }
                            if (pressedPointers.isEmpty()) break

                            if (pressedPointers.size >= 2) {
                                // Multi-finger pinch-to-zoom & two-finger pan
                                activeDraggedNode = null
                                draggedNodeIndex = null
                                isTransforming = true

                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid()

                                val oldScale = scale
                                val newScale = (oldScale * zoomChange).coerceIn(0.18f, 4.5f)
                                scale = newScale

                                // Zoom centered around focal point (centroid)
                                val focalX = centroid.x - centerX
                                val focalY = centroid.y - centerY
                                offsetX += panChange.x + focalX * (1f - newScale / oldScale)
                                offsetY += panChange.y + focalY * (1f - newScale / oldScale)

                                event.changes.forEach { it.consume() }
                            } else if (pressedPointers.size == 1) {
                                val change = pressedPointers[0]
                                val panChange = change.position - change.previousPosition
                                val totalDragDist = (change.position - downPos).getDistance()

                                if (!pastTouchSlop && totalDragDist > 8f) {
                                    pastTouchSlop = true
                                    isTransforming = true
                                }

                                if (pastTouchSlop) {
                                    change.consume()
                                    if (activeDraggedNode != null && activeDraggedNode in allNodes.indices) {
                                        // Drag specific node smoothly
                                        val node = allNodes[activeDraggedNode]
                                        val deltaX = panChange.x / scale
                                        val deltaY = panChange.y / scale
                                        node.x += deltaX
                                        node.y += deltaY
                                        node.vx = deltaX * 0.45f
                                        node.vy = deltaY * 0.45f
                                        physicsIteration++
                                    } else {
                                        // Pan entire graph canvas
                                        offsetX += panChange.x
                                        offsetY += panChange.y
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        draggedNodeIndex = null

                        // Tap handling (when touch wasn't a drag or pinch)
                        val elapsed = System.currentTimeMillis() - downTime
                        if (!pastTouchSlop && !isTransforming && elapsed < 350L) {
                            if (activeDraggedNode != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedNodeIndex = activeDraggedNode
                            } else {
                                selectedNodeIndex = null
                            }
                        }
                    }
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

            // 1. Draw Directed Links (with Viewport Frustum Culling)
            for (edgeIndex in allEdges.indices) {
                val edge = allEdges[edgeIndex]
                val srcNode = allNodes[edge.sourceIndex]
                val dstNode = allNodes[edge.targetIndex]

                val sx = toScreenX(srcNode.x)
                val sy = toScreenY(srcNode.y)
                val dx = toScreenX(dstNode.x)
                val dy = toScreenY(dstNode.y)

                // Frustum culling: skip drawing if both ends are outside viewport bounds
                if ((sx < -40f && dx < -40f) || (sx > canvasWidth + 40f && dx > canvasWidth + 40f) ||
                    (sy < -40f && dy < -40f) || (sy > canvasHeight + 40f && dy > canvasHeight + 40f)) {
                    continue
                }

                val isConnectedToSelected = selectedNodeIndex != null &&
                        (edge.sourceIndex == selectedNodeIndex || edge.targetIndex == selectedNodeIndex)

                val edgeColor = when {
                    isConnectedToSelected -> themeAccent.copy(alpha = 0.95f)
                    selectedNodeIndex != null -> Color(0xFF2C2C2E).copy(alpha = 0.08f)
                    else -> srcNode.clusterColor.copy(alpha = 0.18f)
                }

                val strokeWidth = when {
                    isConnectedToSelected -> (2.2f * scale).coerceIn(1.8f, 5.0f)
                    else -> (0.85f * scale).coerceIn(0.6f, 2.5f)
                }

                // Base Link Line
                drawLine(
                    color = edgeColor,
                    start = Offset(sx, sy),
                    end = Offset(dx, dy),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // Traveling Energy Particle (Only animated for connected links to save GPU)
                if (isConnectedToSelected) {
                    val px = sx + (dx - sx) * pulseProgress
                    val py = sy + (dy - sy) * pulseProgress
                    drawCircle(
                        color = Color.White,
                        radius = (3.0f * scale).coerceIn(2.0f, 5.5f),
                        center = Offset(px, py)
                    )
                }
            }

            // 2. Draw Nodes (with Viewport Frustum Culling)
            for (nodeIndex in allNodes.indices) {
                val node = allNodes[nodeIndex]
                val sx = toScreenX(node.x)
                val sy = toScreenY(node.y)
                val nodeRadius = (node.radius * scale).coerceIn(3.5f, 24f)

                // Frustum culling: skip off-screen nodes
                if (sx + nodeRadius < -30f || sx - nodeRadius > canvasWidth + 30f ||
                    sy + nodeRadius < -30f || sy - nodeRadius > canvasHeight + 30f) {
                    continue
                }

                val isSelected = nodeIndex == selectedNodeIndex
                val isConnectedToSelected = selectedNeighbors.contains(nodeIndex)
                val isQueryMatch = searchQuery.isNotBlank() &&
                        node.note.displayTitle.contains(searchQuery, ignoreCase = true)

                val nodeAlpha = when {
                    isSelected || isQueryMatch -> 1.0f
                    isConnectedToSelected -> 0.95f
                    selectedNodeIndex != null -> 0.15f
                    else -> 0.88f
                }

                // Selection Glow Ring
                if (isSelected || isQueryMatch) {
                    drawCircle(
                        color = themeAccent.copy(alpha = 0.25f),
                        radius = nodeRadius + 10f * scale,
                        center = Offset(sx, sy)
                    )
                }

                // Node Body
                val baseColor = when {
                    isSelected || isQueryMatch -> themeAccent
                    isConnectedToSelected -> node.clusterColor
                    else -> node.clusterColor
                }

                drawCircle(
                    color = baseColor.copy(alpha = nodeAlpha),
                    radius = nodeRadius,
                    center = Offset(sx, sy)
                )

                // Node Outline (Hubs and selected nodes only to prevent visual clutter)
                if (node.isHub || isSelected) {
                    drawCircle(
                        color = Color.White.copy(alpha = if (isSelected) 0.9f else 0.35f * nodeAlpha),
                        radius = nodeRadius,
                        center = Offset(sx, sy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = (if (isSelected) 2.0f else 1.0f) * scale)
                    )
                }

                // Pinned indicator dot inside core
                if (node.isPinned) {
                    drawCircle(
                        color = Color.White,
                        radius = (2.5f * scale).coerceIn(1.8f, 5.0f),
                        center = Offset(sx, sy)
                    )
                }

                // Title Labels (Obsidian-grade spatial filtering: shows landmark hubs at overview, neighbors on select, and deep labels on zoom)
                val shouldDrawLabel = isSelected || isQueryMatch || isConnectedToSelected ||
                        (topLandmarkIds.contains(node.id) && scale >= 0.45f) ||
                        (node.isHub && scale >= 1.25f) ||
                        (scale >= 1.65f && node.degree >= 3) ||
                        (scale >= 2.3f)

                if (shouldDrawLabel) {
                    val label = node.note.displayTitle
                    val paint = if (isSelected || isQueryMatch) selectedLabelPaint else labelPaint
                    paint.textSize = (11.5.sp.toPx() * scale).coerceIn(12f, 28f)

                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        sx,
                        sy + nodeRadius + (12f * scale).coerceIn(10f, 22f),
                        paint
                    )
                }
            }
        }

        // Top Navigation Bar (Clean, Minimal, Professional)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
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
                        .padding(horizontal = 4.dp, vertical = 6.dp)
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

                // Center/Title: Professional, simple "Graph"
                Text(
                    text = "Graph",
                    style = MonolithTypography.headlineMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = primaryTextColor
                )

                // Right Actions: Search, Recenter, Vault Intelligence (i)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                            contentDescription = "Search",
                            tint = if (isSearchActive) themeAccent else primaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scale = defaultScale
                            offsetX = 0f
                            offsetY = 0f
                            selectedNodeIndex = null
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Recenter",
                            tint = primaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showIntelligenceSheet = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Vault Intelligence",
                            tint = themeAccent,
                            modifier = Modifier.size(21.dp)
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
                            Text("Search notes in graph...", fontSize = 13.5.sp, color = iOSTextTertiary)
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
        }

        // Selected Note Detail Float Card (Compact, Non-Intrusive Obsidian Capsule)
        val selectedNode = selectedNodeIndex?.let { if (it in allNodes.indices) allNodes[it] else null }
        AnimatedVisibility(
            visible = selectedNode != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (selectedNode != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = iOSCardBackground,
                    border = BorderStroke(0.5.dp, iOSDivider.copy(alpha = 0.5f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Cluster Color Dot + Title + Degree
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(selectedNode.clusterColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedNode.note.displayTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${selectedNode.degree} links • ${selectedNode.clusterName}",
                                    fontSize = 11.5.sp,
                                    color = iOSTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Right: Open Button + Dismiss [X] Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateToNote(selectedNode.note)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = themeAccent,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Open",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open Note",
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedNodeIndex = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Deselect",
                                    tint = iOSTextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Vault Intelligence Sheet ('i' button modal)
        if (showIntelligenceSheet) {
            GraphIntelligenceSheet(
                analytics = analytics,
                nodes = allNodes,
                onSelectHub = { hubNode ->
                    val matchIndex = allNodes.indexOfFirst { it.id == hubNode.id }
                    if (matchIndex != -1) {
                        selectedNodeIndex = matchIndex
                        // Auto-center camera on the selected hub
                        offsetX = -hubNode.x * scale
                        offsetY = -hubNode.y * scale
                    }
                },
                onDismiss = { showIntelligenceSheet = false }
            )
        }
    }
}
