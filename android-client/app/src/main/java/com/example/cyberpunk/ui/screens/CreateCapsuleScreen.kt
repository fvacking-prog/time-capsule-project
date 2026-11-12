package com.example.cyberpunk.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.cyberpunk.R
import com.example.cyberpunk.data.MediaHelper
import com.example.cyberpunk.data.UnlockDateTimeHelper
import com.example.cyberpunk.data.VoicePlayerHelper
import com.example.cyberpunk.data.VoiceRecorderHelper
import kotlinx.coroutines.delay
import java.io.File
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateCapsuleScreen(
    formState: CreateCapsuleFormState,
    currentUserId: String?,
    availableUsers: List<UserDto>,
    showConfirmDialog: Boolean = false,
    onTitleChange: (String) -> Unit,
    onKindChange: (String) -> Unit = {},
    onRecipientChange: (String) -> Unit = {},
    onRecipientSearchQueryChange: (String) -> Unit = {},
    onAddRecipient: (UserDto) -> Unit = {},
    onRemoveRecipient: (String) -> Unit = {},
    onTagsChange: (String) -> Unit = {},
    onMessageChange: (String) -> Unit = {},
    onDateChipClick: (String) -> Unit = {},
    onCustomDateSelect: (Long) -> Unit = {},
    onCustomTimeSelect: (Int, Int) -> Boolean = { _, _ -> true },
    onGeoLockedToggle: (Boolean) -> Unit = {},
    onTakeMyLocation: (isCoarseOnly: Boolean) -> Unit = {},
    onCancelLocationFetch: () -> Unit = {},
    onShowPermissionDeniedSnackbar: () -> Unit = {},
    showLocationDisabledDialog: Boolean = false,
    onDismissLocationDisabledDialog: () -> Unit = {},
    onPhotoSelected: (ByteArray, String) -> Unit = { _, _ -> },
    onPhotoRemoved: () -> Unit = {},
    onVoiceRecorded: (File, String, Long) -> Unit = { _, _, _ -> },
    onVoiceRemoved: () -> Unit = {},
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
    val context = LocalContext.current

    val voiceRecorder = remember { VoiceRecorderHelper(context) }
    val voicePlayer = remember { VoicePlayerHelper() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var isPlayingVoice by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            onCancelLocationFetch()
            voiceRecorder.release()
            voicePlayer.release()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val processed = MediaHelper.processAndCompressImage(context, uri)
            if (processed != null) {
                onPhotoSelected(processed.first, processed.second)
            }
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val processed = MediaHelper.processAndCompressImage(context, uri)
            if (processed != null) {
                onPhotoSelected(processed.first, processed.second)
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = voiceRecorder.startRecording(
                onMaxDurationReached = {
                    val durationMs = voiceRecorder.stopRecording()
                    isRecordingVoice = false
                    val voiceFile = voiceRecorder.outputFile
                    if (voiceFile != null && voiceFile.exists()) {
                        val base64 = Base64.encodeToString(voiceFile.readBytes(), Base64.NO_WRAP)
                        onVoiceRecorded(voiceFile, base64, durationMs)
                    }
                }
            )
            if (file != null) {
                isRecordingVoice = true
                recordingSeconds = 0
            }
        } else {
            onShowPermissionDeniedSnackbar()
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0
            while (isRecordingVoice && recordingSeconds < 60) {
                delay(1000L)
                recordingSeconds++
            }
            if (isRecordingVoice && recordingSeconds >= 60) {
                val durationMs = voiceRecorder.stopRecording()
                isRecordingVoice = false
                val voiceFile = voiceRecorder.outputFile
                if (voiceFile != null && voiceFile.exists()) {
                    val base64 = Base64.encodeToString(voiceFile.readBytes(), Base64.NO_WRAP)
                    onVoiceRecorded(voiceFile, base64, durationMs)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        when {
            fineGranted -> onTakeMyLocation(false)
            coarseGranted -> onTakeMyLocation(true)
            else -> onShowPermissionDeniedSnackbar()
        }
    }

    val requestLocation = {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        when {
            hasFine -> onTakeMyLocation(false)
            hasCoarse -> onTakeMyLocation(true)
            else -> permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (showLocationDisabledDialog) {
        AlertDialog(
            onDismissRequest = onDismissLocationDisabledDialog,
            title = {
                Text(
                    text = stringResource(R.string.dialog_location_disabled_title),
                    color = TokyoOnBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_location_disabled_msg),
                    color = TokyoOnSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissLocationDisabledDialog()
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_open_location_settings), color = TokyoPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissLocationDisabledDialog) {
                    Text(stringResource(R.string.btn_cancel), color = TokyoOnSurface)
                }
            },
            containerColor = TokyoSurface
        )
    }
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

                    // Фото (Active)
                    FilterChip(
                        selected = formState.kind == "PHOTO",
                        onClick = { onKindChange("PHOTO") },
                        label = { Text(stringResource(R.string.type_photo)) },
                        leadingIcon = { Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TokyoPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = TokyoPrimary,
                            selectedLeadingIconColor = TokyoPrimary
                        )
                    )

                    // Голос (Active)
                    FilterChip(
                        selected = formState.kind == "VOICE",
                        onClick = { onKindChange("VOICE") },
                        label = { Text(stringResource(R.string.type_voice)) },
                        leadingIcon = { Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TokyoPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = TokyoPrimary,
                            selectedLeadingIconColor = TokyoPrimary
                        )
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

                // ВЛОЖЕНИЕ: ФОТОГРАФИЯ
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TokyoBackground.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, TokyoPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, tint = TokyoPrimary, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.attachment_photo_title),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TokyoOnBackground
                            )
                        )
                    }

                    if (formState.attachedPhotoBytes == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val pair = MediaHelper.createCameraTempFile(context)
                                    pendingCameraUri = pair.second
                                    takePictureLauncher.launch(pair.second)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TokyoPrimary),
                                border = BorderStroke(1.dp, TokyoPrimary.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_take_photo), style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = {
                                    pickVisualMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TokyoAccent),
                                border = BorderStroke(1.dp, TokyoAccent.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_choose_photo), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = formState.attachedPhotoBytes,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, TokyoPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.photo_attached_hint),
                                        style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnBackground, fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "Размер: ${(formState.attachedPhotoBytes.size / 1024)} КБ",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TokyoSuccess)
                                    )
                                }
                            }
                            TextButton(
                                onClick = onPhotoRemoved,
                                colors = ButtonDefaults.textButtonColors(contentColor = TokyoError)
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_remove_photo), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // ВЛОЖЕНИЕ: ГОЛОСОВОЕ СООБЩЕНИЕ
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TokyoBackground.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, TokyoSecondary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Mic, null, tint = TokyoSecondary, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.attachment_voice_title),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TokyoOnBackground
                            )
                        )
                    }

                    if (isRecordingVoice) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TokyoError.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .border(1.dp, TokyoError.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(TokyoError)
                                )
                                Text(
                                    text = String.format(
                                        java.util.Locale.US,
                                        "Запись: %02d:%02d / 01:00",
                                        recordingSeconds / 60,
                                        recordingSeconds % 60
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TokyoError, fontWeight = FontWeight.Bold)
                                )
                            }

                            Button(
                                onClick = {
                                    val durationMs = voiceRecorder.stopRecording()
                                    isRecordingVoice = false
                                    val voiceFile = voiceRecorder.outputFile
                                    if (voiceFile != null && voiceFile.exists()) {
                                        val base64 = Base64.encodeToString(voiceFile.readBytes(), Base64.NO_WRAP)
                                        onVoiceRecorded(voiceFile, base64, durationMs)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TokyoError, contentColor = TokyoBackground),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_stop_recording), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else if (formState.attachedVoiceBase64 != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isPlayingVoice) {
                                            voicePlayer.pause()
                                            isPlayingVoice = false
                                        } else {
                                            val file = formState.attachedVoiceFile
                                            if (file != null && file.exists()) {
                                                voicePlayer.play(file) {
                                                    isPlayingVoice = false
                                                }
                                                isPlayingVoice = true
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingVoice) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = TokyoSecondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                val durationSec = ((formState.attachedVoiceDurationMs ?: 1000L) / 1000L).coerceAtLeast(1L)
                                Text(
                                    text = stringResource(R.string.voice_recorded_format, durationSec),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnBackground, fontWeight = FontWeight.Medium)
                                )
                            }

                            TextButton(
                                onClick = {
                                    voicePlayer.stop()
                                    isPlayingVoice = false
                                    onVoiceRemoved()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = TokyoError)
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_delete_voice), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                val hasAudioPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasAudioPerm) {
                                    val file = voiceRecorder.startRecording(
                                        onMaxDurationReached = {
                                            val durationMs = voiceRecorder.stopRecording()
                                            isRecordingVoice = false
                                            val voiceFile = voiceRecorder.outputFile
                                            if (voiceFile != null && voiceFile.exists()) {
                                                val base64 = Base64.encodeToString(voiceFile.readBytes(), Base64.NO_WRAP)
                                                onVoiceRecorded(voiceFile, base64, durationMs)
                                            }
                                        }
                                    )
                                    if (file != null) {
                                        isRecordingVoice = true
                                        recordingSeconds = 0
                                    }
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TokyoSecondary),
                            border = BorderStroke(1.dp, TokyoSecondary.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_record_voice))
                        }
                    }
                }

                // Recipient picker (@handle chips, empty = self)
                if (!formState.isGeoLocked) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = formState.recipientSearchQuery,
                            onValueChange = onRecipientSearchQueryChange,
                            label = { Text(stringResource(R.string.field_recipients_handle_label)) },
                            placeholder = { Text(stringResource(R.string.field_recipients_handle_placeholder)) },
                            leadingIcon = {
                                Text(
                                    text = "@",
                                    color = TokyoSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            trailingIcon = {
                                if (formState.isSearchingRecipients) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = TokyoSecondary,
                                        strokeWidth = 2.dp
                                    )
                                }
                            },
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

                        if (formState.selectedRecipients.isEmpty()) {
                            Text(
                                text = stringResource(R.string.recipients_self_hint),
                                style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.recipients_selected_count,
                                    formState.selectedRecipients.size
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(color = TokyoSecondary)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                formState.selectedRecipients.forEach { recipientUser ->
                                    val chipLabel = recipientUser.handle?.let { "@$it" } ?: recipientUser.username
                                    InputChip(
                                        selected = true,
                                        onClick = { onRemoveRecipient(recipientUser.id) },
                                        label = {
                                            Text(
                                                text = chipLabel,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = TokyoSecondary.copy(alpha = 0.2f),
                                            selectedLabelColor = TokyoOnBackground,
                                            selectedTrailingIconColor = TokyoSecondary
                                        ),
                                        border = InputChipDefaults.inputChipBorder(
                                            enabled = true,
                                            selected = true,
                                            selectedBorderColor = TokyoSecondary.copy(alpha = 0.55f)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }

                        if (formState.recipientSearchResults.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TokyoBackground.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .border(1.dp, TokyoSecondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                formState.recipientSearchResults.take(6).forEach { candidate ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onAddRecipient(candidate) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val avatar = candidate.avatarUrl ?: candidate.photoUrl
                                        if (!avatar.isNullOrBlank()) {
                                            AsyncImage(
                                                model = avatar,
                                                contentDescription = candidate.username,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(TokyoSecondary.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = TokyoSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = candidate.handle?.let { "@$it" } ?: candidate.username,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TokyoOnBackground
                                                )
                                            )
                                            if (!candidate.handle.isNullOrBlank()) {
                                                Text(
                                                    text = candidate.username,
                                                    style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = TokyoAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
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
                }

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
                    val isLocationFixed = formState.latitude != null && formState.longitude != null
                    OutlinedButton(
                        onClick = { requestLocation() },
                        enabled = !formState.isFetchingLocation,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isLocationFixed) TokyoSuccess else TokyoPrimary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isLocationFixed) TokyoSuccess else TokyoPrimary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (formState.isFetchingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = TokyoPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.location_searching_progress))
                        } else if (isLocationFixed) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp), tint = TokyoSuccess)
                            Spacer(modifier = Modifier.width(8.dp))
                            val fixedText = if (formState.locationAccuracyMeters != null) {
                                stringResource(
                                    R.string.location_fixed_format,
                                    formState.latitude!!,
                                    formState.longitude!!,
                                    formState.locationAccuracyMeters
                                )
                            } else {
                                stringResource(
                                    R.string.location_coords_format,
                                    formState.latitude!!,
                                    formState.longitude!!
                                )
                            }
                            Text(
                                text = fixedText,
                                color = TokyoSuccess,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_take_my_location))
                        }
                    }

                    // Радиус (диапазон 50..500 м, быстрые чипы и устранение обрезки ползунка)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Радиус: ${formState.radiusMeters.toInt()} м",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TokyoOnBackground
                            )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Slider(
                                value = formState.radiusMeters.coerceIn(50f, 500f),
                                onValueChange = { onRadiusChange(kotlin.math.round(it)) },
                                valueRange = 50f..500f,
                                colors = SliderDefaults.colors(
                                    thumbColor = TokyoPrimary,
                                    activeTrackColor = TokyoPrimary,
                                    inactiveTrackColor = TokyoPrimary.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(50f, 100f, 200f, 500f).forEach { chipRadius ->
                                val isSelected = formState.radiusMeters.toInt() == chipRadius.toInt()
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onRadiusChange(chipRadius) },
                                    label = { Text("${chipRadius.toInt()} м") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TokyoPrimary.copy(alpha = 0.25f),
                                        selectedLabelColor = TokyoPrimary,
                                        containerColor = TokyoBackground.copy(alpha = 0.6f),
                                        labelColor = TokyoOnSurface
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = TokyoPrimary.copy(alpha = 0.25f),
                                        selectedBorderColor = TokyoPrimary
                                    )
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.radius_indoor_hint),
                            style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
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
                            val displayLabel = user.handle?.let { "${user.username} (@$it)" } ?: user.username
                            Text(
                                text = "$displayLabel ${if (isCreator) stringResource(R.string.creator_mandatory_note) else ""}",
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
