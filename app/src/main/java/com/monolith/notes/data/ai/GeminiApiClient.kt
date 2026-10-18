package com.monolith.notes.data.ai

import com.monolith.notes.model.Note
import com.monolith.notes.ui.graph.GraphAnalytics
import com.monolith.notes.ui.graph.GraphNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// ==========================================
// Structured Mathematical Data Models
// ==========================================

data class VaultBridgeProposal(
    val sourceTitle: String,
    val targetTitle: String,
    val affinityScore: Float,
    val rationale: String
)

data class VaultBottleneck(
    val noteTitle: String,
    val severity: String, // HIGH, MEDIUM, LOW
    val vulnerability: String
)

data class VaultTopologySynthesis(
    val structuralAssessment: String,
    val bridgeProposals: List<VaultBridgeProposal>,
    val bottlenecks: List<VaultBottleneck>,
    val entropyNotes: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class ActiveNeuronCitation(
    val title: String,
    val noteId: String?,
    val relevance: String,
    val excerpt: String
)

data class NeuralQuerySynthesis(
    val query: String,
    val directAnswer: String,
    val keyPoints: List<String>,
    val activeNeurons: List<ActiveNeuronCitation>,
    val missingGaps: String?,
    val confidence: String, // "HIGH", "MODERATE", "LOW"
    val timestamp: Long = System.currentTimeMillis()
)

data class NoteWikilinkSuggestion(
    val vaultTitle: String,
    val relevance: String,
    val suggestedPlacement: String
)

data class NotePointAnalysis(
    val premises: List<String>,
    val inferenceSteps: List<String>,
    val conclusions: List<String>,
    val logicalTensions: List<String>,
    val suggestedWikilinks: List<NoteWikilinkSuggestion>,
    val actionVectors: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class JevClassification(
    val suggestedFolder: String?,
    val suggestedTags: List<String>,
    val confidence: Float
)

// ==========================================
// Native, Zero-Bloat Gemini REST Client
// ==========================================

object GeminiApiClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    /**
     * Test API Key validity against Gemini
     */
    suspend fun testConnection(apiKey: String, model: String = "gemini-2.0-flash"): Result<Boolean> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API Key cannot be empty."))
        }

        val primaryModel = if (model == "gemini-1.5-flash" || model.isBlank()) "gemini-2.0-flash" else model
        val candidateModels = linkedSetOf(
            primaryModel,
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-2.5-flash",
            "gemini-1.5-flash-latest"
        )

        var lastError: String? = null
        for (targetModel in candidateModels) {
            try {
                val url = "$BASE_URL/$targetModel:generateContent?key=${apiKey.trim()}"
                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "Respond with the single word 'OK'")
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.0)
                        put("maxOutputTokens", 10)
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                val (isSuccess, errMsg, is404) = client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Triple(true, null, false)
                    } else {
                        Triple(false, parseErrorMessage(response.code, body), response.code == 404)
                    }
                }

                if (isSuccess) {
                    return@withContext Result.success(true)
                }

                lastError = errMsg
                if (!is404) {
                    // Not 404 (e.g. invalid API key 400 or quota 429), no need to retry models
                    return@withContext Result.failure(IOException(errMsg))
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage
            }
        }

        Result.failure(IOException(lastError ?: "Connection failed."))
    }

    /**
     * Synthesize Mathematical Vault Topology & Cross-Cluster Bridges
     */
    suspend fun synthesizeVaultTopology(
        apiKey: String,
        model: String = "gemini-2.0-flash",
        notes: List<Note>,
        analytics: GraphAnalytics,
        nodes: List<GraphNode>
    ): Result<VaultTopologySynthesis> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("No API key configured."))

        try {
            val topHubs = nodes.sortedByDescending { it.degree }.take(8).map {
                "${it.note.title} (deg=${it.degree}, in=${it.inDegree}, out=${it.outDegree}, PR=${"%.3f".format(it.centralityScore)})"
            }

            val clusterGroups = notes.groupBy { it.folderName }.map { (folder, list) ->
                val sampleTitles = list.take(5).joinToString(", ") { "\"${it.title}\"" }
                "- Cluster '$folder' (${list.size} notes): $sampleTitles"
            }.joinToString("\n")

            val isolatedSample = notes.filter { n ->
                nodes.firstOrNull { it.id == n.id }?.degree == 0
            }.take(8).joinToString(", ") { "\"${it.title}\"" }

            val connectedness = if (notes.isNotEmpty()) {
                ((notes.size - analytics.orphanCount).toFloat() / notes.size.toFloat() * 100f)
            } else 0f

            val prompt = """
                You are the Monolith Mathematical Knowledge Synthesizer. You operate under strict mathematical, topological, and analytical rigor. You NEVER output conversational filler, polite chit-chat, or generic platitudes.

                Analyze this Obsidian knowledge vault:
                - Total Notes: ${notes.size}
                - Total Directed Wikilinks: ${analytics.totalLinks}
                - Graph Density: ${analytics.densityPercentage}%
                - Connectedness: ${"%.1f".format(connectedness)}%
                - Dominant Hubs:
                ${topHubs.joinToString("\n") { "  • $it" }}
                - Topological Clusters:
                $clusterGroups
                - Sample Isolated (0 degree) Notes:
                  $isolatedSample

                Return a JSON object conforming EXACTLY to this schema:
                {
                  "structuralAssessment": "Concise 2-sentence mathematical critique of the knowledge graph (density, hub distribution, power law fit, fragmentation risk).",
                  "bridges": [
                    {
                      "source": "Note Title from one cluster",
                      "target": "Note Title from another cluster",
                      "affinity": 0.95,
                      "rationale": "Exact point-to-point structural overlap explaining why linking these notes bridges knowledge gaps."
                    }
                  ],
                  "bottlenecks": [
                    {
                      "note": "Dominant hub note title",
                      "severity": "HIGH",
                      "vulnerability": "Why this node is a single point of failure and what subgraphs become isolated if removed."
                    }
                  ],
                  "entropyNotes": [
                    "Title of isolated note that has high intellectual value and needs inbound links"
                  ]
                }
            """.trimIndent()

            val responseJson = callGeminiJson(apiKey, model, prompt)
            val assessment = responseJson.optString("structuralAssessment", "Graph topology evaluated.")
            
            val bridges = ArrayList<VaultBridgeProposal>()
            val bridgesArr = responseJson.optJSONArray("bridges") ?: JSONArray()
            for (i in 0 until bridgesArr.length()) {
                val b = bridgesArr.optJSONObject(i) ?: continue
                bridges.add(
                    VaultBridgeProposal(
                        sourceTitle = b.optString("source"),
                        targetTitle = b.optString("target"),
                        affinityScore = b.optDouble("affinity", 0.9).toFloat(),
                        rationale = b.optString("rationale")
                    )
                )
            }

            val bottlenecks = ArrayList<VaultBottleneck>()
            val bottleArr = responseJson.optJSONArray("bottlenecks") ?: JSONArray()
            for (i in 0 until bottleArr.length()) {
                val b = bottleArr.optJSONObject(i) ?: continue
                bottlenecks.add(
                    VaultBottleneck(
                        noteTitle = b.optString("note"),
                        severity = b.optString("severity", "MEDIUM"),
                        vulnerability = b.optString("vulnerability")
                    )
                )
            }

            val entropy = ArrayList<String>()
            val entropyArr = responseJson.optJSONArray("entropyNotes") ?: JSONArray()
            for (i in 0 until entropyArr.length()) {
                val title = entropyArr.optString(i)
                if (title.isNotBlank()) entropy.add(title)
            }

            Result.success(
                VaultTopologySynthesis(
                    structuralAssessment = assessment,
                    bridgeProposals = bridges,
                    bottlenecks = bottlenecks,
                    entropyNotes = entropy
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Query Vault Neurons: Strict RAG synthesis grounded exclusively in active note neurons.
     */
    suspend fun queryVaultNeurons(
        apiKey: String,
        model: String = "gemini-2.0-flash",
        query: String,
        activeNeurons: List<ActiveNeuronCandidate>
    ): Result<NeuralQuerySynthesis> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("No API key configured."))
        if (activeNeurons.isEmpty()) {
            return@withContext Result.success(
                NeuralQuerySynthesis(
                    query = query,
                    directAnswer = "Your vault does not contain any notes or neurons relevant to this inquiry.",
                    keyPoints = listOf("No matching keywords or wikilink connections found in your notes."),
                    activeNeurons = emptyList(),
                    missingGaps = "No notes found matching '$query'. Consider creating a new note to start building knowledge on this topic.",
                    confidence = "LOW"
                )
            )
        }

        try {
            // Build the neuron context block
            val neuronsBlock = StringBuilder()
            activeNeurons.forEachIndexed { index, candidate ->
                neuronsBlock.appendLine("=== NEURON #${index + 1}: [[${candidate.note.displayTitle}]] ===")
                neuronsBlock.appendLine("Folder: ${candidate.note.folderName}")
                if (candidate.note.tags.isNotEmpty()) {
                    neuronsBlock.appendLine("Tags: ${candidate.note.tags.joinToString(", ") { "#$it" }}")
                }
                neuronsBlock.appendLine("Content:")
                neuronsBlock.appendLine(candidate.note.content.take(3500))
                neuronsBlock.appendLine("=== END NEURON ===")
                neuronsBlock.appendLine()
            }

            val prompt = """
                You are the Monolith Neural Retrieval Engine. You operate over the user's personal knowledge vault where each note acts as an active biological neuron.
                
                The user asks:
                "$query"

                Below are the ACTIVATED NEURONS retrieved from the user's vault:
                $neuronsBlock

                CRITICAL LAWS:
                1. STRICT ZERO-HALLUCINATION GROUNDING: You MUST base your entire answer EXCLUSIVELY on the text in the provided neurons above. Under NO CIRCUMSTANCES should you use outside knowledge, general assumptions, or unstated facts.
                2. CITATIONS: Every key point, fact, or inference MUST explicitly cite the contributing neuron using wikilink format [[Note Title]].
                3. HONEST BOUNDARIES: If the provided notes do not contain the answer, or only partially cover it, be completely transparent. Do NOT attempt to fill in blanks with outside general knowledge.
                4. CONFIDENCE: Set confidence to "HIGH" (directly and comprehensively answered by notes), "MODERATE" (partially answered or inferred from notes), or "LOW" (not addressed in notes).
                5. MISSING GAPS: Explicitly identify what related knowledge is missing from the user's vault notes on this subject, or null if fully covered.

                Return a JSON object conforming EXACTLY to this schema:
                {
                  "directAnswer": "Authoritative, concise synthesis answering the query directly from the notes.",
                  "keyPoints": [
                    "Point 1 with direct citation [[Note Title]]",
                    "Point 2 with direct citation [[Note Title]]"
                  ],
                  "neuronCitations": [
                    {
                      "title": "Exact display title of active neuron",
                      "relevance": "Concise 1-sentence explanation of how this neuron supported the answer"
                    }
                  ],
                  "missingGaps": "Identified knowledge gaps in the user's vault regarding this topic, or null",
                  "confidence": "HIGH" | "MODERATE" | "LOW"
                }
            """.trimIndent()

            val responseJson = callGeminiJson(apiKey, model, prompt)

            val directAnswer = responseJson.optString("directAnswer", "Analysis completed.")
            val keyPointsArr = responseJson.optJSONArray("keyPoints") ?: JSONArray()
            val keyPoints = ArrayList<String>()
            for (i in 0 until keyPointsArr.length()) {
                val pt = keyPointsArr.optString(i)
                if (pt.isNotBlank()) keyPoints.add(pt)
            }

            val citationsArr = responseJson.optJSONArray("neuronCitations") ?: JSONArray()
            val citations = ArrayList<ActiveNeuronCitation>()
            val candidateMap = activeNeurons.associateBy { it.note.displayTitle.lowercase().trim() }

            for (i in 0 until citationsArr.length()) {
                val obj = citationsArr.optJSONObject(i) ?: continue
                val title = obj.optString("title")
                val relevance = obj.optString("relevance")
                val matched = candidateMap[title.lowercase().trim()]
                citations.add(
                    ActiveNeuronCitation(
                        title = title,
                        noteId = matched?.note?.id,
                        relevance = relevance,
                        excerpt = matched?.excerpt.orEmpty()
                    )
                )
            }

            // Fallback: If AI returned empty neuron citations, populate from candidate active neurons
            if (citations.isEmpty()) {
                for (cand in activeNeurons) {
                    citations.add(
                        ActiveNeuronCitation(
                            title = cand.note.displayTitle,
                            noteId = cand.note.id,
                            relevance = "Activated with score ${cand.activationScore.toInt()}",
                            excerpt = cand.excerpt
                        )
                    )
                }
            }

            val missingGaps = responseJson.optString("missingGaps").takeIf { it.isNotBlank() && it != "null" }
            val confidence = responseJson.optString("confidence", "HIGH")

            Result.success(
                NeuralQuerySynthesis(
                    query = query,
                    directAnswer = directAnswer,
                    keyPoints = keyPoints,
                    activeNeurons = citations,
                    missingGaps = missingGaps,
                    confidence = confidence
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Point-to-Point Note Deconstruction (Axioms, Logical Tensions, Vault-Grounded Wikilinks, Action Vectors)
     */
    suspend fun analyzeNotePointByPoint(
        apiKey: String,
        model: String = "gemini-2.0-flash",
        note: Note,
        vaultTitles: List<String>
    ): Result<NotePointAnalysis> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("No API key configured."))

        try {
            // Provide a concise list of candidate vault titles so Gemini can discover real links
            val candidateTitles = vaultTitles
                .filter { !it.equals(note.title, ignoreCase = true) && it.isNotBlank() }
                .take(300)
                .joinToString(", ") { "\"$it\"" }

            val prompt = """
                You are the Monolith Point-to-Point Analytical Engine. Deconstruct the given note with axiomatic clarity. Zero conversational fluff, zero generic summaries, 100% precision.

                NOTE TITLE: "${note.title}"
                NOTE CONTENT:
                ${note.content.take(4000)}

                EXISTING VAULT NOTES (Candidate Wikilinks):
                [$candidateTitles]

                Perform a rigorous point-to-point structural analysis. Return a JSON object conforming EXACTLY to this schema:
                {
                  "premises": [
                    "Axiomatic foundational premise 1",
                    "Axiomatic foundational premise 2"
                  ],
                  "inferenceSteps": [
                    "Point-to-point deduction step A -> B",
                    "Point-to-point deduction step B -> C"
                  ],
                  "conclusions": [
                    "Core asserted outcome or principle"
                  ],
                  "logicalTensions": [
                    "Unproven assumption, logical leap, or vulnerability in reasoning"
                  ],
                  "suggestedWikilinks": [
                    {
                      "vaultTitle": "EXACT TITLE from candidate list above (NEVER hallucinate)",
                      "relevance": "Point-to-point concept intersection",
                      "suggestedPlacement": "Where or how to integrate [[vaultTitle]]"
                    }
                  ],
                  "actionVectors": [
                    "Verifiable action item 1",
                    "Verifiable action item 2"
                  ]
                }
            """.trimIndent()

            val responseJson = callGeminiJson(apiKey, model, prompt)

            fun jsonArrayToList(arr: JSONArray?): List<String> {
                if (arr == null) return emptyList()
                val list = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) list.add(s)
                }
                return list
            }

            val premises = jsonArrayToList(responseJson.optJSONArray("premises"))
            val inferences = jsonArrayToList(responseJson.optJSONArray("inferenceSteps"))
            val conclusions = jsonArrayToList(responseJson.optJSONArray("conclusions"))
            val tensions = jsonArrayToList(responseJson.optJSONArray("logicalTensions"))
            val actions = jsonArrayToList(responseJson.optJSONArray("actionVectors"))

            val wikilinks = ArrayList<NoteWikilinkSuggestion>()
            val linksArr = responseJson.optJSONArray("suggestedWikilinks") ?: JSONArray()
            for (i in 0 until linksArr.length()) {
                val item = linksArr.optJSONObject(i) ?: continue
                val vt = item.optString("vaultTitle")
                if (vt.isNotBlank()) {
                    wikilinks.add(
                        NoteWikilinkSuggestion(
                            vaultTitle = vt,
                            relevance = item.optString("relevance"),
                            suggestedPlacement = item.optString("suggestedPlacement")
                        )
                    )
                }
            }

            Result.success(
                NotePointAnalysis(
                    premises = premises,
                    inferenceSteps = inferences,
                    conclusions = conclusions,
                    logicalTensions = tensions,
                    suggestedWikilinks = wikilinks,
                    actionVectors = actions
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Jev-Style Instant Classifier (System-1 classification: Folder & Tags)
     */
    suspend fun classifyNote(
        apiKey: String,
        model: String = "gemini-2.0-flash",
        title: String,
        content: String,
        folders: List<String>,
        existingTags: List<String>
    ): Result<JevClassification> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("No API key configured."))

        try {
            val prompt = """
                You are Jev, a high-speed System-1 classification router for Monolith Notes.
                Classify this note into the optimal folder and assign precise #tags.

                TITLE: "$title"
                CONTENT PREVIEW:
                ${content.take(1500)}

                AVAILABLE FOLDERS:
                [${folders.joinToString(", ") { "\"$it\"" }}]

                KNOWN TAG TAXONOMY:
                [${existingTags.take(100).joinToString(", ") { "\"$it\"" }}]

                Return JSON matching this schema:
                {
                  "suggestedFolder": "One folder name from AVAILABLE FOLDERS or null",
                  "suggestedTags": ["tag1", "tag2"],
                  "confidence": 0.95
                }
            """.trimIndent()

            val responseJson = callGeminiJson(apiKey, model, prompt)
            val folder = responseJson.optString("suggestedFolder").takeIf { it.isNotBlank() && it != "null" }
            val tagsArr = responseJson.optJSONArray("suggestedTags") ?: JSONArray()
            val tags = ArrayList<String>()
            for (i in 0 until tagsArr.length()) {
                val t = tagsArr.optString(i).removePrefix("#")
                if (t.isNotBlank()) tags.add(t)
            }
            val confidence = responseJson.optDouble("confidence", 0.9).toFloat()

            Result.success(
                JevClassification(
                    suggestedFolder = folder,
                    suggestedTags = tags,
                    confidence = confidence
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Internal REST execution helper enforcing responseMimeType="application/json"
     */
    private fun callGeminiJson(apiKey: String, model: String, promptText: String): JSONObject {
        val primaryModel = if (model == "gemini-1.5-flash" || model.isBlank()) "gemini-2.0-flash" else model
        val candidateModels = linkedSetOf(
            primaryModel,
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-2.5-flash",
            "gemini-1.5-flash-latest"
        )

        var lastException: IOException? = null

        for (targetModel in candidateModels) {
            val url = "$BASE_URL/$targetModel:generateContent?key=${apiKey.trim()}"

            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptText)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.15) // Deterministic, rigorous analytical output
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val responseData: Pair<Int, String> = try {
                client.newCall(request).execute().use { response ->
                    Pair(response.code, response.body?.string().orEmpty())
                }
            } catch (e: Exception) {
                lastException = IOException("Network error: ${e.localizedMessage}", e)
                continue
            }

            val (code, body) = responseData
            if (code == 404) {
                // Model not supported or deprecated; cascade to next candidate model
                lastException = IOException(parseErrorMessage(code, body))
                continue
            }

            if (code !in 200..299) {
                val err = parseErrorMessage(code, body)
                throw IOException(err)
            }

            val rootJson = JSONObject(body)
            val candidates = rootJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IOException("Gemini returned empty response candidates.")
            }

            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text").orEmpty().trim()

            if (rawText.isBlank()) {
                throw IOException("Gemini returned empty text payload.")
            }

            val cleanedText = if (rawText.startsWith("```")) {
                rawText.substringAfter("\n").substringBeforeLast("```").trim()
            } else rawText

            // Parse structured JSON
            return JSONObject(cleanedText)
        }

        throw lastException ?: IOException("Failed to obtain response from Gemini models.")
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return try {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            val message = error?.optString("message") ?: "HTTP $code"
            when (code) {
                400 -> "API Key error or invalid request: $message"
                403 -> "API Key invalid or permissions denied: $message"
                404 -> "Model not found ($code): $message"
                429 -> "Rate limit reached. Gemini free tier allows 15 requests/min."
                500, 503 -> "Google Gemini server temporary error: $message"
                else -> "Gemini API error ($code): $message"
            }
        } catch (_: Exception) {
            "HTTP $code: $body"
        }
    }
}
