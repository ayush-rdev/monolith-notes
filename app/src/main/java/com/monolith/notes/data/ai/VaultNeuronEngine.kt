package com.monolith.notes.data.ai

import com.monolith.notes.model.Note
import kotlin.math.ln
import kotlin.math.min

/**
 * Data model representing an activated "neuron" (note) in the personal knowledge vault.
 */
data class ActiveNeuronCandidate(
    val note: Note,
    val activationScore: Float,
    val matchedTerms: List<String>,
    val excerpt: String
)

/**
 * VaultNeuronEngine: Biological Associative Memory & Spreading Activation Engine.
 *
 * Treats every note in the vault as an associative neuron:
 * 1. Lexical matching against titles, tags, and body content with BM25 term weighting.
 * 2. Synaptic Spreading Activation: Energy from activated nodes propagates along
 *    directed [[wikilinks]] to neighboring nodes, elevating conceptually connected thoughts.
 * 3. Extracts relevant grounded context excerpts to feed into the Gemini grounding pipeline.
 */
object VaultNeuronEngine {

    private val STOP_WORDS = hashSetOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and",
        "any", "are", "aren't", "as", "at", "be", "because", "been", "before", "being",
        "below", "between", "both", "but", "by", "can't", "cannot", "could", "couldn't",
        "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during",
        "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
        "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here",
        "here's", "hers", "herself", "him", "himself", "his", "how", "how's", "i",
        "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it",
        "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my",
        "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or",
        "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "same",
        "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
        "some", "such", "than", "that", "that's", "the", "their", "theirs", "them",
        "themselves", "then", "there", "there's", "these", "they", "they'd", "they'll",
        "they're", "they've", "this", "those", "through", "to", "too", "under",
        "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're",
        "we've", "were", "weren't", "what", "what's", "when", "when's", "where",
        "where's", "which", "while", "who", "who's", "whom", "why", "why's", "with",
        "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've",
        "your", "yours", "yourself", "yourselves", "tell", "give", "show", "find"
    )

    private val WIKILINK_REGEX = Regex("\\[\\[([^\\]|#]+)(?:[|#][^\\]]*)?\\]\\]")

    /**
     * Retrieve the highest-activating neurons (notes) for a given user query.
     *
     * @param query The user's natural language inquiry
     * @param notes Full collection of vault notes
     * @param maxNeurons Maximum number of active neurons to return (defaults to 7)
     */
    fun activateNeurons(
        query: String,
        notes: List<Note>,
        maxNeurons: Int = 7
    ): List<ActiveNeuronCandidate> {
        if (query.isBlank() || notes.isEmpty()) return emptyList()

        // 1. Tokenize query into meaningful search terms
        val rawTokens = query.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotBlank() }

        val searchTokens = rawTokens.filter { it.length > 2 && it !in STOP_WORDS }.ifEmpty {
            rawTokens.filter { it.length > 1 }
        }

        if (searchTokens.isEmpty()) return emptyList()

        val lowerQuery = query.lowercase().trim()

        // 2. Base Lexical Activation per Note
        val baseScores = HashMap<String, Float>(notes.size)
        val matchedTermsMap = HashMap<String, MutableList<String>>(notes.size)

        for (note in notes) {
            val titleLower = note.displayTitle.lowercase()
            val contentLower = note.content.lowercase()
            var score = 0f
            val matched = mutableListOf<String>()

            // Exact phrase match bonus
            if (lowerQuery.length > 3 && titleLower.contains(lowerQuery)) {
                score += 80f
                matched.add(note.displayTitle)
            } else if (lowerQuery.length > 3 && contentLower.contains(lowerQuery)) {
                score += 40f
            }

            // Token matching
            for (token in searchTokens) {
                var tokenMatched = false

                // Title match (high cognitive weight)
                if (titleLower.contains(token)) {
                    score += if (titleLower == token) 60f else 35f
                    tokenMatched = true
                }

                // Tag match
                for (tag in note.tags) {
                    if (tag.lowercase().contains(token)) {
                        score += 25f
                        tokenMatched = true
                        break
                    }
                }

                // Content term frequency (log-damped BM25 style)
                if (contentLower.contains(token)) {
                    val count = countOccurrences(contentLower, token)
                    if (count > 0) {
                        score += (10f * ln(1f + count.toFloat())).coerceAtMost(40f)
                        tokenMatched = true
                    }
                }

                if (tokenMatched) {
                    matched.add(token)
                }
            }

            if (score > 0f) {
                baseScores[note.id] = score
                matchedTermsMap[note.id] = matched
            }
        }

        // 3. Spreading Activation (Synaptic Link Propagation)
        // Build fast adjacency map by note displayTitle / ID
        val titleToNote = notes.associateBy { it.displayTitle.lowercase().trim() }
        val finalScores = HashMap<String, Float>(baseScores)

        // For each node with positive activation, propagate energy across wikilinks
        for (note in notes) {
            val sourceScore = baseScores[note.id] ?: continue
            if (sourceScore <= 5f) continue

            // Find outgoing wikilinks in this note's content
            val links = WIKILINK_REGEX.findAll(note.content).map { it.groupValues[1].trim().lowercase() }
            for (targetTitle in links) {
                val targetNote = titleToNote[targetTitle] ?: continue
                if (targetNote.id == note.id) continue

                // Propagate 25% associative synaptic energy
                val spreadEnergy = sourceScore * 0.25f
                finalScores[targetNote.id] = (finalScores[targetNote.id] ?: 0f) + spreadEnergy
            }
        }

        // 4. Sort and Extract Top Active Neurons
        val noteMap = notes.associateBy { it.id }
        val topCandidates = finalScores.entries
            .filter { it.value > 8f } // Minimum activation threshold
            .sortedByDescending { it.value }
            .take(maxNeurons)
            .mapNotNull { entry ->
                val note = noteMap[entry.key] ?: return@mapNotNull null
                val matched = matchedTermsMap[entry.key] ?: emptyList()
                val excerpt = extractContextExcerpt(note.content, searchTokens)

                ActiveNeuronCandidate(
                    note = note,
                    activationScore = entry.value,
                    matchedTerms = matched.distinct(),
                    excerpt = excerpt
                )
            }

        return topCandidates
    }

    /**
     * Extract a 250-character informative window around the most relevant match in content.
     */
    private fun extractContextExcerpt(content: String, tokens: List<String>): String {
        if (content.isBlank()) return "No content"
        val clean = content.replace(Regex("(?m)^#+\\s*"), "") // strip markdown headers
            .replace(Regex("\\[\\[([^\\]]+)\\]\\]"), "$1") // clean wikilinks
            .trim()

        val lower = clean.lowercase()

        // Find first occurrence of any token
        var bestIdx = -1
        for (token in tokens) {
            val idx = lower.indexOf(token)
            if (idx != -1 && (bestIdx == -1 || idx < bestIdx)) {
                bestIdx = idx
            }
        }

        if (bestIdx == -1) {
            return clean.take(240).replace('\n', ' ').trim() + (if (clean.length > 240) "..." else "")
        }

        val start = (bestIdx - 60).coerceAtLeast(0)
        val end = min(clean.length, start + 260)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < clean.length) "..." else ""

        return prefix + clean.substring(start, end).replace('\n', ' ').trim() + suffix
    }

    private fun countOccurrences(text: String, token: String): Int {
        var count = 0
        var idx = 0
        while (true) {
            idx = text.indexOf(token, idx)
            if (idx == -1) break
            count++
            idx += token.length
            if (count >= 15) break // cap to avoid excessive loops
        }
        return count
    }
}
