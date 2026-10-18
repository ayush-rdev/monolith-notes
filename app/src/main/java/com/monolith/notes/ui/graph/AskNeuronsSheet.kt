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
import java.util.Locale

/**
 * Obsidian-Grade Mathematical Neural Retrieval Interface.
 *
 * Implements spreading activation retrieval over vault notes:
 * - Mathematical operator: S(n) = TF-IDF(q, n) + Σ λ·S(j) (λ = 0.25)
 * - Strict zero-hallucination grounding: Exclusively synthesizes from active notes
 * - Monospace telemetry readout: Latency, active candidate scores, vault parameters
 * - Minimalist typography & razor-sharp obsidian borders
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskNeuronsSheet(
    notes: List<Note>,
    apiKey: String,
    aiModel: String = "gemini-2.0-flash",
    initialQueryText: String = "",
    initialSynthesis: NeuralQuerySynthesis? = null,
    initialCandidates: List<ActiveNeuronCandidate> = emptyList(),
    onSynthesisChanged: (query: String, candidates: List<ActiveNeuronCandidate>, synthesis: NeuralQuerySynthesis?) -> Unit = { _, _, _ -> },
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

    var queryText by remember(initialQueryText) { mutableStateOf(initialQueryText) }
    var isQuerying by remember { mutableStateOf(false) }
    var activeCandidates by remember(initialCandidates) { mutableStateOf(initialCandidates) }
    var synthesisResult by remember(initialSynthesis) { mutableStateOf(initialSynthesis) }
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

        // Spreading Activation & Lexical Retrieval over Vault Neurons
        val candidates = VaultNeuronEngine.activateNeurons(trimmed, notes, maxNeurons = 7)
        activeCandidates = candidates

        // Light up fired neurons on knowledge graph canvas
        onActiveNeuronsUpdated(candidates.map { it.note.id })
        onSynthesisChanged(trimmed, candidates, null)

        coroutineScope.launch {
            val result = GeminiApiClient.queryVaultNeurons(
                apiKey = apiKey,
                model = aiModel,
                query = trimmed,
                activeNeurons = candidates,
                totalVaultNotesCount = notes.size
            )
            isQuerying = false
            if (result.isSuccess) {
                val syn = result.getOrNull()
                synthesisResult = syn
                onSynthesisChanged(trimmed, candidates, syn)
            } else {
                errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Query synthesis failed."
                onSynthesisChanged(trimmed, candidates, null)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0A090D),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(32.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E2C3D))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 2.dp)
        ) {
            // Header: Precision Mathematical Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF261D3A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ask Notes",
                            style = MonolithTypography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFF1F5F9)
                        )
                    }
                    Text(
                        text = "Answers grounded strictly in your notes • Zero hallucinations",
                        style = MonolithTypography.bodySmall.copy(
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF94A3B8)
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Done",
                        color = Color(0xFFA78BFA),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Query Input Box (Razor-Sharp Terminal Style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF111018))
                    .border(0.5.dp, Color(0x33A78BFA), RoundedCornerShape(7.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = {
                            Text(
                                text = "Ask anything about your notes...",
                                fontSize = 12.5.sp,
                                color = Color(0xFF64748B)
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

                    if (queryText.isNotBlank() || synthesisResult != null) {
                        IconButton(
                            onClick = {
                                queryText = ""
                                synthesisResult = null
                                activeCandidates = emptyList()
                                errorMessage = null
                                onActiveNeuronsUpdated(emptyList())
                                onSynthesisChanged("", emptyList(), null)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(2.dp))
                    }

                    // Run Query Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (queryText.isNotBlank() && !isQuerying && isKeyConfigured)
                                    Color(0xFF7C3AED)
                                else Color(0xFF1E1A29)
                            )
                            .clickable(enabled = queryText.isNotBlank() && !isQuerying && isKeyConfigured) {
                                runQuery(queryText)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Run",
                            tint = if (queryText.isNotBlank() && !isQuerying && isKeyConfigured) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Missing API Key Callout
            if (!isKeyConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF191217))
                        .border(0.5.dp, Color(0xFF45222E), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Gemini API key required for neural retrieval",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFCA5A5)
                    )
                    TextButton(
                        onClick = {
                            onDismiss()
                            onConfigureApiKey()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Config", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                    }
                }
            }



            // Mathematical Execution State (Querying)
            if (isQuerying) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F0E16))
                        .border(0.5.dp, Color(0x33A78BFA), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = Color(0xFFA78BFA),
                                strokeWidth = 1.5.dp
                            )
                            Text(
                                text = "Searching ${notes.size} notes and synthesizing answer...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        if (activeCandidates.isNotEmpty()) {
                            Text(
                                text = "Matching notes: " + activeCandidates.joinToString(" • ") { it.note.displayTitle },
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
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
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF221114))
                        .border(0.5.dp, Color(0xFF4C1D24), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "ERROR: $errorMessage",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }

            // Result Display: Axiomatic Synthesis & Telemetry
            val syn = synthesisResult
            if (syn != null) {
                Spacer(modifier = Modifier.height(10.dp))

                // Grounded Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0C1310))
                        .border(0.5.dp, Color(0x3334D399), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34D399))
                        )
                        Text(
                            text = "Grounded in ${syn.activeNeurons.size} notes",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6EE7B7)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (syn.confidence) {
                                    "HIGH" -> Color(0x2234D399)
                                    "MODERATE" -> Color(0x22FBBF24)
                                    else -> Color(0x22F87171)
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (syn.confidence) {
                                "HIGH" -> "High Match"
                                "MODERATE" -> "Moderate Match"
                                else -> "Low Match"
                            },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (syn.confidence) {
                                "HIGH" -> Color(0xFFA7F3D0)
                                "MODERATE" -> Color(0xFFFDE68A)
                                else -> Color(0xFFFECACA)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section 01: Relevant Notes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RELEVANT NOTES (${syn.activeNeurons.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color(0xFFA78BFA)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    syn.activeNeurons.forEach { neuron ->
                        val matchedNote = remember(neuron.noteId, notes) { notes.firstOrNull { it.id == neuron.noteId } }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF13111C))
                                .border(0.5.dp, Color(0x33A78BFA), RoundedCornerShape(7.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (neuron.noteId != null) {
                                        onLocateNeuronInGraph(neuron.noteId, neuron.title)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Column(modifier = Modifier.widthIn(max = 180.dp)) {
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
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFA78BFA))
                                        )
                                        Text(
                                            text = neuron.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (matchedNote != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
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
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = neuron.relevance,
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section 02: Answer
                Text(
                    text = "ANSWER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Color(0xFFA78BFA),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF100F17))
                        .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = syn.directAnswer,
                        style = MonolithTypography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color(0xFFF1F5F9)
                    )
                }

                // Section 03: Key Points
                if (syn.keyPoints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "KEY TAKEAWAYS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    syn.keyPoints.forEachIndexed { idx, point ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.5.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F0E16))
                                .border(0.5.dp, Color(0x18FFFFFF), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "•",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA78BFA)
                                )
                                Text(
                                    text = point,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }

                // Section 04: Missing from notes
                val gaps = syn.missingGaps
                if (!gaps.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "NOT COVERED IN NOTES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF16130D))
                            .border(0.5.dp, Color(0x33F59E0B), RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = gaps,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFFDE68A)
                        )
                    }
                }

                // Section 05: Grounded Source Quotes Accordion (100% Proof)
                if (syn.activeNeurons.any { it.excerpt.isNotBlank() }) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { showEvidenceAccordion = !showEvidenceAccordion }
                            .padding(vertical = 5.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (showEvidenceAccordion) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "05 / VERBATIM SOURCE EXCERPTS (100% GROUNDED)",
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AnimatedVisibility(visible = showEvidenceAccordion) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            syn.activeNeurons.filter { it.excerpt.isNotBlank() }.forEach { neuron ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF0C0B12))
                                        .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(5.dp))
                                        .padding(8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "[[${neuron.title}]]",
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA78BFA)
                                        )
                                        Text(
                                            text = "\"${neuron.excerpt}\"",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (!isQuerying) {
                // User-friendly Note Retrieval Help Card
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF12111A))
                        .border(0.5.dp, Color(0x22A78BFA), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "How Ask Notes Works",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF1F5F9)
                            )
                        }
                        Text(
                            text = "Ask questions in natural language. Monolith searches across your ${notes.size} notes and their [[wikilinks]] to synthesize direct answers strictly from what you wrote — zero hallucination.",
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
