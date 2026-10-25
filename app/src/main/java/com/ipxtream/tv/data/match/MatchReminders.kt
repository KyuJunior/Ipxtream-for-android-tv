package com.ipxtream.tv.data.match

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ipxtream.tv.R
import com.ipxtream.tv.data.local.AppSettingsStore
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Which matches get a reminder, and when. Pure, so it is unit-tested.
 *
 * One notification per kick-off time (several matches starting together share it),
 * [LEAD_MS] before kick-off, at most [MAX_PER_DAY] a day.
 */
object MatchReminderPlan {
    const val LEAD_MS = 15 * 60_000L
    /** How far ahead the hourly scan schedules reminders. */
    const val LOOKAHEAD_MS = 3 * 60 * 60_000L
    /** How late after kick-off a delayed worker may still post the reminder. */
    const val GRACE_MS = 10 * 60_000L
    const val MAX_PER_DAY = 4

    data class Slot(val kickoffMs: Long, val notifyAtMs: Long)

    /** Upcoming kick-offs within [LOOKAHEAD_MS] that still need a reminder, earliest first. */
    fun slots(matches: List<Match>, nowMs: Long, notified: Set<String>): List<Slot> =
        matches.asSequence()
            .filter { it.state == MatchState.UPCOMING && it.kickoffMs > nowMs && it.kickoffMs - nowMs <= LOOKAHEAD_MS }
            .filter { it.id !in notified }
            .map { it.kickoffMs }
            .distinct()
            .sorted()
            .map { Slot(it, maxOf(nowMs, it - LEAD_MS)) }
            .toList()

    /** The matches to name when the reminder for [kickoffMs] fires (up to [GRACE_MS] after kick-off if the worker ran late). */
    fun due(matches: List<Match>, kickoffMs: Long, nowMs: Long, notified: Set<String>): List<Match> =
        if (kickoffMs + GRACE_MS < nowMs) emptyList()
        else matches.filter { it.kickoffMs == kickoffMs && it.state == MatchState.UPCOMING && it.id !in notified }
}

/** Schedules the reminder work and posts the notifications. */
object MatchReminders {

    const val CHANNEL_ID = "match_reminders"
    private const val SCAN_WORK = "match_reminders_scan"
    private const val TAG = "match_reminder"
    private const val KEY_KICKOFF = "kickoff"

