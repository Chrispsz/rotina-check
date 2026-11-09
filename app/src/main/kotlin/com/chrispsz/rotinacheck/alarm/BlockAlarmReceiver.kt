package com.chrispsz.rotinacheck.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chrispsz.rotinacheck.R
import com.chrispsz.rotinacheck.data.Graph
import com.chrispsz.rotinacheck.data.TimeRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Thin receiver fired by the exact alarm (block start / end / daily anchor).
 *
 * Contract (zero-leak / zero-ANR):
 *  * NO database, NO services, NO WorkManager — one DataStore read inside
 *    `goAsync()` (the system grants a temporary WakeLock for it), one
 *    discreet notification, then the chain re-arms the next event.
 */
class BlockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BlockAlarmManager.ACTION_FIRE_BLOCK) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(appContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val kind = BlockEvent.Kind.entries.getOrElse(
            intent.getIntExtra(BlockAlarmManager.EXTRA_KIND, BlockEvent.Kind.RESCHEDULE_ANCHOR.ordinal),
        ) { BlockEvent.Kind.RESCHEDULE_ANCHOR }

        val store = Graph.store(context)
        val routine = store.routineNow()
        val alertsOn = store.alertsNow()

        if (kind != BlockEvent.Kind.RESCHEDULE_ANCHOR && alertsOn && routine != null) {
            notifyBlock(
                context = context,
                blockName = intent.getStringExtra(BlockAlarmManager.EXTRA_BLOCK_NAME).orEmpty(),
                kind = kind,
                minutes = intent.getIntExtra(BlockAlarmManager.EXTRA_MINUTES, 0),
            )
        }

        // Chain the next event (or tomorrow's anchor) — every path re-plans.
        BlockAlarmManager(context).reschedule(routine, alertsOn)
    }

    private fun notifyBlock(context: Context, blockName: String, kind: BlockEvent.Kind, minutes: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // UI banner asks for the permission; alerts degrade silently.
        }
        ensureChannel(context)

        val time = TimeRange.label(minutes)
        val body = context.getString(
            if (kind == BlockEvent.Kind.START) R.string.notif_start_body else R.string.notif_end_body,
            time,
        )
        val notification = NotificationCompat.Builder(context, BlockAlarmManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rotina)
            .setContentTitle(blockName)
            .setContentText(body)
            .setColor(ACCENT)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // discreet by design
            .build()

        val id = ("$blockName|$minutes|${kind.name}").hashCode() and 0x7FFFFFFF
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(BlockAlarmManager.CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            BlockAlarmManager.CHANNEL_ID,
            context.getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        val ACCENT = 0xFFF2B36B.toInt()
    }
}
