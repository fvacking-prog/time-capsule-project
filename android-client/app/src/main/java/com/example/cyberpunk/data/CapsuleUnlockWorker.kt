package com.example.cyberpunk.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@Serializable
data class UnlockNotificationPlan(
    val capsuleId: String,
    val title: String,
    val creatorHandle: String? = null,
    val isInbox: Boolean = false,
    val unlockAtMillis: Long,
    val delayMillis: Long,
    val isSocial: Boolean = isInbox
)

object CapsuleUnlockScheduler {
    const val CHANNEL_UNLOCK = "capsule_unlock"
    const val CHANNEL_SOCIAL = "capsule_social"
    const val CHANNEL_ANNIVERSARY = "capsule_anniversary"
    const val CHANNEL_ID = CHANNEL_UNLOCK

    const val WORK_TAG = "capsule_unlock"
    const val WORK_TAG_ANNIVERSARY = "capsule_anniversary"
    const val KEY_CAPSULE_ID = "capsule_id"
    const val KEY_CAPSULE_TITLE = "capsule_title"
    const val KEY_CREATOR_HANDLE = "creator_handle"
    const val KEY_IS_INBOX = "is_inbox"
    const val KEY_IS_SOCIAL = "is_social"
    const val EXTRA_CAPSULE_ID = "extra_capsule_id"

    const val INEXACT_WINDOW_MILLIS = 10L * 60L * 1000L // ~10 minutes
    private const val PREFS_NAME = "capsule_unlock_plans_prefs"
    private const val PREF_PLANS_JSON = "plans_json"
    private const val PREF_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val PREF_QUIET_HOURS_ENABLED = "quiet_hours_enabled"

    private val plansJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Переносит время уведомления из интервала тихих часов (23:00–08:00) на 08:00 утра.
     */
    fun applyQuietHours(
        triggerAtMillis: Long,
        quietHoursEnabled: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        if (!quietHoursEnabled) return triggerAtMillis
        val zdt = Instant.ofEpochMilli(triggerAtMillis).atZone(zoneId)
        val hour = zdt.hour
        return when {
            hour >= 23 -> {
                zdt.toLocalDate()
                    .plusDays(1)
                    .atTime(8, 0, 0, 0)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
            }
            hour < 8 -> {
                zdt.toLocalDate()
                    .atTime(8, 0, 0, 0)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
            }
            else -> triggerAtMillis
        }
    }

    fun isInQuietHours(
        nowMillis: Long,
        quietHoursEnabled: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (!quietHoursEnabled) return false
        val hour = Instant.ofEpochMilli(nowMillis).atZone(zoneId).hour
        return hour >= 23 || hour < 8
    }

    /**
     * Вычисляет время уведомления о годовщине (ровно через 1 год после открытия капсулы),
     * с учётом тихих часов (если включены).
     */
    fun computeAnniversaryTriggerMillis(
        openedAtMillis: Long,
        quietHoursEnabled: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val oneYearLater = Instant.ofEpochMilli(openedAtMillis)
            .atZone(zoneId)
            .plusYears(1)
            .toInstant()
            .toEpochMilli()
        return applyQuietHours(oneYearLater, quietHoursEnabled, zoneId)
    }

