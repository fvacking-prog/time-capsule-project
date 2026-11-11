package com.example.cyberpunk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.cyberpunk.R
import com.example.cyberpunk.data.UnlockDateTimeHelper
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoError
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSecondary
import com.example.cyberpunk.ui.theme.TokyoSuccess
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.example.cyberpunk.ui.viewmodel.CreateCapsuleFormState
import com.timecapsule.shared.UserDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCapsuleScreen(
    formState: CreateCapsuleFormState,
    currentUserId: String?,
    availableUsers: List<UserDto>,
    showConfirmDialog: Boolean = false,
    onTitleChange: (String) -> Unit,
    onKindChange: (String) -> Unit = {},
    onRecipientChange: (String) -> Unit = {},
    onTagsChange: (String) -> Unit = {},
    onMessageChange: (String) -> Unit = {},
    onDateChipClick: (String) -> Unit = {},
    onCustomDateSelect: (Long) -> Unit = {},
    onCustomTimeSelect: (Int, Int) -> Boolean = { _, _ -> true },
    onGeoLockedToggle: (Boolean) -> Unit = {},
    onTakeMyLocation: () -> Unit,
    onRadiusChange: (Float) -> Unit,
    onParticipantSearchQueryChange: (String) -> Unit,
    onToggleParticipant: (String) -> Unit,
    onAddStage: () -> Unit,
    onRemoveStage: (Int) -> Unit,
    onStageDateChange: (Int, Long) -> Unit,
    onStageTimeChange: (Int, Int, Int) -> Boolean = { _, _, _ -> true },
    onStageSecretChange: (Int, String) -> Unit,
    onOpenConfirmDialog: () -> Unit = {},
    onDismissConfirmDialog: () -> Unit = {},
    onSubmit: () -> Unit
) {
    val creatorId = currentUserId ?: ""
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
    }
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    var showDatePickerForStageIndex by remember { mutableStateOf<Int?>(null) }
    var showTimePickerForStageIndex by remember { mutableStateOf<Int?>(null) }

    // DatePicker Dialog
    if (showDatePickerForStageIndex != null) {
        val stageIdx = showDatePickerForStageIndex!!
        val initialMillis = if (stageIdx == -1) formState.unlockDateMillis else formState.stages[stageIdx].unlockDateMillis
        val todayUtcStart = remember {
            LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = maxOf(initialMillis, todayUtcStart),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcStart
                override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now(ZoneId.systemDefault()).year
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePickerForStageIndex = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = datePickerState.selectedDateMillis
                        if (selected != null) {
                            if (stageIdx == -1) {
                                onCustomDateSelect(selected)
                            } else {
                                onStageDateChange(stageIdx, selected)
                            }
                        }
                        showDatePickerForStageIndex = null
                        showTimePickerForStageIndex = stageIdx
                    }
                ) {
                    Text(stringResource(R.string.btn_ok), color = TokyoPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerForStageIndex = null }) {
                    Text(stringResource(R.string.btn_cancel), color = TokyoOnSurface)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // TimePicker Dialog (минимум +1 минута от текущего времени)
    if (showTimePickerForStageIndex != null) {
        val stageIdx = showTimePickerForStageIndex!!
        val baseMillis = if (stageIdx == -1) formState.unlockDateMillis else formState.stages[stageIdx].unlockDateMillis
        val nowMillis = System.currentTimeMillis()
        val earliestZdt = UnlockDateTimeHelper.earliestValidUnlockDateTime(nowMillis)
        val effectiveMillis = maxOf(baseMillis, earliestZdt.toInstant().toEpochMilli())
        val initialZdt = Instant.ofEpochMilli(effectiveMillis).atZone(ZoneId.systemDefault())

        val timePickerState = rememberTimePickerState(
            initialHour = initialZdt.hour,
            initialMinute = initialZdt.minute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePickerForStageIndex = null },
            containerColor = TokyoSurface,
            title = {
                Text(
                    text = stringResource(R.string.time_picker_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    )
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = TokyoBackground,
                            selectorColor = TokyoPrimary,
                            containerColor = TokyoSurface,
                            clockDialSelectedContentColor = TokyoBackground,
                            clockDialUnselectedContentColor = TokyoOnBackground,
                            timeSelectorSelectedContainerColor = TokyoPrimary.copy(alpha = 0.25f),
                            timeSelectorUnselectedContainerColor = TokyoBackground,
                            timeSelectorSelectedContentColor = TokyoPrimary,
                            timeSelectorUnselectedContentColor = TokyoOnBackground
                        )
                    )
                    Text(
                        text = stringResource(R.string.time_picker_min_hint),
                        style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val accepted = if (stageIdx == -1) {
                            onCustomTimeSelect(timePickerState.hour, timePickerState.minute)
                        } else {
                            onStageTimeChange(stageIdx, timePickerState.hour, timePickerState.minute)
                        }
                        if (accepted) {
                            showTimePickerForStageIndex = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_ok), color = TokyoPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerForStageIndex = null }) {
                    Text(stringResource(R.string.btn_cancel), color = TokyoOnSurface)
                }
            }
        )
    }

    // Confirmation Dialog before sealing
    if (showConfirmDialog) {
        val dateStr = runCatching {
            dateFormatter.format(Instant.ofEpochMilli(formState.unlockDateMillis))
        }.getOrDefault("${formState.unlockDateMillis}")

        AlertDialog(
            onDismissRequest = onDismissConfirmDialog,
            title = {
                Text(
                    text = stringResource(R.string.confirm_seal_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.confirm_seal_msg, dateStr),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnSurface)
                )
            },
            confirmButton = {
                Button(
                    onClick = onSubmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TokyoPrimary,
                        contentColor = TokyoBackground
                    )
                ) {
                    Text(stringResource(R.string.btn_seal_capsule))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirmDialog) {
                    Text(stringResource(R.string.btn_cancel), color = TokyoOnSurface)
                }
            },
            containerColor = TokyoSurface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokyoBackground)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Запечатать кусочек сегодняшнего дня",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TokyoPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Создайте капсулу времени, которая откроется только в будущем",
                style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnSurface)
            )
        }

        // ШАГ 1: ТИП КАПСУЛЫ
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.create_step_type),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoOnBackground
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Сообщение (Active)
                    FilterChip(
                        selected = formState.kind == "MESSAGE",
                        onClick = { onKindChange("MESSAGE") },
                        label = { Text(stringResource(R.string.type_message)) },
                        leadingIcon = { Icon(Icons.Default.TextFields, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TokyoPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = TokyoPrimary,
                            selectedLeadingIconColor = TokyoPrimary
                        )
                    )

                    // Фото (Скоро)
                    FilterChip(
                        selected = false,
                        onClick = { },
                        enabled = false,
                        label = { Text("${stringResource(R.string.type_photo)} • ${stringResource(R.string.badge_soon)}") },
                        leadingIcon = { Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp)) }
                    )

                    // Голос (Скоро)
                    FilterChip(
                        selected = false,
                        onClick = { },
                        enabled = false,
                        label = { Text("${stringResource(R.string.type_voice)} • ${stringResource(R.string.badge_soon)}") },
                        leadingIcon = { Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // ШАГ 2: СОДЕРЖИМОЕ
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.create_step_content),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoOnBackground
                    )
                )

                // Title
                OutlinedTextField(
                    value = formState.title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.field_title_label)) },
                    placeholder = { Text(stringResource(R.string.field_title_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TokyoOnBackground,
                        unfocusedTextColor = TokyoOnBackground,
                        focusedBorderColor = TokyoPrimary,
                        unfocusedBorderColor = TokyoPrimary.copy(alpha = 0.25f)
                    )
                )

                // Message Text
                val messageText = formState.messageText.ifEmpty { formState.stages.firstOrNull()?.secretText.orEmpty() }
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageChange,
                    label = { Text(stringResource(R.string.field_message_label)) },
                    placeholder = { Text(stringResource(R.string.field_message_placeholder)) },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TokyoOnBackground,
                        unfocusedTextColor = TokyoOnBackground,
                        focusedBorderColor = TokyoPrimary,
                        unfocusedBorderColor = TokyoPrimary.copy(alpha = 0.25f)
                    )
                )

                // Recipient
                OutlinedTextField(
                    value = formState.recipient,
                    onValueChange = onRecipientChange,
                    label = { Text(stringResource(R.string.field_recipient_label)) },
                    placeholder = { Text(stringResource(R.string.field_recipient_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TokyoOnBackground,
                        unfocusedTextColor = TokyoOnBackground,
                        focusedBorderColor = TokyoSecondary,
                        unfocusedBorderColor = TokyoSecondary.copy(alpha = 0.25f)
                    )
                )

                // Tags
                OutlinedTextField(
                    value = formState.tags,
                    onValueChange = onTagsChange,
                    label = { Text(stringResource(R.string.field_tags_label)) },
                    placeholder = { Text(stringResource(R.string.field_tags_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TokyoOnBackground,
                        unfocusedTextColor = TokyoOnBackground,
                        focusedBorderColor = TokyoAccent,
                        unfocusedBorderColor = TokyoAccent.copy(alpha = 0.25f)
                    )
                )
            }
        }

        // ШАГ 3: ДАТА ОТКРЫТИЯ
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.create_step_date),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoOnBackground
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = formState.selectedDateChip == "TOMORROW",
                        onClick = { onDateChipClick("TOMORROW") },
                        label = { Text(stringResource(R.string.chip_tomorrow)) }
                    )

                    FilterChip(
                        selected = formState.selectedDateChip == "WEEK",
                        onClick = { onDateChipClick("WEEK") },
                        label = { Text(stringResource(R.string.chip_week)) }
                    )

                    FilterChip(
                        selected = formState.selectedDateChip == "MONTH",
                        onClick = { onDateChipClick("MONTH") },
                        label = { Text(stringResource(R.string.chip_month)) }
                    )

                    FilterChip(
                        selected = formState.selectedDateChip == "CUSTOM",
                        onClick = { showDatePickerForStageIndex = -1 },
                        label = { Text(stringResource(R.string.chip_custom_date)) },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp)) }
                    )

                    val timeStr = runCatching {
                        timeFormatter.format(Instant.ofEpochMilli(formState.unlockDateMillis))
                    }.getOrDefault("00:00")
                    FilterChip(
                        selected = formState.selectedDateChip == "CUSTOM",
                        onClick = { showTimePickerForStageIndex = -1 },
                        label = { Text(stringResource(R.string.chip_custom_time, timeStr)) },
                        leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TokyoAccent.copy(alpha = 0.2f),
                            selectedLabelColor = TokyoAccent
                        )
                    )
                }

                // Карточка-итог
                val dateStr = runCatching {
                    dateFormatter.format(Instant.ofEpochMilli(formState.unlockDateMillis))
                }.getOrDefault("${formState.unlockDateMillis}")
                val remainingMillis = maxOf(0L, formState.unlockDateMillis - System.currentTimeMillis())
                val daysLeft = remainingMillis / (24L * 60L * 60L * 1000L)
                val hoursLeft = (remainingMillis / (60L * 60L * 1000L)) % 24L
                val minutesLeft = maxOf(1L, (remainingMillis + 59_999L) / 60_000L)
                val remainingText = when {
                    daysLeft >= 1L -> stringResource(R.string.summary_days_left, daysLeft)
                    hoursLeft >= 1L -> stringResource(
                        R.string.summary_hours_minutes_left,
                        hoursLeft,
                        (remainingMillis / 60_000L) % 60L
                    )
                    else -> stringResource(R.string.summary_minutes_left, minutesLeft)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TokyoBackground.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.summary_card_title),
                            style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$dateStr ($remainingText)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TokyoAccent
                            )
                        )
                    }
                }
            }
        }

        // ПЕРЕКЛЮЧАТЕЛЬ: Открыть только на месте (гео + друзья)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.geo_lock_switch),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TokyoOnBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.geo_lock_hint),
                            style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                        )
                    }

                    Switch(
                        checked = formState.isGeoLocked,
                        onCheckedChange = onGeoLockedToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TokyoBackground,
                            checkedTrackColor = TokyoPrimary
                        )
                    )
                }

                // Расширенные гео-поля (показываются только если переключатель включён)
                if (formState.isGeoLocked) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Координаты
                    OutlinedButton(
                        onClick = onTakeMyLocation,
                        enabled = !formState.isFetchingLocation,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TokyoPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (formState.isFetchingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = TokyoPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_take_my_location))
                        }
                    }

                    if (formState.latitude != null && formState.longitude != null) {
                        Text(
                            text = stringResource(R.string.location_coords_format, formState.latitude, formState.longitude),
                            style = MaterialTheme.typography.bodySmall.copy(color = TokyoAccent)
                        )
                    }

                    // Радиус
                    Column {
                        Text(
                            text = stringResource(R.string.radius_slider_format, formState.radiusMeters),
                            style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                        )
                        Slider(
                            value = formState.radiusMeters,
                            onValueChange = onRadiusChange,
                            valueRange = 10f..500f,
                            colors = SliderDefaults.colors(
                                thumbColor = TokyoPrimary,
                                activeTrackColor = TokyoPrimary
                            )
                        )
                    }

                    // Участники
                    Text(
                        text = stringResource(R.string.participants_select_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TokyoOnBackground
                        )
                    )

                    OutlinedTextField(
                        value = formState.participantSearchQuery,
                        onValueChange = onParticipantSearchQueryChange,
                        placeholder = { Text(stringResource(R.string.participants_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TokyoPrimary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    availableUsers.take(6).forEach { user ->
                        val isCreator = user.id == creatorId
                        val isSelected = isCreator || formState.selectedParticipantIds.contains(user.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCreator) { onToggleParticipant(user.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = if (isCreator) null else { _ -> onToggleParticipant(user.id) },
                                colors = CheckboxDefaults.colors(checkedColor = TokyoPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${user.username} ${if (isCreator) stringResource(R.string.creator_mandatory_note) else ""}",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnBackground)
                            )
                        }
                    }
                }
            }
        }

        // Предупреждающий блок: Если закрыто, то закрыто
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TokyoError.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(TokyoError.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = TokyoError,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.rule_sealed_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TokyoError
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.rule_sealed_desc),
                        style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                    )
                }
            }
        }

        // Кнопка: Запечатать капсулу
        Button(
            onClick = onOpenConfirmDialog,
            enabled = !formState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TokyoPrimary,
                contentColor = TokyoBackground
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (formState.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = TokyoBackground,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_seal_capsule),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
