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
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.zIndex
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

    var graphMode by remember { mutableStateOf(GraphMode.BRAIN) }
    val transitionProgress by animateFloatAsState(
        targetValue = if (graphMode == GraphMode.BRAIN) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "graphModeTransition"
    )
    val selectionTransition by animateFloatAsState(
        targetValue = if (selectedNodeIndex != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "selectionDepthTransition"
    )

    var galaxyScale by remember { mutableFloatStateOf(0.55f) }
    var brainScale by remember { mutableFloatStateOf(0.85f) }

    // Canvas Transformation State (Pan & Zoom)
    var scale by remember { mutableFloatStateOf(0.85f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var defaultScale by remember { mutableFloatStateOf(0.85f) }

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
                val autoFitGalaxy = minOf(950f / spanX, 1600f / spanY).coerceIn(0.25f, 0.85f)
                val autoFitBrain = 0.82f

                withContext(Dispatchers.Main) {
                    galaxyScale = autoFitGalaxy
                    brainScale = autoFitBrain
                    defaultScale = if (graphMode == GraphMode.BRAIN) autoFitBrain else autoFitGalaxy
                    scale = defaultScale
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
    val canvasBgColor = iOSBackground

    // Text Paint for Canvas Node Labels (Gentle on OLED screens, zero glare)
    val labelPaint = remember {
        Paint().apply {
            color = Color(0xFFC7C7CC).toArgb() // Slightly less white, comfortable soft silver
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 2f, Color.Black.copy(alpha = 0.7f).toArgb())
        }
    }

    val neighborLabelPaint = remember {
        Paint().apply {
            color = Color(0xFFE5E5EA).toArgb() // Crisp soft white for direct connections
            textSize = 29f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(5f, 0f, 2f, Color.Black.toArgb())
        }
    }

    val selectedLabelPaint = remember(themeAccent) {
        Paint().apply {
            color = themeAccent.toArgb() // Radiant amber gold for selected thought
            textSize = 32f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 2f, Color.Black.toArgb())
        }
    }

    val fiberReusablePath = remember { Path() }

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
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position
                        var isTransforming = false
                        var activeDraggedNode: Int? = null

                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val centerX = canvasW / 2f
                        val centerY = canvasH / 2f

                        // Discard touches starting within Top Navigation Bar or Bottom Capsule zone
                        val topBarThreshold = if (isSearchActive) 280f else 190f
                        val bottomCapsuleThreshold = canvasH - 220f
                        if (downPos.y < topBarThreshold || (selectedNodeIndex != null && downPos.y > bottomCapsuleThreshold)) {
                            return@awaitEachGesture
                        }

                        // Convert touch coordinate to graph space
                        val currentScale = scale
                        val currentOffX = offsetX
                        val currentOffY = offsetY
                        val graphDownX = (downPos.x - centerX - currentOffX) / currentScale
                        val graphDownY = (downPos.y - centerY - currentOffY) / currentScale

                        // Hit-test nodes (with a comfortable 26px touch target margin)
                        for (i in allNodes.indices) {
                            val node = allNodes[i]
                            val nx = node.x * (1f - transitionProgress) + node.brainX * transitionProgress
                            val ny = node.y * (1f - transitionProgress) + node.brainY * transitionProgress
                            val dx = nx - graphDownX
                            val dy = ny - graphDownY
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
                                        if (graphMode == GraphMode.BRAIN) {
                                            node.brainX += deltaX
                                            node.brainY += deltaY
                                        } else {
                                            node.x += deltaX
                                            node.y += deltaY
                                            node.vx = deltaX * 0.45f
                                            node.vy = deltaY * 0.45f
                                        }
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

            fun getNodeX(node: GraphNode): Float =
                node.x * (1f - transitionProgress) + node.brainX * transitionProgress

            fun getNodeY(node: GraphNode): Float =
                node.y * (1f - transitionProgress) + node.brainY * transitionProgress

            fun toScreenX(gx: Float): Float = centerX + offsetX + (gx * scale)
            fun toScreenY(gy: Float): Float = centerY + offsetY + (gy * scale)

            // 0. Anatomical Cortical Silhouette (Superior View)
            if (transitionProgress > 0.05f) {
                val contourPath = BrainGraphEngine.buildAnatomicalContourPath(
                    centerX = centerX + offsetX,
                    centerY = centerY + offsetY,
                    scale = scale
                )
                val contourAlpha = (0.16f * transitionProgress * (1f - 0.70f * selectionTransition)).coerceAtLeast(0.04f)
                drawPath(
                    path = contourPath,
                    color = Color(0xFFE5E5EA).copy(alpha = contourAlpha),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = (1.5f * scale).coerceIn(1.0f, 3.0f)
                    )
                )
            }

            // 1. Draw Background / Unselected Edges
            for (edgeIndex in allEdges.indices) {
                val edge = allEdges[edgeIndex]
                val isConnectedToSelected = selectedNodeIndex != null &&
                        (edge.sourceIndex == selectedNodeIndex || edge.targetIndex == selectedNodeIndex)

                // Skip connected edges: they will be drawn in Pass 4 on top of the defocus scrim
                if (isConnectedToSelected && selectionTransition > 0.05f) continue

                val srcNode = allNodes[edge.sourceIndex]
                val dstNode = allNodes[edge.targetIndex]

                val sx = toScreenX(getNodeX(srcNode))
                val sy = toScreenY(getNodeY(srcNode))
                val dx = toScreenX(getNodeX(dstNode))
                val dy = toScreenY(getNodeY(dstNode))

                if ((sx < -60f && dx < -60f) || (sx > canvasWidth + 60f && dx > canvasWidth + 60f) ||
                    (sy < -60f && dy < -60f) || (sy > canvasHeight + 60f && dy > canvasHeight + 60f)) {
                    continue
                }

                val edgeAlpha = (if (transitionProgress > 0.5f) 0.25f else 0.18f) * (1f - 0.90f * selectionTransition)
                if (edgeAlpha < 0.015f) continue

                val edgeColor = srcNode.clusterColor.copy(alpha = edgeAlpha)
                val strokeWidth = (0.85f * scale).coerceIn(0.6f, 2.5f)

                val midX = (sx + dx) * 0.5f
                val midY = (sy + dy) * 0.5f
                val ctrlX: Float
                val ctrlY: Float

                if (transitionProgress > 0.05f) {
                    val brainCtrl = BrainGraphEngine.computeFiberControlPoint(
                        sx = sx, sy = sy,
                        dx = dx, dy = dy,
                        centerX = centerX + offsetX,
                        centerY = centerY + offsetY,
                        scale = scale
                    )
                    ctrlX = midX * (1f - transitionProgress) + brainCtrl.x * transitionProgress
                    ctrlY = midY * (1f - transitionProgress) + brainCtrl.y * transitionProgress

                    fiberReusablePath.reset()
                    fiberReusablePath.moveTo(sx, sy)
                    fiberReusablePath.quadraticBezierTo(ctrlX, ctrlY, dx, dy)
                    drawPath(
                        path = fiberReusablePath,
                        color = edgeColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    )
                } else {
                    ctrlX = midX
                    ctrlY = midY
                    drawLine(
                        color = edgeColor,
                        start = Offset(sx, sy),
                        end = Offset(dx, dy),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                // Ambient pulse when nothing is selected
                if (selectedNodeIndex == null && (edgeIndex % 9 == 0) && transitionProgress > 0.5f) {
                    val u = 1f - pulseProgress
                    val px = u * u * sx + 2f * u * pulseProgress * ctrlX + pulseProgress * pulseProgress * dx
                    val py = u * u * sy + 2f * u * pulseProgress * ctrlY + pulseProgress * pulseProgress * dy
                    val alphaEnv = sin(pulseProgress * Math.PI.toFloat()).coerceIn(0.2f, 1f)
                    drawCircle(
                        color = themeAccent.copy(alpha = 0.8f * alphaEnv),
                        radius = (2.2f * scale).coerceIn(2.0f, 5.5f),
                        center = Offset(px, py)
                    )
                }
            }

            // 2. Draw Background / Unselected Nodes
            for (nodeIndex in allNodes.indices) {
                val isSelected = nodeIndex == selectedNodeIndex
                val isConnectedToSelected = selectedNeighbors.contains(nodeIndex)

                // Skip selected and neighbor nodes: they will be drawn in Pass 5 on top of the defocus scrim
                if ((isSelected || isConnectedToSelected) && selectionTransition > 0.05f) continue

                val node = allNodes[nodeIndex]
                val nx = getNodeX(node)
                val ny = getNodeY(node)
                val sx = toScreenX(nx)
                val sy = toScreenY(ny)
                val nodeRadius = (node.radius * scale).coerceIn(3.5f, 24f)

                if (sx + nodeRadius < -30f || sx - nodeRadius > canvasWidth + 30f ||
                    sy + nodeRadius < -30f || sy - nodeRadius > canvasHeight + 30f) {
                    continue
                }

                val isQueryMatch = searchQuery.isNotBlank() &&
                        node.note.displayTitle.contains(searchQuery, ignoreCase = true)

                val nodeAlpha = when {
                    isQueryMatch -> 1.0f
                    else -> 0.88f * (1f - 0.90f * selectionTransition) + 0.08f * selectionTransition
                }

                drawCircle(
                    color = node.clusterColor.copy(alpha = nodeAlpha),
                    radius = nodeRadius * (1f - 0.15f * selectionTransition),
                    center = Offset(sx, sy)
                )

                if (node.isPinned && selectionTransition < 0.5f) {
                    drawCircle(
                        color = Color.White.copy(alpha = nodeAlpha),
                        radius = (2.5f * scale).coerceIn(1.8f, 5.0f),
                        center = Offset(sx, sy)
                    )
                }

                // Title Labels (Only drawn when nothing is selected, or for search query match)
                val shouldDrawLabel = isQueryMatch || (selectedNodeIndex == null && (
                        (topLandmarkIds.contains(node.id) && scale >= 0.45f) ||
                        (node.isHub && scale >= 1.25f) ||
                        (scale >= 1.65f && node.degree >= 3) ||
                        (scale >= 2.3f)
                ))

                if (shouldDrawLabel) {
                    val label = node.note.displayTitle
                    val paint = if (isQueryMatch) selectedLabelPaint else labelPaint
                    paint.textSize = (11.5.sp.toPx() * scale).coerceIn(12f, 28f)

                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        sx,
                        sy + nodeRadius + (12f * scale).coerceIn(10f, 22f),
                        paint
                    )
                }
            }

            // 3. High-Performance 18% Atmospheric Defocus Depth Scrim
            if (selectionTransition > 0.01f) {
                val selNode = selectedNodeIndex?.let { if (it in allNodes.indices) allNodes[it] else null }
                if (selNode != null) {
                    val selSx = toScreenX(getNodeX(selNode))
                    val selSy = toScreenY(getNodeY(selNode))
                    // Radial optical defocus: soft 18% veil at focal center, deepening to 62% towards borders
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                canvasBgColor.copy(alpha = 0.18f * selectionTransition),
                                canvasBgColor.copy(alpha = 0.62f * selectionTransition)
                            ),
                            center = Offset(selSx, selSy),
                            radius = (450f * scale).coerceIn(300f, 1400f)
                        )
                    )
                } else {
                    drawRect(
                        color = canvasBgColor.copy(alpha = 0.50f * selectionTransition)
                    )
                }
            }

            // 4. In-Focus Highlighted Directed Connections (Selected Node Links)
            if (selectedNodeIndex != null) {
                for (edgeIndex in allEdges.indices) {
                    val edge = allEdges[edgeIndex]
                    val isConnectedToSelected = edge.sourceIndex == selectedNodeIndex || edge.targetIndex == selectedNodeIndex
                    if (!isConnectedToSelected) continue

                    val srcNode = allNodes[edge.sourceIndex]
                    val dstNode = allNodes[edge.targetIndex]

                    val sx = toScreenX(getNodeX(srcNode))
                    val sy = toScreenY(getNodeY(srcNode))
                    val dx = toScreenX(getNodeX(dstNode))
                    val dy = toScreenY(getNodeY(dstNode))

                    if ((sx < -60f && dx < -60f) || (sx > canvasWidth + 60f && dx > canvasWidth + 60f) ||
                        (sy < -60f && dy < -60f) || (sy > canvasHeight + 60f && dy > canvasHeight + 60f)) {
                        continue
                    }

                    val edgeColor = themeAccent.copy(alpha = 0.95f)
                    val strokeWidth = (2.4f * scale).coerceIn(2.0f, 5.5f)

                    val midX = (sx + dx) * 0.5f
                    val midY = (sy + dy) * 0.5f
                    val ctrlX: Float
                    val ctrlY: Float

                    if (transitionProgress > 0.05f) {
                        val brainCtrl = BrainGraphEngine.computeFiberControlPoint(
                            sx = sx, sy = sy,
                            dx = dx, dy = dy,
                            centerX = centerX + offsetX,
                            centerY = centerY + offsetY,
                            scale = scale
                        )
                        ctrlX = midX * (1f - transitionProgress) + brainCtrl.x * transitionProgress
                        ctrlY = midY * (1f - transitionProgress) + brainCtrl.y * transitionProgress

                        fiberReusablePath.reset()
                        fiberReusablePath.moveTo(sx, sy)
                        fiberReusablePath.quadraticBezierTo(ctrlX, ctrlY, dx, dy)
                        drawPath(
                            path = fiberReusablePath,
                            color = edgeColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        )
                    } else {
                        ctrlX = midX
                        ctrlY = midY
                        drawLine(
                            color = edgeColor,
                            start = Offset(sx, sy),
                            end = Offset(dx, dy),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }

                    // Traveling Energy Particle / Action Potential along Curved Neural Tract
                    val u = 1f - pulseProgress
                    val px = u * u * sx + 2f * u * pulseProgress * ctrlX + pulseProgress * pulseProgress * dx
                    val py = u * u * sy + 2f * u * pulseProgress * ctrlY + pulseProgress * pulseProgress * dy
                    drawCircle(
                        color = Color.White,
                        radius = (3.4f * scale).coerceIn(2.4f, 6.0f),
                        center = Offset(px, py)
                    )
                }
            }

            // 5. In-Focus Highlighted Nodes (Selected Thought + Connected Neighbors)
            if (selectedNodeIndex != null) {
                // First draw connected neighbors
                for (neighborIndex in selectedNeighbors) {
                    val node = allNodes[neighborIndex]
                    val nx = getNodeX(node)
                    val ny = getNodeY(node)
                    val sx = toScreenX(nx)
                    val sy = toScreenY(ny)
                    val nodeRadius = (node.radius * scale).coerceIn(3.5f, 24f)

                    // Neighbor Aura / Glow
                    drawCircle(
                        color = node.clusterColor.copy(alpha = 0.22f),
                        radius = nodeRadius + 5f * scale,
                        center = Offset(sx, sy)
                    )

                    // Neighbor Body
                    drawCircle(
                        color = node.clusterColor,
                        radius = nodeRadius,
                        center = Offset(sx, sy)
                    )

                    // Subtle Neighbor Outline
                    drawCircle(
                        color = Color.White.copy(alpha = 0.50f),
                        radius = nodeRadius,
                        center = Offset(sx, sy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * scale)
                    )

                    // Neighbor Title Label in soft clean white (readable, focused)
                    val label = node.note.displayTitle
                    neighborLabelPaint.textSize = (11.5.sp.toPx() * scale).coerceIn(12f, 28f)
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        sx,
                        sy + nodeRadius + (12f * scale).coerceIn(10f, 22f),
                        neighborLabelPaint
                    )
                }

                // Finally draw Selected Node on the highest layer
                val selNode = allNodes[selectedNodeIndex!!]
                val snx = getNodeX(selNode)
                val sny = getNodeY(selNode)
                val ssx = toScreenX(snx)
                val ssy = toScreenY(sny)
                val sRadius = (selNode.radius * scale).coerceIn(3.5f, 24f)

                // Large Selection Pulse Ring
                drawCircle(
                    color = themeAccent.copy(alpha = 0.28f),
                    radius = sRadius + 12f * scale,
                    center = Offset(ssx, ssy)
                )

                // Selected Node Body
                drawCircle(
                    color = themeAccent,
                    radius = sRadius,
                    center = Offset(ssx, ssy)
                )

                // Crisp Double Outline
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f),
                    radius = sRadius,
                    center = Offset(ssx, ssy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f * scale)
                )

                // Selected Title Label in Radiant Accent Amber
                val selLabel = selNode.note.displayTitle
                selectedLabelPaint.textSize = (12.5.sp.toPx() * scale).coerceIn(13f, 30f)
                drawContext.canvas.nativeCanvas.drawText(
                    selLabel,
                    ssx,
                    ssy + sRadius + (13f * scale).coerceIn(11f, 24f),
                    selectedLabelPaint
                )
            }
        }

        // Top Navigation Bar (Clean, Minimal, Professional)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
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

                // Segmented Mode Selector: Brain 🧠 | Galaxy 🌌
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(iOSCardBackground)
                        .border(0.5.dp, iOSDivider.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(2.dp)
                ) {
                    // Brain Tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (graphMode == GraphMode.BRAIN) themeAccent.copy(alpha = 0.18f)
                                else Color.Transparent
                            )
                            .clickable {
                                if (graphMode != GraphMode.BRAIN) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    graphMode = GraphMode.BRAIN
                                    scale = brainScale
                                    offsetX = 0f
                                    offsetY = 0f
                                    selectedNodeIndex = null
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🧠",
                                fontSize = 11.5.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Brain",
                                fontSize = 12.sp,
                                fontWeight = if (graphMode == GraphMode.BRAIN) FontWeight.Bold else FontWeight.Medium,
                                color = if (graphMode == GraphMode.BRAIN) themeAccent else iOSTextSecondary
                            )
                        }
                    }

                    // Galaxy Tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (graphMode == GraphMode.GALAXY) themeAccent.copy(alpha = 0.18f)
                                else Color.Transparent
                            )
                            .clickable {
                                if (graphMode != GraphMode.GALAXY) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    graphMode = GraphMode.GALAXY
                                    scale = galaxyScale
                                    offsetX = 0f
                                    offsetY = 0f
                                    selectedNodeIndex = null
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🌌",
                                fontSize = 11.5.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Galaxy",
                                fontSize = 12.sp,
                                fontWeight = if (graphMode == GraphMode.GALAXY) FontWeight.Bold else FontWeight.Medium,
                                color = if (graphMode == GraphMode.GALAXY) themeAccent else iOSTextSecondary
                            )
                        }
                    }
                }

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
                            scale = if (graphMode == GraphMode.BRAIN) brainScale else galaxyScale
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
                                    val targetX = if (graphMode == GraphMode.BRAIN) matchNode.brainX else matchNode.x
                                    val targetY = if (graphMode == GraphMode.BRAIN) matchNode.brainY else matchNode.y
                                    offsetX = -targetX * scale
                                    offsetY = -targetY * scale
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
                .zIndex(10f)
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
                        // Left: Cluster Color Dot + Title + Degree / Lobe
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
                                    text = if (graphMode == GraphMode.BRAIN) {
                                        if (selectedNode.degree == 0) "Orphan Neuron • ${selectedNode.lobe.shortName}"
                                        else "${selectedNode.degree} links • ${selectedNode.lobe.shortName} • ${selectedNode.clusterName}"
                                    } else {
                                        "${selectedNode.degree} links • ${selectedNode.clusterName}"
                                    },
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
                        val hubX = if (graphMode == GraphMode.BRAIN) hubNode.brainX else hubNode.x
                        val hubY = if (graphMode == GraphMode.BRAIN) hubNode.brainY else hubNode.y
                        offsetX = -hubX * scale
                        offsetY = -hubY * scale
                    }
                },
                onDismiss = { showIntelligenceSheet = false }
            )
        }
    }
}

enum class GraphMode {
    BRAIN,
    GALAXY
}