    fun computePlans(
        capsules: List<CapsuleDto>,
        notificationsEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        quietHoursEnabled: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault()
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

            val adjustedUnlockMillis = applyQuietHours(nextUnlockDate, quietHoursEnabled, zoneId)
            val delayMillis = (adjustedUnlockMillis - nowMillis).coerceAtLeast(0L)
            val isSocial = capsule.isInbox || capsule.isGroupCapsule
            UnlockNotificationPlan(
                capsuleId = capsule.id,
                title = capsule.title,
                creatorHandle = capsule.creatorHandle,
                isInbox = capsule.isInbox,
                unlockAtMillis = adjustedUnlockMillis,
                delayMillis = delayMillis,
                isSocial = isSocial
            )
        }
    }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val unlockChannel = NotificationChannel(
            CHANNEL_UNLOCK,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
        }

        val socialChannel = NotificationChannel(
            CHANNEL_SOCIAL,
            context.getString(R.string.notification_channel_social_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_social_desc)
        }

        val anniversaryChannel = NotificationChannel(
            CHANNEL_ANNIVERSARY,
            context.getString(R.string.notification_channel_anniversary_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_anniversary_desc)
        }

        manager.createNotificationChannels(listOf(unlockChannel, socialChannel, anniversaryChannel))
    }

    fun sync(
        context: Context,
        capsules: List<CapsuleDto>,
        notificationsEnabled: Boolean,
        quietHoursEnabled: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        ensureChannels(context)
        cancelScheduledAlarmsOnly(context)
        runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG) }

        val plans = computePlans(
            capsules = capsules,
            notificationsEnabled = notificationsEnabled,
            nowMillis = nowMillis,
            quietHoursEnabled = quietHoursEnabled
        )
        savePlansToStorage(context, plans, notificationsEnabled, quietHoursEnabled)

        if (!notificationsEnabled) {
            runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG_ANNIVERSARY) }
            return
        }

        for (plan in plans) {
            scheduleSingleAlarm(context, plan)
        }

        // Планируем уведомления о годовщине для открытых неархивных капсул
        capsules.forEach { capsule ->
            if (!capsule.archived && capsule.openedAt != null) {
                scheduleAnniversaryIfNeeded(
                    context = context,
                    capsuleId = capsule.id,
                    title = capsule.title,
                    openedAtMillis = capsule.openedAt,
                    quietHoursEnabled = quietHoursEnabled,
                    nowMillis = nowMillis
                )
            }
        }
    }

    fun scheduleAnniversaryIfNeeded(
        context: Context,
        capsuleId: String,
        title: String,
        openedAtMillis: Long,
        quietHoursEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val triggerAt = computeAnniversaryTriggerMillis(openedAtMillis, quietHoursEnabled)
        val delayMillis = triggerAt - nowMillis
        if (delayMillis <= 0L) return

        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val inputData = Data.Builder()
            .putString(KEY_CAPSULE_ID, capsuleId)
            .putString(KEY_CAPSULE_TITLE, title)
            .build()

        val request = OneTimeWorkRequestBuilder<CapsuleAnniversaryWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(WORK_TAG_ANNIVERSARY)
            .build()

        workManager.enqueueUniqueWork(
            "capsule_anniversary_$capsuleId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    internal fun scheduleSingleAlarm(context: Context, plan: UnlockNotificationPlan) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildAlarmPendingIntent(context, plan)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }

        if (canExact) {
            val scheduledExact = runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    plan.unlockAtMillis,
                    pendingIntent
                )
            }.isSuccess
            if (scheduledExact) return
        }

        runCatching {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                plan.unlockAtMillis,
                INEXACT_WINDOW_MILLIS,
                pendingIntent
            )
        }
    }

    private fun buildAlarmPendingIntent(context: Context, plan: UnlockNotificationPlan): PendingIntent {
        val intent = Intent(context, CapsuleUnlockAlarmReceiver::class.java).apply {
            action = "com.example.cyberpunk.ACTION_CAPSULE_UNLOCK_${plan.capsuleId}"
            putExtra(KEY_CAPSULE_ID, plan.capsuleId)
            putExtra(KEY_CAPSULE_TITLE, plan.title)
            putExtra(KEY_CREATOR_HANDLE, plan.creatorHandle)
            putExtra(KEY_IS_INBOX, plan.isInbox)
            putExtra(KEY_IS_SOCIAL, plan.isSocial)
        }
        return PendingIntent.getBroadcast(
            context,
            plan.capsuleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun rescheduleFromStorage(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        ensureChannels(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, true)
        val quietHoursEnabled = prefs.getBoolean(PREF_QUIET_HOURS_ENABLED, false)
        if (!notificationsEnabled) return

        val rawJson = prefs.getString(PREF_PLANS_JSON, null) ?: return
        val storedPlans = runCatching {
            plansJson.decodeFromString<List<UnlockNotificationPlan>>(rawJson)
        }.getOrDefault(emptyList())

        val activePlans = storedPlans.filter { it.unlockAtMillis > nowMillis - 60_000L }.map { plan ->
            val adjusted = applyQuietHours(plan.unlockAtMillis, quietHoursEnabled)
            plan.copy(
                unlockAtMillis = adjusted,
                delayMillis = (adjusted - nowMillis).coerceAtLeast(0L)
            )
        }
        for (plan in activePlans) {
            scheduleSingleAlarm(context, plan)
        }
    }

    private fun savePlansToStorage(
        context: Context,
        plans: List<UnlockNotificationPlan>,
        notificationsEnabled: Boolean,
        quietHoursEnabled: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = runCatching { plansJson.encodeToString(plans) }.getOrDefault("[]")
        prefs.edit()
            .putString(PREF_PLANS_JSON, encoded)
            .putBoolean(PREF_NOTIFICATIONS_ENABLED, notificationsEnabled)
            .putBoolean(PREF_QUIET_HOURS_ENABLED, quietHoursEnabled)
            .apply()
    }

    private fun cancelScheduledAlarmsOnly(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(PREF_PLANS_JSON, null) ?: return
        val storedPlans = runCatching {
            plansJson.decodeFromString<List<UnlockNotificationPlan>>(rawJson)
        }.getOrDefault(emptyList())

        for (plan in storedPlans) {
            runCatching {
                alarmManager.cancel(buildAlarmPendingIntent(context, plan))
            }
        }
    }

    fun cancelAll(context: Context) {
        cancelScheduledAlarmsOnly(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(PREF_PLANS_JSON).putBoolean(PREF_NOTIFICATIONS_ENABLED, false).apply()
        runCatching {
            val wm = WorkManager.getInstance(context)
            wm.cancelAllWorkByTag(WORK_TAG)
            wm.cancelAllWorkByTag(WORK_TAG_ANNIVERSARY)
        }
    }

    internal fun showUnlockNotification(
        context: Context,
        capsuleId: String,
        capsuleTitle: String,
        creatorHandle: String?,
        isInbox: Boolean,
        isSocial: Boolean
    ) {
        if (capsuleId.isBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CAPSULE_ID, capsuleId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            capsuleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (isInbox && !creatorHandle.isNullOrBlank()) {
            context.getString(
                R.string.notification_inbox_unlock_body,
                capsuleTitle,
                creatorHandle
            )
        } else {
            context.getString(R.string.notification_unlock_body, capsuleTitle)
        }

        val channelId = if (isSocial || isInbox) CHANNEL_SOCIAL else CHANNEL_UNLOCK
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_unlock_title))
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(capsuleId.hashCode(), notification)
        }
    }
}

class CapsuleUnlockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val prefs = context.getSharedPreferences("capsule_unlock_plans_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) return

        val quietHoursEnabled = prefs.getBoolean("quiet_hours_enabled", false)
        val capsuleId = intent.getStringExtra(CapsuleUnlockScheduler.KEY_CAPSULE_ID).orEmpty()
        if (capsuleId.isBlank()) return

        val capsuleTitle = intent.getStringExtra(CapsuleUnlockScheduler.KEY_CAPSULE_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notification_default_title)
        val creatorHandle = intent.getStringExtra(CapsuleUnlockScheduler.KEY_CREATOR_HANDLE)
        val isInbox = intent.getBooleanExtra(CapsuleUnlockScheduler.KEY_IS_INBOX, false)
        val isSocial = intent.getBooleanExtra(CapsuleUnlockScheduler.KEY_IS_SOCIAL, isInbox)

        val now = System.currentTimeMillis()
        if (CapsuleUnlockScheduler.isInQuietHours(now, quietHoursEnabled)) {
            val deferredMillis = CapsuleUnlockScheduler.applyQuietHours(now, quietHoursEnabled = true)
            val deferredPlan = UnlockNotificationPlan(
                capsuleId = capsuleId,
                title = capsuleTitle,
                creatorHandle = creatorHandle,
                isInbox = isInbox,
                unlockAtMillis = deferredMillis,
                delayMillis = (deferredMillis - now).coerceAtLeast(0L),
                isSocial = isSocial
            )
            CapsuleUnlockScheduler.scheduleSingleAlarm(context, deferredPlan)
            return
        }

        CapsuleUnlockScheduler.showUnlockNotification(
            context = context,
            capsuleId = capsuleId,
            capsuleTitle = capsuleTitle,
            creatorHandle = creatorHandle,
            isInbox = isInbox,
            isSocial = isSocial
        )
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            CapsuleUnlockScheduler.rescheduleFromStorage(context)
        }
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
        val isSocial = inputData.getBoolean(CapsuleUnlockScheduler.KEY_IS_SOCIAL, isInbox)

        CapsuleUnlockScheduler.showUnlockNotification(
            context = applicationContext,
            capsuleId = capsuleId,
            capsuleTitle = capsuleTitle,
            creatorHandle = creatorHandle,
            isInbox = isInbox,
            isSocial = isSocial
        )
        return Result.success()
    }
}

class CapsuleAnniversaryWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val enabled = runCatching {
            UserPreferences.isNotificationsEnabledFlow(applicationContext).first()
        }.getOrDefault(true)
        if (!enabled) return Result.success()

        val capsuleId = inputData.getString(CapsuleUnlockScheduler.KEY_CAPSULE_ID).orEmpty()
        if (capsuleId.isBlank()) return Result.success()

        val capsuleTitle = inputData.getString(CapsuleUnlockScheduler.KEY_CAPSULE_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.notification_default_title)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        CapsuleUnlockScheduler.ensureChannels(applicationContext)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CapsuleUnlockScheduler.EXTRA_CAPSULE_ID, capsuleId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            ("anniversary_$capsuleId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = applicationContext.getString(R.string.notification_anniversary_body, capsuleTitle)
        val notification = NotificationCompat.Builder(applicationContext, CapsuleUnlockScheduler.CHANNEL_ANNIVERSARY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.notification_anniversary_title))
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(("anniversary_$capsuleId").hashCode(), notification)
        }
        return Result.success()
    }
}
