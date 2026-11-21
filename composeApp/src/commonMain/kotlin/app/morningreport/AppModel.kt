package app.morningreport

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Text being typed into the "new section" / "rename" dialog. */
class SectionDialog(val renameId: String? = null, initial: String = "") {
    var text by mutableStateOf(initial)
}

/** The "add source" dialog. Kept outside the composable so the demo recording can type into it. */
class SourceDialog(val sectionId: String) {
    var kind by mutableStateOf(SourceKind.NEWS)
    var input by mutableStateOf("")
    var timeOn by mutableStateOf(false)
    var time by mutableStateOf("08:00")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/** UI state above the data: which section is open and which dialog is showing. */
class AppModel(val repo: Repository, private val scope: CoroutineScope) {
    var selected by mutableStateOf(repo.data.value.sections.firstOrNull()?.id)
    var sectionDialog by mutableStateOf<SectionDialog?>(null)
    var sourceDialog by mutableStateOf<SourceDialog?>(null)
    var sourceMenu by mutableStateOf<String?>(null)
    var showSettings by mutableStateOf(false)
    /** The video open in the player dialog (Android); null when closed. */
    var playing by mutableStateOf<Item?>(null)
    var refreshing by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)

    fun confirmSection(strings: Strings) {
        val d = sectionDialog ?: return
        val title = d.text.trim()
        if (title.isEmpty()) return
        if (d.renameId != null) repo.renameSection(d.renameId, title)
        else selected = repo.addSection(title).id
        sectionDialog = null
    }

    fun confirmSource(strings: Strings) {
        val d = sourceDialog ?: return
        if (d.busy) return
        val time = if (d.timeOn) Schedule.normalize(d.time) ?: run { d.error = strings.error("bad_time"); return } else null
        d.busy = true
        d.error = null
        scope.launch {
            try {
                repo.addSource(d.sectionId, d.kind, d.input, time)
                if (sourceDialog === d) sourceDialog = null
            } catch (e: Resolver.Failure) {
                d.error = strings.error(e.message)
            } catch (e: Exception) {
                d.error = strings.error(null)
            } finally {
                d.busy = false
            }
        }
    }

    fun refresh(strings: Strings) {
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                val r = repo.refresh(onlyDue = false)
                toast = strings.newItems(r.newItems)
            } finally { refreshing = false }
        }
    }
}
