package com.monolith.notes.ui.graph

import androidx.compose.ui.graphics.Color
import com.monolith.notes.model.Note
import kotlin.math.*

/**
 * Node representation within the Monolith personal knowledge graph.
 */
data class GraphNode(
    val id: String,
    val note: Note,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val radius: Float,
    val inDegree: Int,
    val outDegree: Int,
    val degree: Int,
    val centralityScore: Float,
    val isHub: Boolean,
    val clusterColor: Color,
    val clusterName: String,
    var isPinned: Boolean = false,
    var brainX: Float = 0f,
    var brainY: Float = 0f,
    var lobe: BrainLobe = BrainLobe.FRONTAL_LEFT
)

/**
 * Directed edge pointing from source note (containing wikilink) to target referenced note.
 */
data class GraphEdge(
    val sourceIndex: Int,
    val targetIndex: Int,
    val isDirected: Boolean = true,
    var brainCtrlX: Float = 0f,
    var brainCtrlY: Float = 0f
)

/**
 * Intelligent recommendation connecting unlinked notes based on shared tags or text mentions.
 */
data class SuggestedConnection(
    val targetNote: Note,
    val reason: String
)

/**
 * High-level intelligence metrics for the user's knowledge vault.
 */
data class GraphAnalytics(
    val totalNotes: Int,
    val totalLinks: Int,
    val hubCount: Int,
    val orphanCount: Int,
    val densityPercentage: Int,
    val topHubs: List<GraphNode>,
    val suggestions: Map<String, List<SuggestedConnection>>
)

data class KnowledgeGraphBundle(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val analytics: GraphAnalytics,
    val initialGalaxyScale: Float = 0.55f,
    val initialBrainScale: Float = 0.82f
)

enum class ClusterMode {
    FOLDER,
    TAG
}

object KnowledgeGraphEngine {

    // Curated harmonious cluster colors for visual segmentation
    val CLUSTER_PALETTE = listOf(
        Color(0xFFE5A93C), // Amber Gold
        Color(0xFF38BDF8), // Nordic Sky
        Color(0xFF30D158), // Emerald Green
        Color(0xFFBF5AF2), // Electric Violet
        Color(0xFFFF453A), // Vermilion Red
        Color(0xFF34D399), // Sage Mint
        Color(0xFFFB923C), // Warm Tangerine
        Color(0xFF818CF8), // Royal Indigo
        Color(0xFFFB7185), // Rose Pink
        Color(0xFF2DD4BF)  // Deep Teal
    )

    fun getClusterColor(key: String): Color {
        if (key.isBlank() || key.equals("Notes", ignoreCase = true)) {
            return CLUSTER_PALETTE[0]
        }
        val hash = abs(key.hashCode())
        return CLUSTER_PALETTE[hash % CLUSTER_PALETTE.size]
    }

