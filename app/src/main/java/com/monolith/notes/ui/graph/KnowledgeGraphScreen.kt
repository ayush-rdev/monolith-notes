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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.monolith.notes.data.ai.ActiveNeuronCandidate
import com.monolith.notes.data.ai.GeminiApiClient
import com.monolith.notes.data.ai.NeuralQuerySynthesis
import com.monolith.notes.data.ai.VaultNeuronEngine
import com.monolith.notes.model.Note
import com.monolith.notes.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.*

/**
 * Obsidian-grade Personal Knowledge Graph (Galaxy Architecture).
 * Features unified touch processor (focal-centered pinch zoom & 1-finger node drag/pan),
 * zero-fluff professional top bar, in-place neural retrieval HUD, and proportional radiant neuron activation.
 */
@Composable
fun KnowledgeGraphScreen(
    notes: List<Note>,
    initialBundle: KnowledgeGraphBundle? = null,
    apiKey: String = "",
    aiModel: String = "gemini-2.0-flash",
    onConfigureApiKey: () -> Unit = {},
    onBack: () -> Unit,
    onNavigateToNote: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showIntelligenceSheet by remember { mutableStateOf(false) }
    var showAskNeuronsSheet by remember { mutableStateOf(false) }

    // In-Place Neural Retrieval Box & Synthesis States
    var showNeuralQueryBox by remember { mutableStateOf(false) }
    var neuralInputQuery by remember { mutableStateOf("") }
    var isNeuralSynthesizing by remember { mutableStateOf(false) }
    var neuralErrorMessage by remember { mutableStateOf<String?>(null) }
    val queryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showNeuralQueryBox) {
        if (showNeuralQueryBox) {
            delay(120)
            try {
                queryFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    var activeNeuronIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var neuralQueryText by remember { mutableStateOf("") }
    var neuralSynthesisResult by remember { mutableStateOf<NeuralQuerySynthesis?>(null) }
    var neuralActiveCandidates by remember { mutableStateOf<List<ActiveNeuronCandidate>>(emptyList()) }

    var selectedNodeIndex by remember { mutableStateOf<Int?>(null) }
    var draggedNodeIndex by remember { mutableStateOf<Int?>(null) }

    val selectionTransition by animateFloatAsState(
        targetValue = if (selectedNodeIndex != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "selectionDepthTransition"
    )
    val neuralFocusTransition by animateFloatAsState(
        targetValue = if (activeNeuronIds.isNotEmpty()) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "neuralFocusTransition"
    )

    // Build or reuse cached Graph Data from background engine
    val graphBundle = remember(notes, initialBundle) {
        if (initialBundle != null && initialBundle.nodes.size == notes.size) {
            initialBundle
        } else {
            KnowledgeGraphEngine.buildGraph(notes, ClusterMode.FOLDER)
        }
    }

    val allNodes = graphBundle.nodes
    val allEdges = graphBundle.edges
    val analytics = graphBundle.analytics

    val galaxyScale = graphBundle.initialGalaxyScale

    // Canvas Transformation State (Pan & Zoom) initialized directly to calibrated resting scale
    var scale by remember(graphBundle) { mutableFloatStateOf(galaxyScale) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

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
            // Smoothly converge remaining steps (already pre-stabilized off-screen)
            for (step in 40 until 90) {
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

                if (energy < 0.035f && draggedNodeIndex == null) {
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

    // Active Neurons (RAG query activated) indices for radiant synaptic rendering
    val activeNeuronIndices = remember(activeNeuronIds, allNodes) {
        if (activeNeuronIds.isEmpty()) emptySet<Int>()
        else allNodes.mapIndexedNotNull { idx, node -> if (node.id in activeNeuronIds) idx else null }.toSet()
    }

    // Traveling Energy Pulse Animation along Directed Links (Active when node selected or active neurons firing)
    val shouldAnimatePulse = selectedNodeIndex != null || activeNeuronIndices.isNotEmpty()
    val pulseProgress: Float = if (shouldAnimatePulse) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseProgress"
        )
        anim
    } else {
        0f
    }

    // Back button handling
    BackHandler {
        if (showNeuralQueryBox) {
            showNeuralQueryBox = false
        } else if (showIntelligenceSheet) {
            showIntelligenceSheet = false
        } else if (activeNeuronIds.isNotEmpty()) {
            activeNeuronIds = emptySet()
            neuralQueryText = ""
            neuralSynthesisResult = null
            neuralActiveCandidates = emptyList()
        } else if (selectedNodeIndex != null) {
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

            val screenXs = FloatArray(nodeCount)
            val screenYs = FloatArray(nodeCount)

            for (i in 0 until nodeCount) {
                val node = allNodes[i]
                screenXs[i] = centerX + offsetX + (node.x * scale)
                screenYs[i] = centerY + offsetY + (node.y * scale)
            }

            val activeScrim = maxOf(selectionTransition, neuralFocusTransition)

            // 1. Draw Background / Unselected Edges
            for (edgeIndex in allEdges.indices) {
                val edge = allEdges[edgeIndex]
                val isConnectedToSelected = selectedNodeIndex != null &&
                        (edge.sourceIndex == selectedNodeIndex || edge.targetIndex == selectedNodeIndex)

                // Skip connected edges: they will be drawn in Pass 4 on top of the defocus scrim
                if (isConnectedToSelected && selectionTransition > 0.05f) continue

                // Skip edges between active neurons: they will be drawn in Pass 4.5 on top of the defocus scrim
                val isBothActive = activeNeuronIndices.contains(edge.sourceIndex) && activeNeuronIndices.contains(edge.targetIndex)
                if (isBothActive && neuralFocusTransition > 0.05f) continue

                val srcNode = allNodes[edge.sourceIndex]
                val sx = screenXs[edge.sourceIndex]
                val sy = screenYs[edge.sourceIndex]
                val dx = screenXs[edge.targetIndex]
                val dy = screenYs[edge.targetIndex]

                if ((sx < -60f && dx < -60f) || (sx > canvasWidth + 60f && dx > canvasWidth + 60f) ||
                    (sy < -60f && dy < -60f) || (sy > canvasHeight + 60f && dy > canvasHeight + 60f)) {
                    continue
                }

                val edgeAlpha = 0.18f * (1f - 0.94f * activeScrim)
                if (edgeAlpha < 0.010f) continue

                val edgeColor = srcNode.clusterColor.copy(alpha = edgeAlpha)
                val strokeWidth = (0.85f * scale).coerceIn(0.6f, 2.5f)

                drawLine(
                    color = edgeColor,
                    start = Offset(sx, sy),
                    end = Offset(dx, dy),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // 2. Draw Background / Unselected Nodes
            for (nodeIndex in allNodes.indices) {
                val isSelected = nodeIndex == selectedNodeIndex
                val isConnectedToSelected = selectedNeighbors.contains(nodeIndex)
                val isActiveNeuron = activeNeuronIndices.contains(nodeIndex)

                // Skip selected and neighbor nodes: they will be drawn in Pass 5 on top of the defocus scrim
                if ((isSelected || isConnectedToSelected) && selectionTransition > 0.05f) continue

                // Skip active neurons: they will be drawn in Pass 4.5 on top of the defocus scrim
                if (isActiveNeuron && neuralFocusTransition > 0.05f) continue

                val node = allNodes[nodeIndex]
                val sx = screenXs[nodeIndex]
                val sy = screenYs[nodeIndex]
                val nodeRadius = (node.radius * scale).coerceIn(3.5f, 24f)

                if (sx + nodeRadius < -30f || sx - nodeRadius > canvasWidth + 30f ||
                    sy + nodeRadius < -30f || sy - nodeRadius > canvasHeight + 30f) {
                    continue
                }

                val isQueryMatch = searchQuery.isNotBlank() &&
                        node.note.displayTitle.contains(searchQuery, ignoreCase = true)

                val nodeAlpha = when {
                    isQueryMatch -> 1.0f
                    else -> 0.88f * (1f - 0.92f * activeScrim) + 0.06f * activeScrim
                }

                drawCircle(
                    color = node.clusterColor.copy(alpha = nodeAlpha),
                    radius = nodeRadius * (1f - 0.15f * selectionTransition),
                    center = Offset(sx, sy)
                )

                if (node.isPinned && activeScrim < 0.5f) {
                    drawCircle(
                        color = Color.White.copy(alpha = nodeAlpha),
                        radius = (2.5f * scale).coerceIn(1.8f, 5.0f),
                        center = Offset(sx, sy)
                    )
                }

                // Title Labels (Only drawn when nothing is selected, or for search query match)
                val shouldDrawLabel = isQueryMatch || (selectedNodeIndex == null && activeNeuronIndices.isEmpty() && (
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

            // 3. High-Performance Optical Defocus Depth Scrim
            if (activeScrim > 0.01f) {
                if (selectedNodeIndex != null && selectedNodeIndex in allNodes.indices) {
                    val selIdx = selectedNodeIndex!!
                    val selSx = screenXs[selIdx]
                    val selSy = screenYs[selIdx]
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                canvasBgColor.copy(alpha = 0.18f * activeScrim),
                                canvasBgColor.copy(alpha = 0.68f * activeScrim)
                            ),
                            center = Offset(selSx, selSy),
                            radius = (450f * scale).coerceIn(300f, 1400f)
                        )
                    )
                } else if (activeNeuronIndices.isNotEmpty()) {
                    val focalIdx = activeNeuronIndices.first()
                    val fSx = screenXs[focalIdx]
                    val fSy = screenYs[focalIdx]
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                canvasBgColor.copy(alpha = 0.15f * activeScrim),
                                canvasBgColor.copy(alpha = 0.75f * activeScrim)
                            ),
                            center = Offset(fSx, fSy),
                            radius = (550f * scale).coerceIn(350f, 1600f)
                        )
                    )
                } else {
                    drawRect(
                        color = canvasBgColor.copy(alpha = 0.55f * activeScrim)
                    )
                }
            }

            // 4. In-Focus Highlighted Directed Connections (Selected Node Links)
            if (selectedNodeIndex != null) {
                for (edgeIndex in allEdges.indices) {
                    val edge = allEdges[edgeIndex]
                    val isConnectedToSelected = edge.sourceIndex == selectedNodeIndex || edge.targetIndex == selectedNodeIndex
                    if (!isConnectedToSelected) continue

                    val sx = screenXs[edge.sourceIndex]
                    val sy = screenYs[edge.sourceIndex]
                    val dx = screenXs[edge.targetIndex]
                    val dy = screenYs[edge.targetIndex]

                    if ((sx < -60f && dx < -60f) || (sx > canvasWidth + 60f && dx > canvasWidth + 60f) ||
                        (sy < -60f && dy < -60f) || (sy > canvasHeight + 60f && dy > canvasHeight + 60f)) {
                        continue
                    }

                    val edgeColor = themeAccent.copy(alpha = 0.95f)
                    val strokeWidth = (2.2f * scale).coerceIn(1.8f, 5.0f)

                    drawLine(
                        color = edgeColor,
                        start = Offset(sx, sy),
                        end = Offset(dx, dy),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )

                    // Traveling Energy Particle along Link
                    val u = 1f - pulseProgress
                    val px = sx * u + dx * pulseProgress
                    val py = sy * u + dy * pulseProgress
                    drawCircle(
                        color = Color.White,
                        radius = (3.4f * scale).coerceIn(2.4f, 6.0f),
                        center = Offset(px, py)
                    )
                }
            }

            // 4.5. Active Neurons & Synaptic Tracts (Proportional Radiant Activation)
            if (activeNeuronIndices.isNotEmpty()) {
                val neuronPulse = (sin(pulseProgress * 2f * PI.toFloat()) * 0.5f + 0.5f)
                val maxScore = neuralActiveCandidates.maxOfOrNull { it.activationScore }?.coerceAtLeast(1f) ?: 1f
                val scoreMap = neuralActiveCandidates.associate { it.note.id to it.activationScore }

                // Synapses connecting active neurons
                for (edge in allEdges) {
                    val srcActive = activeNeuronIndices.contains(edge.sourceIndex)
                    val tgtActive = activeNeuronIndices.contains(edge.targetIndex)
                    if (srcActive && tgtActive) {
                        val sx = screenXs[edge.sourceIndex]
                        val sy = screenYs[edge.sourceIndex]
                        val dx = screenXs[edge.targetIndex]
                        val dy = screenYs[edge.targetIndex]

                        drawLine(
                            color = Color(0xFFA78BFA).copy(alpha = 0.85f * neuralFocusTransition),
                            start = Offset(sx, sy),
                            end = Offset(dx, dy),
                            strokeWidth = (2.2f * scale).coerceIn(1.5f, 4.5f),
                            cap = StrokeCap.Round
                        )

                        // Traveling Action Potential
                        val u = 1f - pulseProgress
                        val px = sx * u + dx * pulseProgress
                        val py = sy * u + dy * pulseProgress
                        drawCircle(
                            color = Color(0xFFE9D5FF),
                            radius = (4.0f * scale).coerceIn(2.5f, 7.0f),
                            center = Offset(px, py)
                        )
                    }
                }

                // Active Neurons: "the more relevant the more bright"
                for (activeIdx in activeNeuronIndices) {
                    if (activeIdx == selectedNodeIndex) continue
                    val node = allNodes[activeIdx]
                    val sx = screenXs[activeIdx]
                    val sy = screenYs[activeIdx]
                    val nodeRadius = (node.radius * scale).coerceIn(3.5f, 24f)
                    val score = scoreMap[node.id] ?: 0f
                    val normalized = (score / maxScore).coerceIn(0.20f, 1.0f)

                    // Outer Radiant Synaptic Halo (Scaled by relevance sigma)
                    val haloRadius = nodeRadius + (8f + 20f * normalized + 6f * neuronPulse) * scale
                    val haloAlpha = ((0.32f + 0.48f * normalized + 0.12f * neuronPulse) * neuralFocusTransition).coerceIn(0f, 1f)
                    drawCircle(
                        color = Color(0xFFA78BFA).copy(alpha = haloAlpha),
                        radius = haloRadius,
                        center = Offset(sx, sy)
                    )

                    // Core neon perimeter ring (thicker and brighter for higher relevance)
                    val ringWidth = ((1.8f + 2.0f * normalized) * scale).coerceIn(1.5f, 6f)
                    drawCircle(
                        color = Color(0xFFF3E8FF),
                        radius = nodeRadius + (2.0f * scale),
                        center = Offset(sx, sy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth)
                    )

                    // Core node body
                    drawCircle(
                        color = node.clusterColor,
                        radius = nodeRadius,
                        center = Offset(sx, sy)
                    )

                    // Prominent Title & Relevance Score Badge
                    val scoreStr = if (score > 0f) " [σ=${String.format(Locale.US, "%.1f", score)}]" else ""
                    val fullLabel = "${node.note.displayTitle}$scoreStr"
                    neighborLabelPaint.color = android.graphics.Color.rgb(233, 213, 255)
                    neighborLabelPaint.textSize = ((12.0f + 2.5f * normalized).sp.toPx() * scale).coerceIn(12f, 28f)
                    drawContext.canvas.nativeCanvas.drawText(
                        fullLabel,
                        sx,
                        sy + nodeRadius + (12f * scale).coerceIn(10f, 22f),
                        neighborLabelPaint
                    )
                    neighborLabelPaint.color = android.graphics.Color.WHITE
                }
            }

            // 5. In-Focus Highlighted Nodes (Selected Thought + Connected Neighbors)
            if (selectedNodeIndex != null) {
                // First draw connected neighbors
                for (neighborIndex in selectedNeighbors) {
                    val node = allNodes[neighborIndex]
                    val sx = screenXs[neighborIndex]
                    val sy = screenYs[neighborIndex]
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
                val selIdx = selectedNodeIndex!!
                val selNode = allNodes[selIdx]
                val ssx = screenXs[selIdx]
                val ssy = screenYs[selIdx]
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
                // "< Notes" Back Button + Neuron Count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        }
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = themeAccent,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Notes",
                        color = themeAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "•  ${allNodes.size} neurons",
                        fontFamily = FontFamily.Monospace,
                        color = iOSTextTertiary,
                        fontSize = 11.sp
                    )
                }

                // Right Actions: Query Neurons, Search, Recenter, Vault Intelligence (i)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF191724))
                            .border(0.5.dp, Color(0x33A78BFA), RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showNeuralQueryBox = true
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Ask Notes",
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ask",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) themeAccent else primaryTextColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scale = galaxyScale
                            offsetX = 0f
                            offsetY = 0f
                            selectedNodeIndex = null
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Recenter",
                            tint = primaryTextColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showIntelligenceSheet = true
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Vault Intelligence",
                            tint = themeAccent,
                            modifier = Modifier.size(18.dp)
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

        // Floating Prompt Pill (when no node is selected and search is inactive)
        // Floating Prompt Pill (Ultra-Minimalist Monospace / Obsidian Pill)
        AnimatedVisibility(
            visible = selectedNodeIndex == null && !isSearchActive && activeNeuronIds.isEmpty() && !showNeuralQueryBox,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(9f)
                .padding(bottom = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xF20F0E16))
                    .border(0.5.dp, Color(0x33A78BFA), RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showNeuralQueryBox = true
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Ask Notes...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFDDD6FE)
                    )
                }
            }
        }

        // Active Neural Retrieval HUD Card (Direct on Graph)
        AnimatedVisibility(
            visible = activeNeuronIds.isNotEmpty() && !showNeuralQueryBox,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(9f)
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xF80D0C14))
                    .border(0.5.dp, Color(0x44A78BFA), RoundedCornerShape(10.dp))
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Top Telemetry Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "\"$neuralQueryText\"",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF1F5F9),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x3334D399))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${activeNeuronIds.size} matched",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6EE7B7),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isNeuralSynthesizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    color = Color(0xFFA78BFA),
                                    strokeWidth = 1.5.dp
                                )
                                Text(
                                    text = "Searching...",
                                    fontSize = 10.sp,
                                    color = Color(0xFFC4B5FD)
                                )
                            } else if (neuralSynthesisResult != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF261D3A))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showAskNeuronsSheet = true
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "View Answer ↗",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA78BFA)
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        activeNeuronIds = emptySet()
                                        neuralQueryText = ""
                                        neuralSynthesisResult = null
                                        neuralActiveCandidates = emptyList()
                                    }
                            )
                        }
                    }

                    // Matching Notes (Horizontal chips: tap to center on note)
                    if (neuralActiveCandidates.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            neuralActiveCandidates.forEach { candidate ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF161422))
                                        .border(0.5.dp, Color(0x33A78BFA), RoundedCornerShape(6.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val matchIdx = allNodes.indexOfFirst { it.id == candidate.note.id }
                                            if (matchIdx != -1) {
                                                selectedNodeIndex = matchIdx
                                                val targetNode = allNodes[matchIdx]
                                                offsetX = -targetNode.x * scale
                                                offsetY = -targetNode.y * scale
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFA78BFA))
                                        )
                                        Text(
                                            text = candidate.note.displayTitle,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFDDD6FE)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // In-Place Spotlight-Style Neural Query Box (Direct on Graph)
        AnimatedVisibility(
            visible = showNeuralQueryBox,
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 0.95f),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(15f)
        ) {
            fun submitNeuralQuery(prompt: String) {
                val trimmed = prompt.trim()
                if (trimmed.isBlank() || isNeuralSynthesizing) return
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showNeuralQueryBox = false
                neuralQueryText = trimmed
                isNeuralSynthesizing = true
                neuralErrorMessage = null
                neuralSynthesisResult = null

                val candidates = VaultNeuronEngine.activateNeurons(trimmed, notes, maxNeurons = 7)
                neuralActiveCandidates = candidates
                activeNeuronIds = candidates.map { it.note.id }.toSet()

                // Auto-center camera on the top active seed
                if (candidates.isNotEmpty()) {
                    val topNode = allNodes.firstOrNull { it.id == candidates.first().note.id }
                    if (topNode != null) {
                        offsetX = -topNode.x * scale
                        offsetY = -topNode.y * scale
                    }
                }

                coroutineScope.launch {
                    val result = GeminiApiClient.queryVaultNeurons(
                        apiKey = apiKey,
                        model = aiModel,
                        query = trimmed,
                        activeNeurons = candidates,
                        totalVaultNotesCount = notes.size
                    )
                    isNeuralSynthesizing = false
                    if (result.isSuccess) {
                        neuralSynthesisResult = result.getOrNull()
                    } else {
                        neuralErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Query synthesis failed."
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.70f))
                    .clickable { showNeuralQueryBox = false },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xF80E0D16))
                        .border(0.5.dp, Color(0x44A78BFA), RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* absorb taps on dialog body */ }
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF261D3A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFA78BFA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Search Notes",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Answers grounded strictly in your notes",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showNeuralQueryBox = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF161422))
                                .border(0.5.dp, Color(0x33A78BFA), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = neuralInputQuery,
                                onValueChange = { neuralInputQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(Color(0xFFA78BFA)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    submitNeuralQuery(neuralInputQuery)
                                }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(queryFocusRequester),
                                decorationBox = { innerTextField ->
                                    if (neuralInputQuery.isEmpty()) {
                                        Text(
                                            text = "Ask anything about your notes...",
                                            fontSize = 12.5.sp,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            if (neuralInputQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { neuralInputQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (neuralInputQuery.isNotBlank() && !isNeuralSynthesizing) Color(0xFF7C3AED)
                                        else Color(0xFF1E1A29)
                                    )
                                    .clickable(enabled = neuralInputQuery.isNotBlank() && !isNeuralSynthesizing) {
                                        submitNeuralQuery(neuralInputQuery)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Search",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (neuralInputQuery.isNotBlank()) Color.White else Color(0xFF64748B)
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Run",
                                        tint = if (neuralInputQuery.isNotBlank()) Color.White else Color(0xFF64748B),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
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
                notes = notes,
                apiKey = apiKey,
                aiModel = aiModel,
                onConfigureApiKey = onConfigureApiKey,
                onOpenAskNeurons = {
                    showIntelligenceSheet = false
                    showAskNeuronsSheet = true
                },
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

        // Ask Vault Neurons Sheet (Strict Grounded RAG)
        if (showAskNeuronsSheet) {
            AskNeuronsSheet(
                notes = notes,
                apiKey = apiKey,
                aiModel = aiModel,
                initialQueryText = neuralQueryText,
                initialSynthesis = neuralSynthesisResult,
                initialCandidates = neuralActiveCandidates,
                onSynthesisChanged = { query, candidates, synthesis ->
                    neuralQueryText = query
                    neuralActiveCandidates = candidates
                    neuralSynthesisResult = synthesis
                },
                onConfigureApiKey = onConfigureApiKey,
                onLocateNeuronInGraph = { noteId, _ ->
                    val matchIndex = allNodes.indexOfFirst { it.id == noteId }
                    if (matchIndex != -1) {
                        selectedNodeIndex = matchIndex
                        val target = allNodes[matchIndex]
                        offsetX = -target.x * scale
                        offsetY = -target.y * scale
                        showAskNeuronsSheet = false
                    }
                },
                onOpenNote = { note ->
                    showAskNeuronsSheet = false
                    onNavigateToNote(note)
                },
                onActiveNeuronsUpdated = { activeIds ->
                    activeNeuronIds = activeIds.toSet()
                },
                onDismiss = { showAskNeuronsSheet = false }
            )
        }
    }
}
