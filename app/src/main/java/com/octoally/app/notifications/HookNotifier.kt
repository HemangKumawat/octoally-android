package com.octoally.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.octoally.app.MainActivity
import com.octoally.core.network.AgentEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Surfaces server-fired hook notifications as Android heads-up notifications.
 *
 * Only 3 of the 6 server hook types become heads-up alerts:
 *   - session_complete — Claude finished work
 *   - error_spike      — errors rising
 *   - tool_denied      — tool use was blocked
 *
 * The remaining 3 (rate_limit, budget_warn, model_change) are dropped here.
 *   - model_change is informational and Task #8's RouteDecision surfaces it in-UI.
 *   - rate_limit and budget_warn are logged-only until we have a settings UX
 *     for the user to opt in (pass-3).
 */
@Singleton
class HookNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ALERTS_CHANNEL_ID = "octoally_alerts"
        const val ALERTS_CHANNEL_NAME = "OctoAlly Alerts"
        const val ALERTS_CHANNEL_DESC = "Session completion, errors, and denied tool use"

        const val EXTRA_NAVIGATE_SESSION = "navigate_to_session"

        private val HEADS_UP_TYPES = setOf("session_complete", "error_spike", "tool_denied")
    }

    /** Create (or update) the high-importance alerts channel. Idempotent. */
    fun ensureChannel() {
        val channel = NotificationChannel(
            ALERTS_CHANNEL_ID,
            ALERTS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ALERTS_CHANNEL_DESC
            enableLights(true)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /** Show a heads-up notification for the 3 actionable hook types; drop the rest. */
    fun notify(hook: AgentEvent.HookNotification) {
        if (hook.type !in HEADS_UP_TYPES) return

        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            hook.sessionId?.let { putExtra(EXTRA_NAVIGATE_SESSION, it) }
        }
        val pending = PendingIntent.getActivity(
            context,
            stableId(hook),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (hook.severity) {
            AgentEvent.Severity.ERROR -> NotificationCompat.PRIORITY_HIGH
            AgentEvent.Severity.WARN -> NotificationCompat.PRIORITY_DEFAULT
            AgentEvent.Severity.INFO -> NotificationCompat.PRIORITY_LOW
        }

        val defaults = when (hook.severity) {
            AgentEvent.Severity.ERROR ->
                Notification.DEFAULT_SOUND or
                    Notification.DEFAULT_VIBRATE or
                    Notification.DEFAULT_LIGHTS
            AgentEvent.Severity.WARN -> Notification.DEFAULT_VIBRATE
            AgentEvent.Severity.INFO -> 0
        }

        val builder = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(hook.title)
            .setContentText(hook.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(hook.message))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(
                when (hook.severity) {
                    AgentEvent.Severity.ERROR -> NotificationCompat.CATEGORY_ERROR
                    AgentEvent.Severity.WARN -> NotificationCompat.CATEGORY_STATUS
                    AgentEvent.Severity.INFO -> NotificationCompat.CATEGORY_STATUS
                }
            )
        if (defaults != 0) builder.setDefaults(defaults) else builder.setSilent(true)

        try {
            nm.notify(stableId(hook), builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and now — drop silently.
        }
    }

    /** Stable-per-(type+session) id so same-kind alerts replace rather than stack. */
    private fun stableId(hook: AgentEvent.HookNotification): Int {
        val key = hook.type + "|" + (hook.sessionId ?: "")
        // Positive-only so it doesn't collide with SessionSyncService's NOTIF_ID=1001.
        return (key.hashCode() and 0x7FFFFFFF) or 0x10000
    }
}
