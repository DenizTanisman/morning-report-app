package app.morningreport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Optional: asks a language model how well each new item fits the person's interests (0–10).
 * Items under the threshold are not deleted, only folded away under "Hidden by AI".
 * Works with any OpenAI-compatible `/chat/completions` endpoint: Ollama, LM Studio, llama.cpp server, OpenAI…
 */
object AiFilter {
    private const val BATCH = 12
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns item id → score for the items it could score. Throws with a readable message when the endpoint fails. */
    fun score(settings: AiSettings, items: List<Pair<Item, String>>): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        items.chunked(BATCH).forEach { batch -> out += scoreBatch(settings, batch) }
        return out
    }

    private fun scoreBatch(settings: AiSettings, batch: List<Pair<Item, String>>): Map<String, Int> {
        val list = batch.mapIndexed { i, (item, sourceName) ->
            "${i + 1}. [$sourceName] ${item.title}" + item.summary.take(200).let { if (it.isBlank()) "" else " — $it" }
        }.joinToString("\n")
        val prompt = """
            |The reader's interests:
            |${settings.interests.ifBlank { "(not given: score general importance)" }}
            |
            |Rate how much the reader would want to see each item, from 0 (not at all) to 10 (must see).
            |Answer with JSON only, like {"scores":[7,2,9]}, one number per item, in order.
            |
            |$list
        """.trimMargin()
        val body = buildJsonObject {
            put("model", settings.model)
            put("temperature", 0)
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", "You sort a personal news feed. Reply with JSON only.") })
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            })
        }.toString()
        val url = settings.endpoint.trimEnd('/') + "/chat/completions"
        val raw = Http.postJson(url, body, settings.apiKey.ifBlank { null })
        val content = json.parseToJsonElement(raw).jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("The model gave no answer.")
        val scores = parseScores(content)
        return batch.mapIndexedNotNull { i, (item, _) -> scores.getOrNull(i)?.let { item.id to it.coerceIn(0, 10) } }.toMap()
    }

    /** Accepts `{"scores":[…]}`, a bare `[…]`, or JSON wrapped in prose / code fences. */
    fun parseScores(text: String): List<Int> {
        val start = text.indexOf('{').takeIf { it >= 0 }
        if (start != null) {
            val end = text.lastIndexOf('}')
            if (end > start) runCatching {
                val arr = json.parseToJsonElement(text.substring(start, end + 1)).jsonObject["scores"] as? JsonArray
                if (arr != null) return arr.map { it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.content.toDouble().toInt() }
            }
        }
        val a = text.indexOf('['); val b = text.lastIndexOf(']')
        if (a >= 0 && b > a) runCatching {
            return json.parseToJsonElement(text.substring(a, b + 1)).jsonArray.map { it.jsonPrimitive.content.toDouble().toInt() }
        }
        throw IllegalStateException("Could not read the model's answer.")
    }

    @Suppress("unused") private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
}
