package app.morningreport

import androidx.compose.runtime.staticCompositionLocalOf

/** Every visible text, in English and Turkish. The device language picks one; anything else gets English. */
class Strings(val tr: Boolean) {
    private fun t(en: String, tr: String) = if (this.tr) tr else en

    val appName = t("Morning Report", "Sabah Raporu")
    val refresh = t("Refresh", "Yenile")
    val settings = t("Settings", "Ayarlar")
    val newSection = t("New section", "Yeni başlık")
    val emptyTitle = t("Make it yours", "Kendi raporunu kur")
    val emptyBody = t(
        "Create a section like Economy or Sports, then add the news sites and YouTube channels you follow.",
        "Ekonomi ya da Spor gibi bir başlık aç, sonra takip ettiğin haber sitelerini ve YouTube kanallarını ekle.",
    )
    val createSection = t("Create a section", "Başlık oluştur")
    val sectionName = t("Section name", "Başlık adı")
    val suggestions = if (tr) listOf("Ekonomi", "Spor", "Teknoloji", "Bilim", "Dünya", "Gündem") else listOf("Economy", "Sports", "Tech", "Science", "World", "Politics")
    val create = t("Create", "Oluştur")
    val save = t("Save", "Kaydet")
    val cancel = t("Cancel", "Vazgeç")
    val rename = t("Rename", "Yeniden adlandır")
    val delete = t("Delete", "Sil")
    val moveLeft = t("Move left", "Sola taşı")
    val moveRight = t("Move right", "Sağa taşı")
    val deleteSectionQ = t("Delete this section and its sources?", "Bu başlık ve kaynakları silinsin mi?")

    val addSource = t("Add source", "Kaynak ekle")
    val news = t("News", "Haber")
    val youtube = "YouTube"
    val newsHint = t("Site or feed link, e.g. theverge.com", "Site ya da RSS linki, örn. bbc.com/turkce")
    val youtubeHint = t("@channel or a channel / video link", "@kanal ya da kanal / video linki")
    val atTime = t("Check at a set time", "Belirli saatte kontrol et")
    val atTimeHelp = t(
        "Automatic checks read this source once a day, from this time on. Refresh always reads everything.",
        "Otomatik kontrol bu kaynağı günde bir kez, bu saatten sonra okur. Yenile her zaman hepsini okur.",
    )
    val timeHint = t("HH:MM", "SS:DD")
    val add = t("Add", "Ekle")
    val checking = t("Looking it up…", "Bakılıyor…")
    val noSources = t("No sources yet. Add a news site or a YouTube channel.", "Henüz kaynak yok. Bir haber sitesi ya da YouTube kanalı ekle.")
    val noItems = t("Nothing new yet. Pull to refresh or wait for the next check.", "Henüz yeni bir şey yok. Yenile'ye bas ya da sıradaki kontrolü bekle.")
    val videos = t("Videos", "Videolar")
    val articles = t("News", "Haberler")
    fun hiddenByAi(n: Int) = t("Hidden by AI ($n)", "AI'ın gizledikleri ($n)")
    val hiddenByAiLabel = t("hidden by AI filtering", "AI ayıklamasıyla gizlendi")
    val newShort = t("New", "Yeni")
    val openInYoutube = t("Open in YouTube", "YouTube'da aç")
    val sourceTime = t("Daily check time", "Günlük kontrol saati")
    val noTime = t("Every check", "Her kontrolde")
    val removeSource = t("Remove source", "Kaynağı kaldır")
    val openSite = t("Open site", "Siteyi aç")
    val lastChecked = t("Last checked", "Son kontrol")
    val never = t("never", "hiç")
    val failed = t("Could not read", "Okunamadı")

    fun newItems(n: Int) = if (n == 0) t("No new items", "Yeni bir şey yok") else t("$n new", "$n yeni")
    fun ago(ms: Long): String {
        val m = ms / 60_000
        return when {
            m < 1 -> t("now", "şimdi")
            m < 60 -> t("${m}m", "$m dk")
            m < 60 * 24 -> t("${m / 60}h", "${m / 60} sa")
            else -> t("${m / 1440}d", "${m / 1440} gün")
        }
    }

    fun error(code: String?): String = when (code) {
        "empty" -> t("Type something first.", "Önce bir şey yaz.")
        "not_youtube" -> t("That doesn't look like a YouTube channel.", "Bu bir YouTube kanalına benzemiyor.")
        "youtube_unreachable" -> t("YouTube could not be reached. Check the connection.", "YouTube'a ulaşılamadı. Bağlantıyı kontrol et.")
        "channel_not_found" -> t("No channel found at that address.", "O adreste bir kanal bulunamadı.")
        "not_a_site" -> t("That doesn't look like a web address.", "Bu bir web adresine benzemiyor.")
        "site_unreachable" -> t("The site could not be reached.", "Siteye ulaşılamadı.")
        "feed_not_found" -> t("This site has no news feed (RSS) we could find. Try the site's RSS link.", "Bu sitede bir haber akışı (RSS) bulunamadı. Sitenin RSS linkini dene.")
        "duplicate" -> t("This source is already in the section.", "Bu kaynak bu başlıkta zaten var.")
        "bad_time" -> t("Write the time as HH:MM, e.g. 07:30.", "Saati SS:DD olarak yaz, örn. 07:30.")
        else -> code ?: t("Something went wrong.", "Bir şeyler ters gitti.")
    }

    // AI settings
    val aiTitle = t("AI filtering (optional)", "AI ile ayıklama (isteğe bağlı)")
    val aiBody = t(
        "A language model you run yourself scores each new item against your interests. Low scores are folded away, never deleted. Off by default.",
        "Kendi çalıştırdığın bir dil modeli her yeni öğeyi ilgi alanlarına göre puanlar. Düşük puanlılar silinmez, sadece katlanır. Varsayılan olarak kapalı.",
    )
    val aiEnabled = t("Use AI filtering", "AI ile ayıkla")
    val aiEndpoint = t("Server address (OpenAI-compatible)", "Sunucu adresi (OpenAI uyumlu)")
    val aiModel = t("Model", "Model")
    val aiKey = t("API key (only for hosted services)", "API anahtarı (yalnız bulut servisleri için)")
    val aiInterests = t("Your interests, in your own words", "İlgi alanların, kendi cümlelerinle")
    val aiInterestsHint = t("e.g. Central bank decisions, NBA (Lakers), new AI models. Not celebrity news.", "örn. Merkez bankası kararları, NBA (Lakers), yeni yapay zekâ modelleri. Magazin değil.")
    fun aiThreshold(n: Int) = t("Hide below $n / 10", "$n / 10 altını gizle")
    val aiTest = t("Test and re-score", "Dene ve yeniden puanla")
    val aiOk = t("The model answered. Items were scored.", "Model cevap verdi. Öğeler puanlandı.")
    val aiHowTo = t("Setup guide", "Kurulum rehberi")
    val close = t("Close", "Kapat")
    val about = t(
        "Everything stays on this device. Feeds are read straight from the sites; there is no account and no server.",
        "Her şey bu cihazda kalır. Akışlar doğrudan sitelerden okunur; hesap ya da sunucu yok.",
    )
    val notifTitle = t("Morning Report", "Sabah Raporu")
    fun notifBody(n: Int) = t("$n new items in your sections", "Başlıklarında $n yeni öğe var")
}

val LocalStrings = staticCompositionLocalOf { Strings(false) }
