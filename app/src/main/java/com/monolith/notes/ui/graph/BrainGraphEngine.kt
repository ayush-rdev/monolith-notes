package com.monolith.notes.ui.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.monolith.notes.model.Note
import kotlin.math.*

/**
 * Anatomical Lobe classification for notes in Brain Graph mode.
 */
enum class BrainLobe(val displayName: String, val shortName: String) {
    FRONTAL_LEFT("Frontal Lobe (Left)", "Frontal L"),
    FRONTAL_RIGHT("Frontal Lobe (Right)", "Frontal R"),
    PARIETAL_LEFT("Parietal Lobe (Left)", "Parietal L"),
    PARIETAL_RIGHT("Parietal Lobe (Right)", "Parietal R"),
    TEMPORAL_LEFT("Temporal Lobe (Left)", "Temporal L"),
    TEMPORAL_RIGHT("Temporal Lobe (Right)", "Temporal R"),
    OCCIPITAL_LEFT("Occipital Lobe (Left)", "Occipital L"),
    OCCIPITAL_RIGHT("Occipital Lobe (Right)", "Occipital R"),
    CEREBELLUM("Cerebellum (Orphans)", "Cerebellum")
}

/**
 * Represents the anatomical layout and geometry of the human brain cortex in 2D superior/axial view.
 * Reverse-engineered from the Obsidian Brain Graph plugin for supreme mobile performance.
 */
object BrainGraphEngine {

    // Base dimensions for the anatomical coordinate space
    const val BRAIN_WIDTH = 840f
    const val BRAIN_HEIGHT = 1040f
    private const val MEDIAL_FISSURE_HALF_WIDTH = 22f

    /**
     * Compute brain coordinates (brainX, brainY) and assign an anatomical lobe to each node.
     */
    @Suppress("UNUSED_PARAMETER")
    fun computeBrainLayout(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>
    ) {
        if (nodes.isEmpty()) return

        // 1. Separate connected notes from orphan notes (degree == 0)
        val connectedNodes = ArrayList<GraphNode>()
        val orphanNodes = ArrayList<GraphNode>()

        for (node in nodes) {
            if (node.degree == 0) {
                orphanNodes.add(node)
            } else {
                connectedNodes.add(node)
            }
        }

        // 2. Group connected notes by clusterName (e.g. folder or community)
        val clusterMap = LinkedHashMap<String, ArrayList<GraphNode>>()
        for (node in connectedNodes) {
            clusterMap.getOrPut(node.clusterName) { ArrayList() }.add(node)
        }

        // Sort clusters by size (largest first)
        val sortedClusters = clusterMap.entries.sortedByDescending { it.value.size }

        // Anatomical Cortical Sectors (excluding Cerebellum, which is reserved for orphans)
        val corticalLobes = listOf(
            BrainLobe.FRONTAL_LEFT,
            BrainLobe.FRONTAL_RIGHT,
            BrainLobe.PARIETAL_LEFT,
            BrainLobe.PARIETAL_RIGHT,
            BrainLobe.TEMPORAL_LEFT,
            BrainLobe.TEMPORAL_RIGHT,
            BrainLobe.OCCIPITAL_LEFT,
            BrainLobe.OCCIPITAL_RIGHT
        )

        // Assign clusters to lobes
        sortedClusters.forEachIndexed { clusterIndex, entry ->
            val lobe = corticalLobes[clusterIndex % corticalLobes.size]
            val clusterNodes = entry.value

            // Sort nodes in cluster so the highest-degree Hub sits at the focal center
            clusterNodes.sortByDescending { it.degree }
            layoutClusterInLobe(clusterNodes, lobe)
        }

        // 3. Layout Orphan notes inside the Cerebellum at the posterior base
        layoutOrphansInCerebellum(orphanNodes)

        // 4. Precompute fixed world-space fiber control points for all edges
        val coreX = 0f
        val coreY = -30f
        for (edge in edges) {
            val src = nodes[edge.sourceIndex]
            val dst = nodes[edge.targetIndex]
            val midX = (src.brainX + dst.brainX) * 0.5f
            val midY = (src.brainY + dst.brainY) * 0.5f
            val dist = hypot(dst.brainX - src.brainX, dst.brainY - src.brainY)
            val bendFactor = (dist / 600f).coerceIn(0.20f, 0.65f)
            edge.brainCtrlX = midX + (coreX - midX) * bendFactor
            edge.brainCtrlY = midY + (coreY - midY) * bendFactor
        }
    }

