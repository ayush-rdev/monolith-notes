package com.monolith.notes.ui.graph

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.model.Note
import com.monolith.notes.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.*

data class GraphNode(
    val id: String,
    val note: Note,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val radius: Float,
    val degree: Int
)

data class GraphEdge(
    val sourceIndex: Int,
    val targetIndex: Int
)

@Composable
fun KnowledgeGraphScreen(
    notes: List<Note>,
    onBack: () -> Unit,
    onNavigateToNote: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedNodeIndex by remember { mutableStateOf<Int?>(null) }

    // Canvas Transformation State (Pan & Zoom)
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Build Graph Nodes & Edges from Notes
    val graphData = remember(notes) {
        val count = notes.size
        if (count == 0) return@remember Pair(emptyList<GraphNode>(), emptyList<GraphEdge>())

        // Map note titles and filenames to index
        val noteTitleMap = mutableMapOf<String, Int>()
        notes.forEachIndexed { index, note ->
            noteTitleMap[note.displayTitle.lowercase().trim()] = index
            note.file?.nameWithoutExtension?.let {
                noteTitleMap[it.lowercase().trim()] = index
            }
        }

        // Build edges
        val edgeSet = mutableSetOf<Pair<Int, Int>>()
        val degrees = IntArray(count)

        notes.forEachIndexed { srcIndex, note ->
            for (outlink in note.outlinks) {
                val targetIndex = noteTitleMap[outlink.lowercase().trim()]
                if (targetIndex != null && targetIndex != srcIndex) {
                    val edgeKey = if (srcIndex < targetIndex) Pair(srcIndex, targetIndex) else Pair(targetIndex, srcIndex)
                    if (edgeSet.add(edgeKey)) {
                        degrees[srcIndex]++
                        degrees[targetIndex]++
                    }
                }
            }
        }

        // Initial positions arranged in a golden spiral
        val goldenAngle = 2.3999632f
        val distanceStep = 110f
        val nodesList = ArrayList<GraphNode>(count)

        for (i in 0 until count) {
            val dist = sqrt(i.toFloat() + 1f) * distanceStep
            val theta = i * goldenAngle
            val initX = dist * cos(theta)
            val initY = dist * sin(theta)
            val deg = degrees[i]
            val r = (10f + min(deg * 3.5f, 24f))
            nodesList.add(
                GraphNode(
                    id = notes[i].id,
                    note = notes[i],
                    x = initX,
                    y = initY,
                    radius = r,
                    degree = deg
                )
            )
        }

        val edgesList = edgeSet.map { GraphEdge(it.first, it.second) }
        Pair(nodesList, edgesList)
    }

    val nodes = graphData.first
    val edges = graphData.second
    val selectedNeighbors = remember(selectedNodeIndex, edges) {
        val target = selectedNodeIndex ?: return@remember emptySet<Int>()
        val set = HashSet<Int>()
        for (edge in edges) {
            if (edge.sourceIndex == target) set.add(edge.targetIndex)
            else if (edge.targetIndex == target) set.add(edge.sourceIndex)
        }
        set
    }

    // 120Hz Physics Simulation Loop
    var iteration by remember { mutableIntStateOf(0) }
    LaunchedEffect(graphData) {
        if (nodes.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            // Run force simulation
            val kRepulse = 38000f
            val kSpring = 0.045f
            val restLength = 160f
            val damping = 0.85f
            val centerForce = 0.012f

            for (step in 0 until 180) {
                // 1. Repulsion between all pairs
                for (i in 0 until nodes.size) {
                    val nodeA = nodes[i]
                    for (j in (i + 1) until nodes.size) {
                        val nodeB = nodes[j]
                        val dx = nodeB.x - nodeA.x
                        val dy = nodeB.y - nodeA.y
                        val distSq = dx * dx + dy * dy + 100f
                        val dist = sqrt(distSq)
                        val force = kRepulse / distSq
                        val fx = (dx / dist) * force
                        val fy = (dy / dist) * force

                        nodeA.vx -= fx
                        nodeA.vy -= fy
                        nodeB.vx += fx
                        nodeB.vy += fy
                    }
                }

                // 2. Spring attraction along edges
                for (edge in edges) {
                    val nodeA = nodes[edge.sourceIndex]
                    val nodeB = nodes[edge.targetIndex]
                    val dx = nodeB.x - nodeA.x
                    val dy = nodeB.y - nodeA.y
                    val dist = max(sqrt(dx * dx + dy * dy), 1f)
                    val displacement = dist - restLength
                    val force = displacement * kSpring
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force

                    nodeA.vx += fx
                    nodeA.vy += fy
                    nodeB.vx -= fx
                    nodeB.vy -= fy
                }

                // 3. Centering force & update positions
                for (node in nodes) {
                    node.vx -= node.x * centerForce
                    node.vy -= node.y * centerForce
                    node.vx *= damping
                    node.vy *= damping
                    node.x += node.vx
                    node.y += node.vy
                }

                if (step % 2 == 0) {
                    withContext(Dispatchers.Main) {
                        iteration = step
                    }
                    delay(12) // ~80-120fps fluid convergence
                }
            }
            withContext(Dispatchers.Main) {
                iteration++
            }
        }
    }

    // Back button handling
    BackHandler {
        onBack()
    }

    // Text Paint for Node Labels on Canvas
    val labelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 32f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
        }
    }

    val accentColor = iOSYellow

    val selectedLabelPaint = remember(accentColor) {
        Paint().apply {
            color = accentColor.toArgb()
            textSize = 36f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(8f, 0f, 3f, android.graphics.Color.BLACK)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(iOSBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Main Visual Graph Canvas
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.25f, 3.5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .pointerInput(nodes, scale, offsetX, offsetY) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f

                        val graphX = (tapOffset.x - centerX - offsetX) / scale
                        val graphY = (tapOffset.y - centerY - offsetY) / scale

                        // Hit test nodes
                        var closestIndex: Int? = null
                        var minDistance = Float.MAX_VALUE

                        for (i in 0 until nodes.size) {
                            val node = nodes[i]
                            val dx = node.x - graphX
                            val dy = node.y - graphY
                            val dist = sqrt(dx * dx + dy * dy)
                            if (dist < (node.radius + 20f) && dist < minDistance) {
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
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f
            val centerY = canvasHeight / 2f

            // Read iteration state to trigger recomposition during physics convergence
            @Suppress("UNUSED_VARIABLE")
            val currentIter = iteration

            // Helper lambda to project graph coordinates to screen coordinates
            fun toScreenX(gx: Float): Float = centerX + offsetX + (gx * scale)
            fun toScreenY(gy: Float): Float = centerY + offsetY + (gy * scale)

            // 1. Draw Edges
            for (edge in edges) {
                val srcNode = nodes[edge.sourceIndex]
                val dstNode = nodes[edge.targetIndex]

                val isConnectedToSelected = selectedNodeIndex != null &&
                        (edge.sourceIndex == selectedNodeIndex || edge.targetIndex == selectedNodeIndex)

                val edgeColor = when {
                    isConnectedToSelected -> accentColor
                    selectedNodeIndex != null -> Color(0xFF2C2C2E).copy(alpha = 0.35f)
                    else -> accentColor.copy(alpha = 0.28f)
                }

                val strokeWidth = when {
                    isConnectedToSelected -> 3.5f * scale
                    else -> 1.5f * scale
                }

                drawLine(
                    color = edgeColor,
                    start = Offset(toScreenX(srcNode.x), toScreenY(srcNode.y)),
                    end = Offset(toScreenX(dstNode.x), toScreenY(dstNode.y)),
                    strokeWidth = strokeWidth.coerceIn(1f, 8f)
                )
            }

            // 2. Draw Nodes
            for (i in 0 until nodes.size) {
                val node = nodes[i]
                val sx = toScreenX(node.x)
                val sy = toScreenY(node.y)

                // Skip drawing if outside screen viewport
                if (sx < -100 || sx > canvasWidth + 100 || sy < -100 || sy > canvasHeight + 100) {
                    continue
                }

                val isSelected = selectedNodeIndex == i
                val isConnectedToSelected = selectedNeighbors.contains(i)

                val isQueryMatch = searchQuery.isNotBlank() &&
                        node.note.displayTitle.contains(searchQuery, ignoreCase = true)

                val nodeRadius = (node.radius * scale).coerceIn(4f, 60f)

                // Outer Halo Glow for Selected / Connected / High-degree Nodes
                if (isSelected || isConnectedToSelected || isQueryMatch) {
                    drawCircle(
                        color = if (isSelected || isQueryMatch) accentColor.copy(alpha = 0.32f) else accentColor.copy(alpha = 0.16f),
                        radius = nodeRadius + (10f * scale).coerceIn(4f, 24f),
                        center = Offset(sx, sy)
                    )
                }

                // Core Node Fill
                val fillColor = when {
                    isSelected -> accentColor
                    isQueryMatch -> accentColor
                    isConnectedToSelected -> accentColor.copy(alpha = 0.9f)
                    node.degree > 0 -> accentColor.copy(alpha = 0.85f)
                    else -> Color(0xFF636366)
                }

                drawCircle(
                    color = fillColor,
                    radius = nodeRadius,
                    center = Offset(sx, sy)
                )

                // Border Ring
                val borderColor = if (isSelected) Color.White else Color(0x33FFFFFF)
                drawCircle(
                    color = borderColor,
                    radius = nodeRadius,
                    center = Offset(sx, sy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * scale)
                )

                // Node Title Label (draw if scale is readable or node is selected/matching query)
                val shouldDrawLabel = isSelected || isQueryMatch || isConnectedToSelected ||
                        (scale > 0.85f && node.degree > 0) || (scale > 1.3f)

                if (shouldDrawLabel) {
                    val label = node.note.displayTitle
                    val paint = if (isSelected || isQueryMatch) selectedLabelPaint else labelPaint
                    paint.textSize = (13.sp.toPx() * scale).coerceIn(18f, 40f)

                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        sx,
                        sy + nodeRadius + (16f * scale).coerceIn(12f, 28f),
                        paint
                    )
                }
            }
        }

        // Top Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "< Notes" Button
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
                    tint = iOSYellow,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Notes",
                    color = iOSYellow,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Title Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(iOSCardBackground)
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = iOSYellow,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${notes.size} Notes • ${edges.size} Links",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            // Recenter View Button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Recenter Graph",
                    tint = iOSYellow,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Selected Note Detail Card (Bottom Float)
        val selectedNode = selectedNodeIndex?.let { if (it in nodes.indices) nodes[it] else null }
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
                    shape = RoundedCornerShape(16.dp),
                    color = iOSCardBackground,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedNode.note.displayTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${selectedNode.degree} connections",
                                        fontSize = 12.sp,
                                        color = iOSYellow
                                    )
                                    if (selectedNode.note.folderName != "Notes") {
                                        Text(
                                            text = " • ${selectedNode.note.folderName}",
                                            fontSize = 12.sp,
                                            color = iOSTextSecondary
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
                                    containerColor = iOSYellow,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Open",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        if (selectedNode.note.previewSnippet.isNotBlank() && selectedNode.note.previewSnippet != "No additional text") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = selectedNode.note.previewSnippet,
                                fontSize = 13.sp,
                                color = iOSTextSecondary,
                                maxLines = 2,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
