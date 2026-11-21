package app.morningreport

import kotlinx.coroutines.delay

/**
 * Drives the real UI for the README recording: it types into the same dialogs a person would use,
 * so the video shows the app exactly as it works. Started with `--demo` (desktop) or the `demo` extra (Android).
 * Sources are read live from the network.
 */
object Demo {
    class Add(val kind: SourceKind, val input: String, val time: String? = null)
    class Plan(val section: String, val sources: List<Add>)

    val plans = listOf(
        Plan("Tech", listOf(
            Add(SourceKind.NEWS, "theverge.com"),
            Add(SourceKind.YOUTUBE, "@mkbhd"),
            Add(SourceKind.NEWS, "techcrunch.com"),
            Add(SourceKind.YOUTUBE, "@Fireship"),
        )),
        Plan("Science", listOf(
            Add(SourceKind.YOUTUBE, "@veritasium"),
            Add(SourceKind.NEWS, "sciencedaily.com"),
            Add(SourceKind.YOUTUBE, "@kurzgesagt"),
        )),
        Plan("Sports", listOf(
            Add(SourceKind.NEWS, "espn.com", time = "08:00"),
            Add(SourceKind.YOUTUBE, "@NBA"),
            Add(SourceKind.NEWS, "bbc.com/sport"),
        )),
    )

    private suspend fun type(set: (String) -> Unit, text: String) {
        var s = ""
        for (ch in text) { s += ch; set(s); delay(55) }
    }

    suspend fun run(model: AppModel, strings: Strings) {
        delay(2200) // the empty start screen
        for (plan in plans) {
            val d = SectionDialog()
            model.sectionDialog = d
            delay(700)
            type({ d.text = it }, plan.section)
            delay(500)
            model.confirmSection(strings)
            delay(900)
            for (add in plan.sources) {
                val sd = SourceDialog(model.selected ?: return)
                model.sourceDialog = sd
                delay(600)
                if (add.kind != sd.kind) { sd.kind = add.kind; delay(450) }
                type({ sd.input = it }, add.input)
                delay(300)
                if (add.time != null) {
                    sd.timeOn = true; delay(500)
                    sd.time = ""; type({ sd.time = it }, add.time); delay(500)
                }
                model.confirmSource(strings)
                // Wait until the source is found and read (or it failed and the dialog shows why).
                var waited = 0
                while (model.sourceDialog === sd && waited < 25_000) { delay(200); waited += 200; if (sd.error != null) break }
                if (sd.error != null) { delay(1500); model.sourceDialog = null }
                delay(900)
            }
            delay(1500)
        }
        // The finished result: walk through the sections.
        for (plan in model.repo.data.value.sections) { model.selected = plan.id; delay(2600) }
        model.selected = model.repo.data.value.sections.firstOrNull()?.id
    }
}