    /**
     * Layout nodes belonging to a cluster inside a specific anatomical lobe.
     */
    private fun layoutClusterInLobe(nodes: List<GraphNode>, lobe: BrainLobe) {
        val (centerX, centerY, radiusX, radiusY) = getLobeCenterAndRadii(lobe)
        val count = nodes.size
        val goldenAngle = 2.3999632f

        for (i in 0 until count) {
            val node = nodes[i]
            node.lobe = lobe

            if (i == 0) {
                // Primary Hub at the exact focal center of the lobe
                node.brainX = centerX
                node.brainY = centerY
            } else {
                // Distribute surrounding nodes using Fermat spiral clamped within lobe bounds
                val normRadius = sqrt(i.toFloat() / count.toFloat())
                val theta = i * goldenAngle
                val rx = normRadius * (radiusX * 0.88f)
                val ry = normRadius * (radiusY * 0.88f)

                var x = centerX + rx * cos(theta)
                var y = centerY + ry * sin(theta)

                // Enforce medial longitudinal fissure clearance (notes stay away from center line)
                if (lobe.name.contains("LEFT")) {
                    x = x.coerceAtMost(-MEDIAL_FISSURE_HALF_WIDTH)
                } else if (lobe.name.contains("RIGHT")) {
                    x = x.coerceAtLeast(MEDIAL_FISSURE_HALF_WIDTH)
                }

                node.brainX = x
                node.brainY = y
            }
        }
    }

    /**
     * Layout orphan notes (unlinked) inside the bilateral Cerebellum.
     */
    private fun layoutOrphansInCerebellum(orphans: List<GraphNode>) {
        val count = orphans.size
        if (count == 0) return

        val goldenAngle = 2.3999632f
        val cerebCenterY = 380f // Posterior base
        val cerebRadiusX = 280f
        val cerebRadiusY = 110f

        for (i in 0 until count) {
            val node = orphans[i]
            node.lobe = BrainLobe.CEREBELLUM

            val normRadius = sqrt((i + 1).toFloat() / count.toFloat())
            val theta = i * goldenAngle
            val rx = normRadius * (cerebRadiusX * 0.85f)
            val ry = normRadius * (cerebRadiusY * 0.85f)

            val x = rx * cos(theta)
            val y = cerebCenterY + ry * sin(theta)

            node.brainX = x
            node.brainY = y
        }
    }

    /**
     * Returns (centerX, centerY, radiusX, radiusY) in screen-independent brain units.
     * Coordinate origin (0, 0) is the center of the cerebral cortex.
     * -Y is anterior (Frontal pole).
     * +Y is posterior (Occipital / Cerebellum).
     */
    private fun getLobeCenterAndRadii(lobe: BrainLobe): FloatArray {
        return when (lobe) {
            BrainLobe.FRONTAL_LEFT -> floatArrayOf(-170f, -280f, 150f, 160f)
            BrainLobe.FRONTAL_RIGHT -> floatArrayOf(170f, -280f, 150f, 160f)
            BrainLobe.PARIETAL_LEFT -> floatArrayOf(-220f, -60f, 160f, 140f)
            BrainLobe.PARIETAL_RIGHT -> floatArrayOf(220f, -60f, 160f, 140f)
            BrainLobe.TEMPORAL_LEFT -> floatArrayOf(-260f, 120f, 140f, 130f)
            BrainLobe.TEMPORAL_RIGHT -> floatArrayOf(260f, 120f, 140f, 130f)
            BrainLobe.OCCIPITAL_LEFT -> floatArrayOf(-140f, 260f, 120f, 120f)
            BrainLobe.OCCIPITAL_RIGHT -> floatArrayOf(140f, 260f, 120f, 120f)
            BrainLobe.CEREBELLUM -> floatArrayOf(0f, 380f, 260f, 110f)
        }
    }

