package com.monolith.notes.ui.graph

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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

    // Build Graph Data using KnowledgeGraphEngine
    val graphBundle = remember(notes) {
        KnowledgeGraphEngine.buildGraph(notes, ClusterMode.FOLDER)
    }

    val allNodes = graphBundle.nodes
    val allEdges = graphBundle.edges
    val analytics = graphBundle.analytics

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

        val kRepulse = (24000f + 1400f * sqrt(nodeCount.toFloat())).coerceIn(22000f, 60000f)
        val kSpring = 0.042f
        val restLength = (130f + 20f * log2(nodeCount.coerceAtLeast(2).toFloat())).coerceIn(120f, 240f)
        val damping = 0.84f
        val centerForce = (0.016f / sqrt(nodeCount.coerceAtLeast(1).toFloat())).coerceIn(0.004f, 0.024f)

        withContext(Dispatchers.Default) {
            for (step in 0 until 180) {
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

                // Early exit if graph has settled into resting equilibrium (saves CPU & battery)
                if (step > 25 && energy < 0.06f && draggedNodeIndex == null) {
                    break
                }

                if (step % 2 == 0) {
                    withContext(Dispatchers.Main) {
                        physicsIteration = step
                    }
                    delay(12) // Smooth 60/120Hz convergence
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
                    isConnectedToSelected -> themeAccent
                    selectedNodeIndex != null -> Color(0xFF2C2C2E).copy(alpha = 0.20f)
                    else -> srcNode.clusterColor.copy(alpha = 0.30f)
                }

                val strokeWidth = when {
                    isConnectedToSelected -> 3.2f * scale
                    else -> 1.4f * scale
                }.coerceIn(1f, 6.5f)

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
                        radius = (3.5f * scale).coerceIn(2.5f, 6.5f),
                        center = Offset(px, py)
                    )
                }
            }

            // 2. Draw Nodes (with Viewport Frustum Culling)
            for (nodeIndex in allNodes.indices) {
                val node = allNodes[nodeIndex]
                val sx = toScreenX(node.x)
                val sy = toScreenY(node.y)
                val nodeRadius = (node.radius * scale).coerceIn(8f, 75f)

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
                    selectedNodeIndex != null -> 0.22f
                    else -> 0.88f
                }

                // Selection Glow Ring
                if (isSelected || isQueryMatch) {
                    drawCircle(
                        color = themeAccent.copy(alpha = 0.30f),
                        radius = nodeRadius + 14f * scale,
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

                // Node Outline
                drawCircle(
                    color = Color.White.copy(alpha = if (isSelected) 0.9f else 0.4f * nodeAlpha),
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

                // Title Labels (Obsidian-grade spatial filtering: shows core hubs at overview, neighbors on select, and deep labels on zoom)
                val shouldDrawLabel = isSelected || isQueryMatch || isConnectedToSelected ||
                        (node.isHub && scale >= 0.75f) ||
                        (scale >= 1.45f && node.degree >= 3) ||
                        (scale >= 2.1f)

                if (shouldDrawLabel) {
                    val label = node.note.displayTitle
                    val paint = if (isSelected || isQueryMatch) selectedLabelPaint else labelPaint
                    paint.textSize = (13.sp.toPx() * scale).coerceIn(16f, 36f)

                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        sx,
                        sy + nodeRadius + (16f * scale).coerceIn(12f, 26f),
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
                            scale = 1f
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

        // Selected Note Detail Float Card
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
                                    Text(
                                        text = "•",
                                        fontSize = 12.sp,
                                        color = iOSTextTertiary
                                    )
                                    Text(
                                        text = selectedNode.clusterName,
                                        fontSize = 12.sp,
                                        color = iOSTextSecondary
                                    )
                                }
                            }

                            // Open Note Button
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
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "Open",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open Note",
                                    modifier = Modifier.size(13.dp)
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
