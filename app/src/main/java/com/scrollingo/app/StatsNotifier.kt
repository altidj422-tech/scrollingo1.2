package com.scrollingo.app

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

/**
 * Shows a small, on-brand status-bar tracker with the learner's progress:
 * today's accuracy and their current correct-answer streak. Quiet by design
 * (IMPORTANCE_LOW, alerts only once) so it informs without nagging.
 *
 * All numbers come from [QuizProgress], which is local-only — nothing here is
 * uploaded or shared.
 */
object StatsNotifier {

    private const val CHANNEL_ID = "scrollingo_stats"
    private const val NOTIFICATION_ID = 4201

    /** Safe to call repeatedly; creating an existing channel is a no-op. */
    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Learning progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Your daily accuracy and answer streak"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasPostPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /** Refresh the tracker to reflect the latest stored stats. No-op without permission. */
    fun update(context: Context) = show(context, QuizProgress.snapshot(context))

    /** Refresh the tracker using an already-computed snapshot (avoids a re-read). */
    fun update(context: Context, snapshot: QuizProgress.Snapshot) = show(context, snapshot)

    /** Remove the tracker from the status bar (e.g. when the user turns it off). */
    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun show(context: Context, snapshot: QuizProgress.Snapshot) {
        // Respect the in-app toggle: if disabled, make sure nothing lingers.
        if (!Prefs.isStatsNotificationEnabled(context)) {
            cancel(context)
            return
        }
        if (!hasPostPermission(context)) return
        ensureChannel(context)

        val percent = snapshot.todayPercent
        val streak = snapshot.streak

        val title: String
        val summary: String
        val expanded: String
        if (snapshot.todayTotal == 0) {
            title = "Ready when you are"
            summary = if (streak > 0) "🔥 $streak streak · scroll to keep it going"
            else "Scroll a little, learn a word 🐱"
            expanded = if (streak > 0) {
                "Your $streak-answer streak is still alive 🔥\nBest so far: ${snapshot.bestStreak}."
            } else {
                "Open TikTok, Reels or Shorts — Scrollingo will quiz you between videos."
            }
        } else {
            title = "$percent% correct today"
            summary = "🔥 $streak in a row · ${snapshot.todayCorrect}/${snapshot.todayTotal} right"
            expanded = "You've answered ${snapshot.todayCorrect} of ${snapshot.todayTotal} right today.\n" +
                "🔥 Current streak: $streak   ·   Best: ${snapshot.bestStreak}"
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cat)
            .setColor(ContextCompat.getColor(context, R.color.purple))
            .setColorized(true)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)

        // Show today's accuracy as a determinate bar once there's something to show.
        if (snapshot.todayTotal > 0) {
            builder.setProgress(100, percent, false)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }
}