    /** Starts (or stops) the hourly scan to match the setting. Safe to call on every app start. */
    fun sync(context: Context, enabled: Boolean, rescan: Boolean = false) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            val policy = if (rescan) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
            val scan = PeriodicWorkRequestBuilder<MatchReminderScanWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(SCAN_WORK, policy, scan)
        } else {
            wm.cancelUniqueWork(SCAN_WORK)
            wm.cancelAllWorkByTag(TAG)
        }
    }

    internal fun schedule(context: Context, slot: MatchReminderPlan.Slot, nowMs: Long) {
        val request = OneTimeWorkRequestBuilder<MatchReminderNotifyWorker>()
            .setInitialDelay(slot.notifyAtMs - nowMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_KICKOFF to slot.kickoffMs))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("match_reminder_${slot.kickoffMs}", ExistingWorkPolicy.KEEP, request)
    }

    internal fun kickoffOf(params: WorkerParameters): Long = params.inputData.getLong(KEY_KICKOFF, 0L)

    /** Today's fixtures, plus tomorrow's when kick-offs within the look-ahead can fall after midnight. */
    internal suspend fun upcoming(context: Context, leagues: List<String>, nowMs: Long): List<Match> {
        val repo = MatchRepository(context)
        val tz = TimeZone.getDefault()
        val today = repo.matchesFor(0, leagues).matches
        val nearMidnight = MatchDays.dayKey(nowMs + MatchReminderPlan.LOOKAHEAD_MS, tz) != MatchDays.dayKey(nowMs, tz)
        return if (nearMidnight) today + repo.matchesFor(1, leagues).matches else today
    }

    internal fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    internal fun post(context: Context, kickoffMs: Long, matches: List<Match>) {
        if (!canNotify(context)) return
        val text = com.ipxtream.tv.util.LocaleHelper.localized(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, text.getString(R.string.notif_match_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val names = matches.map { "${it.home.shortName.ifBlank { it.home.name }} – ${it.away.shortName.ifBlank { it.away.name }}" }
        // The kick-off time, not "in 15 minutes": WorkManager may run the reminder a little late.
        val time = android.text.format.DateFormat.getTimeFormat(text).format(java.util.Date(kickoffMs))
        val title = if (matches.size == 1) names.first()
                    else text.getString(R.string.notif_match_title_many, matches.size, time)
        val body = if (matches.size == 1) text.getString(R.string.notif_match_soon, time, matches.first().competition.name)
                   else names.joinToString("\n")
        val open = Intent(context, com.ipxtream.tv.ui.login.LoginActivity::class.java)
            .putExtra(com.ipxtream.tv.ui.login.LoginActivity.EXTRA_OPEN_MATCHES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val tap = PendingIntent.getActivity(
            context, kickoffMs.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brand_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setWhen(kickoffMs)
            .build()
        @Suppress("MissingPermission") // checked in canNotify
        NotificationManagerCompat.from(context).notify((kickoffMs / 60_000L).toInt(), notification)
    }
}

/** Remembers which matches were announced and how many reminders went out today. */
internal class MatchReminderLog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("match_reminders", Context.MODE_PRIVATE)

    /** Match ids already announced (entries older than two days are dropped). */
    fun notified(nowMs: Long): Set<String> = entries(nowMs).map { it.substringBefore('@') }.toSet()

    fun sentToday(nowMs: Long): Int =
        if (prefs.getInt("day", 0) == MatchDays.dayKey(nowMs, TimeZone.getDefault())) prefs.getInt("count", 0) else 0

    fun record(matches: List<Match>, nowMs: Long) {
        val today = MatchDays.dayKey(nowMs, TimeZone.getDefault())
        val kept = entries(nowMs) + matches.map { "${it.id}@${it.kickoffMs}" }
        prefs.edit()
            .putStringSet("notified", kept.toSet())
            .putInt("day", today)
            .putInt("count", sentToday(nowMs) + 1)
            .apply()
    }

    private fun entries(nowMs: Long): List<String> =
        prefs.getStringSet("notified", emptySet()).orEmpty()
            .filter { (it.substringAfter('@').toLongOrNull() ?: 0L) > nowMs - 2 * 24 * 60 * 60_000L }
}

/** Hourly: schedules a reminder for each upcoming kick-off of the followed leagues. */
class MatchReminderScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = AppSettingsStore.getInstance(applicationContext).settings.value
        if (!settings.matchReminders || settings.followedLeagues.isEmpty()) return Result.success()
        val now = System.currentTimeMillis()
        val matches = MatchReminders.upcoming(applicationContext, settings.followedLeagues, now)
        MatchReminderPlan.slots(matches, now, MatchReminderLog(applicationContext).notified(now))
            .forEach { MatchReminders.schedule(applicationContext, it, now) }
        return Result.success()
    }
}

/** Fires [MatchReminderPlan.LEAD_MS] before one kick-off time and posts its notification. */
class MatchReminderNotifyWorker(context: Context, private val params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = AppSettingsStore.getInstance(applicationContext).settings.value
        if (!settings.matchReminders) return Result.success()
        val kickoff = MatchReminders.kickoffOf(params)
        val now = System.currentTimeMillis()
        val log = MatchReminderLog(applicationContext)
        if (log.sentToday(now) >= MatchReminderPlan.MAX_PER_DAY) return Result.success()
        val matches = MatchReminders.upcoming(applicationContext, settings.followedLeagues, now)
        val due = MatchReminderPlan.due(matches, kickoff, now, log.notified(now))
        if (due.isEmpty()) return Result.success()
        MatchReminders.post(applicationContext, kickoff, due)
        log.record(due, now)
        return Result.success()
    }
}