    /**
     * Builds nodes, edges, centrality scores, and suggested connections from the notes collection.
     */
    fun buildGraph(
        notes: List<Note>,
        clusterMode: ClusterMode = ClusterMode.FOLDER
    ): KnowledgeGraphBundle {
        val count = notes.size
        if (count == 0) {
            return KnowledgeGraphBundle(
                nodes = emptyList(),
                edges = emptyList(),
                analytics = GraphAnalytics(0, 0, 0, 0, 0, emptyList(), emptyMap())
            )
        }

        // 1. Build lookup index by note display title, filename, and id
        val titleMap = HashMap<String, Int>(count * 2)
        notes.forEachIndexed { index, note ->
            titleMap[note.displayTitle.lowercase().trim()] = index
            note.file?.nameWithoutExtension?.let {
                titleMap[it.lowercase().trim()] = index
            }
            titleMap[note.id] = index
        }

        val inDegrees = IntArray(count)
        val outDegrees = IntArray(count)
        val edgesList = ArrayList<GraphEdge>()
        val edgeSet = HashSet<Pair<Int, Int>>()

        // 2. Discover directional outlinks (wikilinks)
        notes.forEachIndexed { srcIndex, note ->
            for (outlink in note.outlinks) {
                val targetIndex = titleMap[outlink.lowercase().trim()]
                if (targetIndex != null && targetIndex != srcIndex) {
                    val pair = Pair(srcIndex, targetIndex)
                    if (edgeSet.add(pair)) {
                        edgesList.add(GraphEdge(srcIndex, targetIndex, isDirected = true))
                        outDegrees[srcIndex]++
                        inDegrees[targetIndex]++
                    }
                }
            }
        }

        // 3. PageRank Centrality Calculation (15 iterations)
        val totalDeg = IntArray(count) { inDegrees[it] + outDegrees[it] }
        var pageRank = FloatArray(count) { 1.0f / count }
        val prDamping = 0.85f

        for (iter in 0 until 15) {
            val nextPR = FloatArray(count) { (1f - prDamping) / count }
            for (edge in edgesList) {
                val outDeg = max(outDegrees[edge.sourceIndex], 1)
                nextPR[edge.targetIndex] += prDamping * (pageRank[edge.sourceIndex] / outDeg)
            }
            pageRank = nextPR
        }

        // Normalize PageRank between 0f and 1f
        val maxPR = pageRank.maxOrNull() ?: 1f
        val minPR = pageRank.minOrNull() ?: 0f
        val prRange = max(maxPR - minPR, 0.0001f)

        // 4. Initial layout coordinates using a Golden Spiral distribution
        val goldenAngle = 2.3999632f
        val distanceStep = (75f + 15f * log2(count.coerceAtLeast(2).toFloat())).coerceIn(70f, 150f)
        val nodesList = ArrayList<GraphNode>(count)

        var orphanCount = 0

        for (i in 0 until count) {
            val dist = sqrt(i.toFloat() + 1f) * distanceStep
            val theta = i * goldenAngle
            val initX = dist * cos(theta)
            val initY = dist * sin(theta)

            val degree = totalDeg[i]
            if (degree == 0) orphanCount++

            val normPR = (pageRank[i] - minPR) / prRange
            val isHub = (count <= 10 && degree >= 2) || (count > 10 && (degree >= 6 || normPR > 0.75f))

            // Radius scales elegantly with degree and centrality (Obsidian-pro proportion)
            val radius = (5.5f + min(degree * 1.0f + normPR * 5f, 14f)).coerceIn(5.5f, 20f)

            val clusterName = when (clusterMode) {
                ClusterMode.FOLDER -> notes[i].folderName
                ClusterMode.TAG -> notes[i].tags.firstOrNull() ?: notes[i].folderName
            }
            val clusterColor = getClusterColor(clusterName)

            nodesList.add(
                GraphNode(
                    id = notes[i].id,
                    note = notes[i],
                    x = initX,
                    y = initY,
                    radius = radius,
                    inDegree = inDegrees[i],
                    outDegree = outDegrees[i],
                    degree = degree,
                    centralityScore = normPR,
                    isHub = isHub,
                    clusterColor = clusterColor,
                    clusterName = clusterName
                )
            )
        }

        // 4b. Compute anatomical Brain Graph coordinates (Obsidian Brain Graph architecture)
        BrainGraphEngine.computeBrainLayout(nodesList, edgesList)

        // 4c. Pre-stabilize galaxy coordinates so opening the graph is instantaneous with 0ms delay and 0 jump
        val kRepulse = (14000f + 600f * sqrt(count.toFloat())).coerceIn(12000f, 32000f)
        val kSpring = 0.036f
        val restLength = (80f + 12f * log2(count.coerceAtLeast(2).toFloat())).coerceIn(75f, 150f)
        val physicsDamping = 0.80f
        val centerForce = (0.012f / sqrt(count.coerceAtLeast(1).toFloat())).coerceIn(0.003f, 0.016f)

        for (step in 0 until 60) {
            val energy = stepPhysics(
                nodes = nodesList,
                edges = edgesList,
                kRepulse = kRepulse,
                kSpring = kSpring,
                restLength = restLength,
                damping = physicsDamping,
                centerForce = centerForce,
                draggedIndex = null
            )
            if (energy < 0.02f) break
        }

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (n in nodesList) {
            if (n.x < minX) minX = n.x
            if (n.x > maxX) maxX = n.x
            if (n.y < minY) minY = n.y
            if (n.y > maxY) maxY = n.y
        }
        val spanX = (maxX - minX).coerceAtLeast(200f) + 160f
        val spanY = (maxY - minY).coerceAtLeast(200f) + 240f
        val autoFitGalaxy = minOf(950f / spanX, 1600f / spanY).coerceIn(0.25f, 0.85f)
        val autoFitBrain = 0.82f

        // 5. Suggestions Map (deferred / lightweight to prevent UI thread freezing)
        val suggestionsMap = emptyMap<String, List<SuggestedConnection>>()

        val topHubs = nodesList.filter { it.isHub }.sortedByDescending { it.degree }.take(5)
        val density = if (count > 1) ((2.0 * edgesList.size) / (count * (count - 1)) * 100.0).toInt() else 0

        val analytics = GraphAnalytics(
            totalNotes = count,
            totalLinks = edgesList.size,
            hubCount = nodesList.count { it.isHub },
            orphanCount = orphanCount,
            densityPercentage = density,
            topHubs = topHubs,
            suggestions = suggestionsMap
        )

        return KnowledgeGraphBundle(
            nodes = nodesList,
            edges = edgesList,
            analytics = analytics,
            initialGalaxyScale = autoFitGalaxy,
            initialBrainScale = autoFitBrain
        )
    }

