package app.morningreport

import kotlinx.serialization.Serializable

@Serializable
enum class SourceKind { NEWS, YOUTUBE }

/**
 * One news site or YouTube channel inside a section.
 * [feedUrl] is what is actually read (RSS/Atom); [siteUrl] is where the source lives for people.
 * [time] ("HH:mm") is optional: when set, automatic checks read this source once a day after that time;
 * without it every automatic check reads it. A manual refresh always reads every source.
 */
@Serializable
data class Source(
    val id: String,
    val kind: SourceKind,
    val name: String,
    val feedUrl: String,
    val siteUrl: String,
    val time: String? = null,
    val lastChecked: Long = 0,
    val lastError: String? = null,
)

@Serializable
data class Section(
    val id: String,
    val title: String,
    val sources: List<Source> = emptyList(),
)

@Serializable
data class Item(
    val id: String,
    val sourceId: String,
    val title: String,
    val link: String,
    val published: Long,
    val summary: String = "",
    val thumbnail: String? = null,
    val fetchedAt: Long = 0,
    /** AI relevance 0–10, null when AI filtering was off or failed for this item. */
    val score: Int? = null,
)

/**
 * Optional AI filtering through any OpenAI-compatible chat endpoint. The default points at a local Ollama,
 * so nothing leaves the computer unless the person points it elsewhere.
 */
@Serializable
data class AiSettings(
    val enabled: Boolean = false,
    val endpoint: String = "http://localhost:11434/v1",
    val model: String = "gemma3:4b",
    val apiKey: String = "",
    val interests: String = "",
    val threshold: Int = 5,
    val lastError: String? = null,
)

@Serializable
data class AppData(
    val sections: List<Section> = emptyList(),
    val items: List<Item> = emptyList(),
    val ai: AiSettings = AiSettings(),
    val version: Int = 1,
)