    /**
     * Compute the quadratic Bézier control point for a white matter neural fiber.
     * Fibers dive toward the subcortical core (0, -20f), creating an anatomical arc.
     * Cross-hemispheric fibers arc across the medial fissure like the Corpus Callosum.
     */
    fun computeFiberControlPoint(
        sx: Float, sy: Float,
        dx: Float, dy: Float,
        centerX: Float, centerY: Float,
        scale: Float
    ): Offset {
        val midX = (sx + dx) * 0.5f
        val midY = (sy + dy) * 0.5f

        val dist = hypot(dx - sx, dy - sy)
        // Subcortical core in screen coordinates
        val coreX = centerX
        val coreY = centerY - (30f * scale)

        // Longer fibers dive deeper toward the core (corpus callosum / internal capsule)
        val bendFactor = (dist / (600f * scale)).coerceIn(0.20f, 0.65f)

        val ctrlX = midX + (coreX - midX) * bendFactor
        val ctrlY = midY + (coreY - midY) * bendFactor

        return Offset(ctrlX, ctrlY)
    }

    /**
     * Generates a sleek, anatomical outline path of the human brain in superior (top-down) view.
     * Includes bilateral cerebral hemispheres, medial longitudinal fissure, and cerebellum.
     */
    fun buildAnatomicalContourPath(
        targetPath: Path = Path(),
        centerX: Float,
        centerY: Float,
        scale: Float
    ): Path {
        targetPath.reset()

        fun toX(bx: Float) = centerX + bx * scale
        fun toY(by: Float) = centerY + by * scale

        // 1. Left Hemisphere Outer Contour
        targetPath.moveTo(toX(-MEDIAL_FISSURE_HALF_WIDTH), toY(-440f)) // Frontal pole medial start
        // Frontal pole curve
        targetPath.cubicTo(
            toX(-120f), toY(-480f),
            toX(-260f), toY(-400f),
            toX(-340f), toY(-240f)
        )
        // Temporal / Parietal lateral bulge
        targetPath.cubicTo(
            toX(-400f), toY(-120f),
            toX(-410f), toY(80f),
            toX(-350f), toY(220f)
        )
        // Occipital curvature
        targetPath.cubicTo(
            toX(-300f), toY(320f),
            toX(-160f), toY(380f),
            toX(-MEDIAL_FISSURE_HALF_WIDTH), toY(370f)
        )
        // Medial longitudinal fissure left border
        targetPath.lineTo(toX(-MEDIAL_FISSURE_HALF_WIDTH), toY(-440f))

        // 2. Right Hemisphere Outer Contour
        targetPath.moveTo(toX(MEDIAL_FISSURE_HALF_WIDTH), toY(-440f))
        targetPath.cubicTo(
            toX(120f), toY(-480f),
            toX(260f), toY(-400f),
            toX(340f), toY(-240f)
        )
        targetPath.cubicTo(
            toX(400f), toY(-120f),
            toX(410f), toY(80f),
            toX(350f), toY(220f)
        )
        targetPath.cubicTo(
            toX(300f), toY(320f),
            toX(160f), toY(380f),
            toX(MEDIAL_FISSURE_HALF_WIDTH), toY(370f)
        )
        targetPath.lineTo(toX(MEDIAL_FISSURE_HALF_WIDTH), toY(-440f))

        // 3. Cerebellum Base Contour (below occipital lobes)
        targetPath.moveTo(toX(-280f), toY(375f))
        targetPath.cubicTo(
            toX(-320f), toY(440f),
            toX(-160f), toY(500f),
            toX(0f), toY(490f)
        )
        targetPath.cubicTo(
            toX(160f), toY(500f),
            toX(320f), toY(440f),
            toX(280f), toY(375f)
        )
        targetPath.cubicTo(
            toX(140f), toY(390f),
            toX(-140f), toY(390f),
            toX(-280f), toY(375f)
        )

        return targetPath
    }
}
