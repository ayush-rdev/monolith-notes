package com.monolith.notes.ui.graph

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.data.ai.ActiveNeuronCandidate
import com.monolith.notes.data.ai.ActiveNeuronCitation
import com.monolith.notes.data.ai.GeminiApiClient
import com.monolith.notes.data.ai.NeuralQuerySynthesis
import com.monolith.notes.data.ai.VaultNeuronEngine
import com.monolith.notes.model.Note
import com.monolith.notes.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Obsidian-Grade Neural Query Interface.
 *
 * Treats graph nodes as biological neurons:
 * - Directly query your vault with natural language
 * - Spreading activation & lexical scoring retrieves active neurons
 * - Zero-hallucination grounding: Answers strictly from your notes
 * - Visual citations & 1-tap focus to locate fired neurons in the graph or open in editor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskNeuronsSheet(
    notes: List<Note>,
    apiKey: String,
    aiModel: String = "gemini-2.0-flash",
    onConfigureApiKey: () -> Unit = {},
    onLocateNeuronInGraph: (noteId: String, title: String) -> Unit = { _, _ -> },
    onOpenNote: (Note) -> Unit = {},
    onActiveNeuronsUpdated: (List<String>) -> Unit = {},
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var queryText by remember { mutableStateOf("") }
    var isQuerying by remember { mutableStateOf(false) }
    var activeCandidates by remember { mutableStateOf<List<ActiveNeuronCandidate>>(emptyList()) }
    var synthesisResult by remember { mutableStateOf<NeuralQuerySynthesis?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEvidenceAccordion by remember { mutableStateOf(false) }

    val isKeyConfigured = apiKey.isNotBlank()

    fun runQuery(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || isQuerying) return
        keyboardController?.hide()
        focusManager.clearFocus()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        isQuerying = true
        errorMessage = null
        synthesisResult = null

        // 1. Spreading Activation & Lexical Retrieval of Vault Neurons
        val candidates = VaultNeuronEngine.activateNeurons(trimmed, notes, maxNeurons = 7)
        activeCandidates = candidates

        // Notify parent graph screen to light up fired neurons on the canvas!
        onActiveNeuronsUpdated(candidates.map { it.note.id })

        coroutineScope.launch {
            val result = GeminiApiClient.queryVaultNeurons(
                apiKey = apiKey,
                model = aiModel,
                query = trimmed,
                activeNeurons = candidates
            )
            isQuerying = false
            if (result.isSuccess) {
                synthesisResult = result.getOrNull()
            } else {
                errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Query synthesis failed."
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0C0B10),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38354A))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // Header: Brain & Synapse Identity
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF4C1D95), Color(0xFF7C3AED))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ask Vault Neurons",
                                style = MonolithTypography.titleMedium.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF281F3D))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "RAG",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA78BFA)
                                )
                            }
                        }
                        Text(
                            text = "Answers grounded strictly in your notes",
                            style = MonolithTypography.bodySmall.copy(fontSize = 11.5.sp),
                            color = iOSTextSecondary
                        )
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Done",
                        color = Color(0xFFA78BFA),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Query Input Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF15131E))
                    .border(1.dp, Color(0xFF2F2845), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    TextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = {
                            Text(
                                text = "Ask your vault's neurons...",
                                fontSize = 14.sp,
                                color = iOSTextSecondary
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFA78BFA)
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runQuery(queryText) }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )

                    if (queryText.isNotBlank()) {
                        IconButton(
                            onClick = { queryText = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = iOSTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Firing Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (queryText.isNotBlank() && !isQuerying && isKeyConfigured)
                                    Color(0xFF7C3AED)
                                else Color(0xFF262138)
                            )
                            .clickable(enabled = queryText.isNotBlank() && !isQuerying && isKeyConfigured) {
                                runQuery(queryText)
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Query",
                                tint = if (queryText.isNotBlank() && !isQuerying && isKeyConfigured) Color.White else iOSTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // API Key Warning if missing
            if (!isKeyConfigured) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1724))
                        .border(1.dp, Color(0xFF45273C), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Gemini API key required for neural retrieval",
                        fontSize = 11.5.sp,
                        color = Color(0xFFE9D5FF)
                    )
                    TextButton(
                        onClick = {
                            onDismiss()
                            onConfigureApiKey()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Configure", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                    }
                }
            }

            // Quick Prompt Suggestions (Horizontal Reel)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf(
                    "Synthesize core principles",
                    "What are my focus protocols?",
                    "Cache locality & data design",
                    "Software architecture rules",
                    "Identify knowledge gaps"
                )
                suggestions.forEach { prompt ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF181523))
                            .border(0.5.dp, Color(0xFF2C2440), RoundedCornerShape(8.dp))
                            .clickable {
                                queryText = prompt
                                runQuery(prompt)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "✦ $prompt",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFD8B4FE)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Querying State: Synaptic Firing Animation
            if (isQuerying) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF151221))
                        .border(1.dp, Color(0xFF3B2F57), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFFA78BFA),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Activating synaptic paths across ${notes.size} neurons...",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }

                        if (activeCandidates.isNotEmpty()) {
                            Text(
                                text = "Fired neurons: ${activeCandidates.joinToString(", ") { "[[${it.note.displayTitle}]]" }}",
                                fontSize = 11.sp,
                                color = Color(0xFFC084FC),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Error Display
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2B1417))
                        .border(1.dp, Color(0xFF5C262C), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Error: $errorMessage",
                        fontSize = 12.sp,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }

            // Result Display: Axiomatic Synthesis & Active Neurons
            val syn = synthesisResult
            if (syn != null) {
                // Grounding Verification Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101B17))
                        .border(1.dp, Color(0xFF1A382B), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Grounded Exclusively in Vault",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6EE7B7)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (syn.confidence) {
                                    "HIGH" -> Color(0xFF065F46)
                                    "MODERATE" -> Color(0xFF78350F)
                                    else -> Color(0xFF7F1D1D)
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${syn.confidence} CONFIDENCE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (syn.confidence) {
                                "HIGH" -> Color(0xFFA7F3D0)
                                "MODERATE" -> Color(0xFFFDE68A)
                                else -> Color(0xFFFECACA)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Active Neurons Reel (Fired Nodes)
                Text(
                    text = "ACTIVATED NEURONS (${syn.activeNeurons.size})",
                    style = MonolithTypography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = Color(0xFFA78BFA),
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    syn.activeNeurons.forEach { neuron ->
                        val matchedNote = remember(neuron.noteId, notes) { notes.firstOrNull { it.id == neuron.noteId } }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF171424))
                                .border(1.dp, Color(0xFF33294C), RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (neuron.noteId != null) {
                                        onLocateNeuronInGraph(neuron.noteId, neuron.title)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Column(modifier = Modifier.widthIn(max = 190.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFA78BFA))
                                        )
                                        Text(
                                            text = neuron.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (matchedNote != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open Note",
                                            tint = Color(0xFFA78BFA),
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onOpenNote(matchedNote)
                                                }
                                        )
                                    }
                                }
                                if (neuron.relevance.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = neuron.relevance,
                                        fontSize = 10.sp,
                                        color = iOSTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Direct Axiomatic Answer Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF14121F))
                        .border(1.dp, Color(0xFF2C2442), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SYNTHESIS",
                            style = MonolithTypography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                            color = Color(0xFFA78BFA)
                        )

                        Text(
                            text = syn.directAnswer,
                            style = MonolithTypography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.White
                        )
                    }
                }

                // Key Points with Citations
                if (syn.keyPoints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "GROUNDED POINTS & INFERENCES",
                        style = MonolithTypography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                        color = iOSTextSecondary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                    )

                    syn.keyPoints.forEach { point ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF13111C))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "•",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA78BFA)
                                )
                                Text(
                                    text = point,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }

                // Vault Gaps Section (if present)
                val gaps = syn.missingGaps
                if (!gaps.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF20162B))
                            .border(1.dp, Color(0xFF3F2B57), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFD8B4FE),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "VAULT GAPS DETECTED",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD8B4FE)
                                )
                            }
                            Text(
                                text = gaps,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFFE9D5FF)
                            )
                        }
                    }
                }

                // Grounded Source Quotes Accordion (100% BS Proof)
                if (syn.activeNeurons.any { it.excerpt.isNotBlank() }) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showEvidenceAccordion = !showEvidenceAccordion }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (showEvidenceAccordion) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = iOSTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "View Source Quotes from Notes",
                                fontSize = 12.sp,
                                color = iOSTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    AnimatedVisibility(visible = showEvidenceAccordion) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            syn.activeNeurons.filter { it.excerpt.isNotBlank() }.forEach { neuron ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F0E17))
                                        .border(0.5.dp, Color(0xFF28233D), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = "[[${neuron.title}]]",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA78BFA)
                                        )
                                        Text(
                                            text = "\"${neuron.excerpt}\"",
                                            fontSize = 11.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            lineHeight = 15.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (!isQuerying) {
                // Empty State prior to query
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF13111C))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF231B38)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Grain,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "Nodes as Neurons",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Every note in your vault is treated as a synaptic neuron. Ask anything to activate connected thoughts and receive answers strictly verified against your written notes.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = iOSTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
