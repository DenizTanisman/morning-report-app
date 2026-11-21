package app.morningreport

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

/** One repository per process, shared by the screen and the background check. */
object Graph {
    @Volatile private var repo: Repository? = null
    fun repo(ctx: Context): Repository = repo ?: synchronized(this) {
        repo ?: run {
            val file = File(ctx.filesDir, "data.json")
            Repository(
                load = { file.takeIf { it.exists() }?.readText() },
                save = { text -> File(ctx.filesDir, "data.json.tmp").apply { writeText(text) }.renameTo(file) },
            ).also { repo = it }
        }
    }
}

class MorningReportApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Every 15 minutes (the shortest Android allows) the worker reads the sources that are due.
        val req = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("check", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val r = runCatching { Graph.repo(applicationContext).refresh(onlyDue = true) }.getOrNull() ?: return Result.success()
        if (r.newItems > 0) notify(applicationContext, r.newItems)
        return Result.success()
    }

    private fun notify(ctx: Context, n: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val s = Strings(systemLanguage() == "tr")
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("new", s.appName, NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val note = android.app.Notification.Builder(ctx, "new")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.notifTitle)
            .setContentText(s.notifBody(n))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1, note)
    }
}

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = Graph.repo(this)
        val demo = intent.getBooleanExtra("demo", false)
        val strings = when (intent.getStringExtra("lang")) { "tr" -> Strings(true); "en" -> Strings(false); else -> Strings(systemLanguage() == "tr") }
        setContent {
            val scope = rememberCoroutineScope()
            val model = remember { AppModel(repo, scope) }
            LaunchedEffect(Unit) {
                if (demo) Demo.run(model, strings)
                else {
                    if (Build.VERSION.SDK_INT >= 33 && repo.data.value.sections.isNotEmpty() &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    runCatching { repo.refresh(onlyDue = true) }
                }
            }
            App(model, strings)
        }
    }
}
