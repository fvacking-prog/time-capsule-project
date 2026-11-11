package com.example.cyberpunk.data

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
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.cyberpunk.MainActivity
import com.example.cyberpunk.R
import com.timecapsule.shared.CapsuleDto
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

data class UnlockNotificationPlan(
    val capsuleId: String,
    val title: String,
    val creatorHandle: String?,
    val isInbox: Boolean,
    val unlockAtMillis: Long,
    val delayMillis: Long
)

object CapsuleUnlockScheduler {
    const val CHANNEL_ID = "capsule_unlock_notifications"
    const val WORK_TAG = "capsule_unlock"
    const val KEY_CAPSULE_ID = "capsule_id"
    const val KEY_CAPSULE_TITLE = "capsule_title"
    const val KEY_CREATOR_HANDLE = "creator_handle"
    const val KEY_IS_INBOX = "is_inbox"
    const val EXTRA_CAPSULE_ID = "extra_capsule_id"

    fun computePlans(
        capsules: List<CapsuleDto>,
        notificationsEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): List<UnlockNotificationPlan> {
        if (!notificationsEnabled) return emptyList()
        return capsules.mapNotNull { capsule ->
            if (capsule.archived || capsule.status == "OPENED" || capsule.status == "ARCHIVED") {
                return@mapNotNull null
            }
            val nextUnlockDate = capsule.stages
                .filter { !it.isUnlocked && it.unlockDate > nowMillis }
                .minOfOrNull { it.unlockDate }
                ?: return@mapNotNull null

            val delayMillis = (nextUnlockDate - nowMillis).coerceAtLeast(0L)
            UnlockNotificationPlan(
                capsuleId = capsule.id,
                title = capsule.title,
                creatorHandle = capsule.creatorHandle,
                isInbox = capsule.isInbox,
                unlockAtMillis = nextUnlockDate,
                delayMillis = delayMillis
            )
        }
    }

    fun sync(
        context: Context,
        capsules: List<CapsuleDto>,
        notificationsEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        workManager.cancelAllWorkByTag(WORK_TAG)
        val plans = computePlans(capsules, notificationsEnabled, nowMillis)
        for (plan in plans) {
            val inputData = Data.Builder()
                .putString(KEY_CAPSULE_ID, plan.capsuleId)
                .putString(KEY_CAPSULE_TITLE, plan.title)
                .putString(KEY_CREATOR_HANDLE, plan.creatorHandle)
                .putBoolean(KEY_IS_INBOX, plan.isInbox)
                .build()

            val request = OneTimeWorkRequestBuilder<CapsuleUnlockNotificationWorker>()
                .setInitialDelay(plan.delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(WORK_TAG)
                .build()

            workManager.enqueueUniqueWork(
                "capsule_unlock_${plan.capsuleId}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun cancelAll(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG) }
    }
}

class CapsuleUnlockNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val enabled = runCatching {
            UserPreferences.isNotificationsEnabledFlow(applicationContext).first()
        }.getOrDefault(true)
        if (!enabled) return Result.success()

        val capsuleId = inputData.getString(CapsuleUnlockScheduler.KEY_CAPSULE_ID).orEmpty()
        val capsuleTitle = inputData.getString(CapsuleUnlockScheduler.KEY_CAPSULE_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.notification_default_title)
        val creatorHandle = inputData.getString(CapsuleUnlockScheduler.KEY_CREATOR_HANDLE)
        val isInbox = inputData.getBoolean(CapsuleUnlockScheduler.KEY_IS_INBOX, false)

        if (capsuleId.isBlank()) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        ensureChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CapsuleUnlockScheduler.EXTRA_CAPSULE_ID, capsuleId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            capsuleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (isInbox && !creatorHandle.isNullOrBlank()) {
            applicationContext.getString(
                R.string.notification_inbox_unlock_body,
                capsuleTitle,
                creatorHandle
            )
        } else {
            applicationContext.getString(R.string.notification_unlock_body, capsuleTitle)
        }

        val notification = NotificationCompat.Builder(applicationContext, CapsuleUnlockScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.notification_unlock_title))
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(capsuleId.hashCode(), notification)
        }
        return Result.success()
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val existing = manager.getNotificationChannel(CapsuleUnlockScheduler.CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CapsuleUnlockScheduler.CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }
}