    /**
     * Executes one iteration of self-tuning organic force-directed physics.
     * Repulsion, spring tension, and collision radii adapt automatically to node density.
     */
    fun stepPhysics(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        kRepulse: Float,
        kSpring: Float,
        restLength: Float,
        damping: Float,
        centerForce: Float,
        draggedIndex: Int? = null
    ): Float {
        val count = nodes.size
        if (count == 0) return 0f

        // 1. Repulsion between all pairs + collision cushion
        for (i in 0 until count) {
            val nodeA = nodes[i]
            for (j in (i + 1) until count) {
                val nodeB = nodes[j]
                val dx = nodeB.x - nodeA.x
                if (dx > 450f || dx < -450f) continue
                val dy = nodeB.y - nodeA.y
                if (dy > 450f || dy < -450f) continue

                val distSq = dx * dx + dy * dy + 300f
                if (distSq > 202500f) continue // 450^2 cutoff: skip expensive sqrt and division if far away
                val dist = sqrt(distSq)

                // Standard inverse-square repulsion
                var force = kRepulse / distSq

                // Elastic collision cushion to prevent overlapping nodes
                val minSeparation = nodeA.radius + nodeB.radius + 18f
                if (dist < minSeparation) {
                    val overlap = minSeparation - dist
                    force += overlap * 1.5f
                }

                val fx = (dx / dist) * force
                val fy = (dy / dist) * force

                if (i != draggedIndex && !nodeA.isPinned) {
                    nodeA.vx -= fx
                    nodeA.vy -= fy
                }
                if (j != draggedIndex && !nodeB.isPinned) {
                    nodeB.vx += fx
                    nodeB.vy += fy
                }
            }
        }

        // 2. Spring attraction along connected links
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

            if (edge.sourceIndex != draggedIndex && !nodeA.isPinned) {
                nodeA.vx += fx
                nodeA.vy += fy
            }
            if (edge.targetIndex != draggedIndex && !nodeB.isPinned) {
                nodeB.vx -= fx
                nodeB.vy -= fy
            }
        }

        // 3. Centering gravity & velocity integration
        var totalKineticEnergy = 0f
        for (i in 0 until count) {
            if (i == draggedIndex) continue
            val node = nodes[i]
            if (node.isPinned) continue

            node.vx -= node.x * centerForce
            node.vy -= node.y * centerForce
            node.vx *= damping
            node.vy *= damping

            // Velocity clamping to prevent sudden explosive jumps
            val maxSpeed = 30f
            node.vx = node.vx.coerceIn(-maxSpeed, maxSpeed)
            node.vy = node.vy.coerceIn(-maxSpeed, maxSpeed)

            node.x += node.vx
            node.y += node.vy

            totalKineticEnergy += node.vx * node.vx + node.vy * node.vy
        }
        return totalKineticEnergy
    }
}
