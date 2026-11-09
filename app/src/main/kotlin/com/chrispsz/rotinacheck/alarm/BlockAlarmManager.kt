package com.chrispsz.rotinacheck.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.chrispsz.rotinacheck.data.Routine
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The ONLY class allowed to talk to [AlarmManager].
 *
 * # Chain model (passive battery)
 *
 * Exactly ONE alarm is ever registered: the next block event that must
 * happen today. When it fires, the receiver shows one notification and
 * chains the next event — nothing polls, listens or stays resident. If the
 * day has no events left, the chain re-arms itself at 00:01 (daily anchor).
 *
 * # Exactness contract
 *
 * `SCHEDULE_EXACT_ALARM` (user-grantable, Play-safe for non-alarm-clock
 * apps). If revoked, degradation is graceful: `setWindow(±10 min)` — the
 * alert still arrives, only less precisely, and the UI surfaces a banner.
 */
class BlockAlarmManager(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean =
        alarmManager?.canScheduleExactAlarms() ?: false

    /** Deep-links to the system "Alarms & reminders" screen (banner action). */
    fun launchExactAlarmSettings() {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Registers [event] at [atEpochMs] (already resolved for the timezone). */
    @SuppressLint("MissingPermission")
    fun schedule(event: BlockEvent, atEpochMs: Long) {
        val am = alarmManager ?: return
        val pi = pendingIntent(event)
        if (am.canScheduleExactAlarms()) {
            // Fires even in Doze; while-idle throttling (~1/9 min) is
            // irrelevant at block-time granularity.
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, atEpochMs, FLEX_WINDOW_MS, pi)
        }
    }

    /**
     * Drops the currently registered alarm and re-plans from [now].
     *
     * Extras do NOT affect `filterEquals`, so cancelling with any event's
     * PendingIntent removes the single chained alarm — by design.
     */
    fun cancel() {
        val am = alarmManager ?: return
        am.cancel(pendingIntent(BlockEvent.DAILY_ANCHOR))
    }

    /**
     * One-call re-planning used by the receiver chain, boot/time receivers,
     * and every settings/editor change: next pending event today, or the
     * 00:01 anchor that opens tomorrow's schedule.
     */
    fun reschedule(
        routine: Routine?,
        alertsOn: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        now: ZonedDateTime = ZonedDateTime.now(zone),
    ) {
        cancel()
        if (!alertsOn || routine == null) return

        val today: LocalDate = now.toLocalDate()
        val events = SchedulePlanner.eventsForDate(routine, today) ?: return
        val next = SchedulePlanner.nextEvent(events, SchedulePlanner.nowMinutes(now))
        if (next != null) {
            schedule(next, SchedulePlanner.epochForMinutes(today, next.minutesOfDay, zone))
        } else {
            schedule(
                BlockEvent.DAILY_ANCHOR,
                SchedulePlanner.epochForMinutes(today.plusDays(1), BlockEvent.DAILY_ANCHOR.minutesOfDay, zone),
            )
        }
    }

    private fun pendingIntent(event: BlockEvent): PendingIntent {
        val intent = Intent(context, BlockAlarmReceiver::class.java)
            .setAction(ACTION_FIRE_BLOCK)
            .putExtra(EXTRA_BLOCK_ID, event.blockId)
            .putExtra(EXTRA_BLOCK_NAME, event.blockName)
            .putExtra(EXTRA_KIND, event.kind.ordinal)
            .putExtra(EXTRA_MINUTES, event.minutesOfDay)
        return PendingIntent.getBroadcast(
            context,
            event.code(),
            intent,
            // IMMUTABLE is mandatory since API 31; UPDATE_CURRENT keeps one PI.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE_BLOCK = "com.chrispsz.rotinacheck.action.FIRE_BLOCK"
        const val EXTRA_BLOCK_ID = "com.chrispsz.rotinacheck.extra.BLOCK_ID"
        const val EXTRA_BLOCK_NAME = "com.chrispsz.rotinacheck.extra.BLOCK_NAME"
        const val EXTRA_KIND = "com.chrispsz.rotinacheck.extra.KIND"
        const val EXTRA_MINUTES = "com.chrispsz.rotinacheck.extra.MINUTES"
        const val CHANNEL_ID = "block_alerts"
        private const val FLEX_WINDOW_MS = 10 * 60_000L
    }
}
