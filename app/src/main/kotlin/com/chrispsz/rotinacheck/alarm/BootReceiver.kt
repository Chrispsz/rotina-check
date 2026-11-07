package com.chrispsz.rotinacheck.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chrispsz.rotinacheck.data.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-arms the block chain after reboot / clock / timezone changes by
 * re-deriving the next event from the stored routine. BOOT_COMPLETED,
 * TIME_SET and TIMEZONE_CHANGED are protected system broadcasts, so
 * exported=true is safe — third-party apps cannot spoof them.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = Graph.store(appContext)
                BlockAlarmManager(appContext).reschedule(store.routineNow(), store.alertsNow())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
