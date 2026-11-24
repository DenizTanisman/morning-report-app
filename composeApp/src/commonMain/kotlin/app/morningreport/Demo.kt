package app.morningreport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the real UI for the README recording (about 30 seconds): it types into the same dialogs a person would use,
 * so the video shows the app as it works. Started with `--demo` (desktop) or the `demo` extra (Android).
 *
 * Every source here is free to show: xkcd (CC BY-NC 2.5), Pepper&Carrot / David Revoy (CC BY 4.0),
 * Blender Studio (CC BY), NoCopyrightSounds (copyright-free music), NASA and USGS (US public domain).
 *
 * Sources are read live from the network, but they are looked up before the recorded part starts,
 * so the video does not sit on a waiting dialog. The first section is typed out in full; the other two
 * are shown as quick cuts (name and address already in the box, then the result).
 */
object Demo {
    class Add(val kind: SourceKind, val input: String)
    class Plan(val section: String, val sources: List<Add>, val quick: Boolean)

    val plans = listOf(
        Plan("Fun", listOf(
            Add(SourceKind.NEWS, "xkcd.com"),
            Add(SourceKind.NEWS, "peppercarrot.com"),
            Add(SourceKind.YOUTUBE, "@BlenderStudio"),
            Add(SourceKind.YOUTUBE, "@NoCopyrightSounds"),
        ), quick = false),
        Plan("Space", listOf(
            Add(SourceKind.NEWS, "nasa.gov"),
            Add(SourceKind.YOUTUBE, "@NASA"),
        ), quick = true),
        Plan("Earth", listOf(
            Add(SourceKind.NEWS, "earthobservatory.nasa.gov"),
            Add(SourceKind.YOUTUBE, "@USGS"),
        ), quick = true),
    )

    /** The video opened in the player: a Blender Studio film teaser when the feed has one (Shorts are vertical and load slowly). */
    private val playPick = Regex("teaser|trailer|open movie|short film", RegexOption.IGNORE_CASE)

    private suspend fun type(set: (String) -> Unit, text: String, perChar: Long = 45) {
        var s = ""
        for (ch in text) { s += ch; set(s); delay(perChar) }
    }

    /** While the demo plays, dialogs do not take focus, so no on-screen keyboard slides over the recording. */
    var running = false
        private set

    suspend fun run(model: AppModel, strings: Strings) = coroutineScope {
        running = true
        // Look every source up first (the empty start screen shows meanwhile; the recording is trimmed to start after it).
        val resolved = HashMap<String, Source>()
        for (plan in plans) for (add in plan.sources) {
            runCatching { withContext(Dispatchers.IO) { Resolver.resolve(add.kind, add.input) } }
                .onSuccess { resolved[add.input] = it }
        }
        delay(1000)

        var playSource: String? = null
        for (plan in plans) {
            val d = SectionDialog()
            model.sectionDialog = d
            if (plan.quick) { delay(60); d.text = plan.section; delay(600) }
            else { delay(450); type({ d.text = it }, plan.section, 70); delay(350) }
            model.confirmSection(strings)
            val sectionId = model.selected ?: return@coroutineScope
            delay(if (plan.quick) 350 else 450)
            for (add in plan.sources) {
                val sd = SourceDialog(sectionId)
                model.sourceDialog = sd
                sd.kind = add.kind
                if (plan.quick) { sd.input = add.input; delay(600) }
                else { delay(300); type({ sd.input = it }, add.input, 34); delay(200) }
                val src = resolved[add.input]
                if (src == null) {
                    // Not found ahead of time: go the normal way and wait for it.
                    model.confirmSource(strings)
                    var waited = 0
                    while (model.sourceDialog === sd && waited < 25_000 && sd.error == null) { delay(200); waited += 200 }
                    model.sourceDialog = null
                } else {
                    sd.busy = true
                    delay(if (plan.quick) 150 else 350)
                    val fresh = src.copy(id = newId())
                    model.repo.addResolvedSource(sectionId, fresh)
                    model.sourceDialog = null
                    if (add.input == "@BlenderStudio") playSource = fresh.id
                    launch { runCatching { model.repo.refresh(onlyDue = false, only = setOf(fresh.id)) } }
                }
                delay(if (plan.quick) 250 else 500)
            }
            delay(if (plan.quick) 1300 else 900)

            // After the first section: open a video in the player (where videos play inside the app).
            if (!plan.quick && inAppVideo && playSource != null) {
                var waited = 0
                var pick: Item? = null
                while (pick == null && waited < 15_000) {
                    val items = model.repo.data.value.items.filter { it.sourceId == playSource }
                    pick = items.firstOrNull { playPick.containsMatchIn(it.title) } ?: items.firstOrNull { "log" in it.title.lowercase() } ?: items.firstOrNull()
                    if (pick == null) { delay(200); waited += 200 }
                }
                if (pick != null) { model.playing = pick; delay(12_000); model.playing = null; delay(900) }
            }
        }
        // The finished result: the three sections one after another.
        for (s in model.repo.data.value.sections) { model.selected = s.id; delay(1100) }
        delay(800)
    }
}
