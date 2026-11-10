package com.example.cyberpunk.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cyberpunk.R
import com.example.cyberpunk.data.ApiConfig
import com.example.cyberpunk.data.CapsuleRepository
import com.example.cyberpunk.data.LocationProvider
import com.example.cyberpunk.data.MockCapsuleRepository
import com.example.cyberpunk.data.RemoteCapsuleRepository
import com.example.cyberpunk.data.SessionExpiredException
import com.example.cyberpunk.data.UserPreferences
import com.timecapsule.shared.CapsuleDto
import com.timecapsule.shared.CheckInRequest
import com.timecapsule.shared.CreateCapsuleRequest
import com.timecapsule.shared.CreateStageRequest
import com.timecapsule.shared.UserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate
import java.time.ZoneOffset

private val exportJson = Json { prettyPrint = true }

data class DraftStageItem(
    val unlockDateMillis: Long,
    val secretText: String
)

data class CreateCapsuleFormState(
    val title: String = "",
    val kind: String = "MESSAGE",
    val recipient: String = "",
    val tags: String = "",
    val messageText: String = "",
    val unlockDateMillis: Long = LocalDate.now().plusDays(7)
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    val selectedDateChip: String = "WEEK",
    val isGeoLocked: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Float = 50f,
    val participantSearchQuery: String = "",
    val selectedParticipantIds: Set<String> = setOf("user_maga", "user_zainab"),
    val stages: List<DraftStageItem> = listOf(
        DraftStageItem(
            unlockDateMillis = LocalDate.now().plusDays(7)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            secretText = ""
        )
    ),
    val isFetchingLocation: Boolean = false,
    val isSubmitting: Boolean = false
)

data class CapsuleUiState(
    val isAuthInitialized: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isAuthenticating: Boolean = false,
    val currentUser: UserDto? = null,
    val availableUsers: List<UserDto> = listOf(
        UserDto(id = "user_maga", username = "Maga", email = "m***a@demo.local"),
        UserDto(id = "user_zainab", username = "Zainab", email = "z***b@demo.local")
    ),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val capsules: List<CapsuleDto> = emptyList(),
    val selectedCapsuleId: String? = null,
    val showProfileDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showJoinDialog: Boolean = false,
    val joinInviteCodeInput: String = "",
    val isJoiningCapsule: Boolean = false,
    val settingsUrlInput: String = ApiConfig.BASE_URL,
    val isCheckingHealth: Boolean = false,
    val healthResultText: String? = null,
    val healthResultSuccess: Boolean? = null,
    val isDemoMode: Boolean = false,
    val isDemoForceActive: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val deviceLat: Double? = null,
    val deviceLon: Double? = null,
    val lockedDialogMissingText: String? = null,
    val capsulesLoadError: String? = null,
    val isRefreshingCapsules: Boolean = false,
    val createForm: CreateCapsuleFormState = CreateCapsuleFormState(),
    val searchQuery: String = "",
    val selectedTab: Int = 0, // 0: SEALED, 1: READY, 2: OPENED, 3: ARCHIVED
    val isPinSet: Boolean = false,
    val isPinUnlocked: Boolean = false,
    val showPinPromptDialog: Boolean = false,
    val showSetPinDialog: Boolean = false,
    val pinError: String? = null,
    val isNotificationsEnabled: Boolean = true,
    val showConfirmSealDialog: Boolean = false,
    val showConfirmDeleteDataDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val aboutDialogType: String = "ABOUT",
    val isDeletingData: Boolean = false
) {
    val selectedCapsule: CapsuleDto?
        get() = capsules.find { it.id == selectedCapsuleId } ?: capsules.firstOrNull()

    val myCapsules: List<CapsuleDto>
        get() {
            val uid = currentUserId ?: return emptyList()
            val filtered = capsules.filter { c ->
                c.creatorId == uid || c.participants.any { p -> p.userId == uid }
            }
            if (deviceLat == null || deviceLon == null) return filtered
            return filtered.sortedBy { c ->
                MockCapsuleRepository.haversineMeters(deviceLat, deviceLon, c.latitude, c.longitude)
            }
        }

    val currentUserId: String?
        get() = currentUser?.id

    val currentUsername: String
        get() = currentUser?.username ?: ""

    val currentPhotoUrl: String?
        get() = currentUser?.photoUrl
}

class CapsuleViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val locationProvider = LocationProvider(appContext)

    private val remoteRepository: CapsuleRepository = RemoteCapsuleRepository()
    private val mockRepository: CapsuleRepository = MockCapsuleRepository(
        defaultTitle = appContext.getString(R.string.mock_capsule_khujand_title),
        stage1PlainText = appContext.getString(R.string.mock_stage_1_decrypted),
        lockedPlaceholder = appContext.getString(R.string.locked_stage_placeholder)
    )

    private val _uiState = MutableStateFlow(CapsuleUiState())
    val uiState: StateFlow<CapsuleUiState> = _uiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private var userSearchJob: Job? = null

    init {
        ApiConfig.init(appContext)
        _uiState.update { it.copy(settingsUrlInput = ApiConfig.BASE_URL) }

        viewModelScope.launch {
            val hasPin = UserPreferences.hasPinFlow(appContext).first()
            val notif = UserPreferences.isNotificationsEnabledFlow(appContext).first()
            _uiState.update {
                it.copy(
                    isPinSet = hasPin,
                    isPinUnlocked = !hasPin,
                    isNotificationsEnabled = notif
                )
            }
        }

        viewModelScope.launch {
            val savedSession = ApiConfig.loadSession(appContext)
            if (savedSession != null) {
                val (user, _) = savedSession
                _uiState.update { state ->
                    state.copy(
                        isAuthInitialized = true,
                        isAuthenticated = true,
                        currentUser = user,
                        availableUsers = mergeUsers(state.availableUsers, listOf(user)),
                        createForm = state.createForm.copy(
                            selectedParticipantIds = state.createForm.selectedParticipantIds + user.id
                        )
                    )
                }
                loadCapsules()
                refreshUsersList("")
            } else {
                _uiState.update { state ->
                    state.copy(
                        isAuthInitialized = true,
                        isAuthenticated = false,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun mergeUsers(current: List<UserDto>, incoming: List<UserDto>): List<UserDto> {
        val map = linkedMapOf<String, UserDto>()
        current.forEach { map[it.id] = it }
        incoming.forEach { map[it.id] = it }
        return map.values.toList()
    }

    private fun handleUnauthorized() {
        viewModelScope.launch {
            ApiConfig.clearSession(appContext)
        }
        _uiState.update { state ->
            state.copy(
                isAuthenticated = false,
                isAuthenticating = false,
                currentUser = null,
                capsules = emptyList(),
                selectedCapsuleId = null
            )
        }
        _snackbarEvents.tryEmit(appContext.getString(R.string.auth_error_invalid_token))
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true) }
            try {
                val authResponse = try {
                    remoteRepository.authGoogle(idToken)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val reason = e.localizedMessage ?: e.javaClass.simpleName
                    _snackbarEvents.tryEmit(appContext.getString(R.string.auth_error_network, reason))
                    remoteRepository.authGoogle(idToken)
                }

                ApiConfig.saveSession(appContext, authResponse.user, authResponse.sessionToken)

                _uiState.update { state ->
                    state.copy(
                        isAuthenticated = true,
                        currentUser = authResponse.user,
                        availableUsers = mergeUsers(state.availableUsers, listOf(authResponse.user)),
                        createForm = state.createForm.copy(
                            selectedParticipantIds = state.createForm.selectedParticipantIds + authResponse.user.id
                        )
                    )
                }
                loadCapsules()
                refreshUsersList("")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.localizedMessage ?: e.javaClass.simpleName
                _snackbarEvents.tryEmit(appContext.getString(R.string.auth_error_generic, reason))
            } finally {
                _uiState.update { it.copy(isAuthenticating = false) }
            }
        }
    }

    fun onGoogleSignInError(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    fun signInWithDemo(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true) }
            try {
                val authResponse = try {
                    remoteRepository.authDemo(userId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _snackbarEvents.tryEmit(appContext.getString(R.string.auth_demo_offline_notice, userId))
                    mockRepository.authDemo(userId)
                }

                ApiConfig.saveSession(appContext, authResponse.user, authResponse.sessionToken)

                _uiState.update { state ->
                    state.copy(
                        isAuthenticated = true,
                        currentUser = authResponse.user,
                        availableUsers = mergeUsers(state.availableUsers, listOf(authResponse.user)),
                        createForm = state.createForm.copy(
                            selectedParticipantIds = state.createForm.selectedParticipantIds + authResponse.user.id
                        )
                    )
                }
                loadCapsules()
                refreshUsersList("")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.localizedMessage ?: e.javaClass.simpleName
                _snackbarEvents.tryEmit(appContext.getString(R.string.auth_error_generic, reason))
            } finally {
                _uiState.update { it.copy(isAuthenticating = false) }
            }
        }
    }

    fun logout() {
        val credentialManager = CredentialManager.create(appContext)
        viewModelScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
            }
            ApiConfig.clearSession(appContext)
            UserPreferences.clearPin(appContext)
            _uiState.update { state ->
                state.copy(
                    isAuthenticated = false,
                    isAuthenticating = false,
                    currentUser = null,
                    showProfileDialog = false,
                    capsules = emptyList(),
                    selectedCapsuleId = null,
                    isPinSet = false,
                    isPinUnlocked = true,
                    showPinPromptDialog = false,
                    showSetPinDialog = false
                )
            }
            _snackbarEvents.tryEmit(appContext.getString(R.string.logout_done))
        }
    }

    fun refreshUsersList(query: String = "") {
        userSearchJob?.cancel()
        userSearchJob = viewModelScope.launch {
            try {
                val remoteUsers = try {
                    remoteRepository.searchUsers(query)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SessionExpiredException) {
                    handleUnauthorized()
                    return@launch
                } catch (_: Exception) {
                    mockRepository.searchUsers(query)
                }
                _uiState.update { state ->
                    state.copy(availableUsers = mergeUsers(state.availableUsers, remoteUsers))
                }
            } catch (_: Exception) {
            }
        }
    }

    fun updateParticipantSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(createForm = state.createForm.copy(participantSearchQuery = query))
        }
        refreshUsersList(query)
    }

    fun toggleParticipantSelection(userId: String) {
        val currentCreatorId = _uiState.value.currentUserId ?: "user_maga"
        if (userId == currentCreatorId) return

        _uiState.update { state ->
            val set = state.createForm.selectedParticipantIds.toMutableSet()
            if (set.contains(userId)) {
                set.remove(userId)
            } else {
                set.add(userId)
            }
            state.copy(createForm = state.createForm.copy(selectedParticipantIds = set))
        }
    }

    fun openJoinDialog() {
        _uiState.update { it.copy(showJoinDialog = true, joinInviteCodeInput = "") }
    }

    fun dismissJoinDialog() {
        _uiState.update { it.copy(showJoinDialog = false, joinInviteCodeInput = "") }
    }

    fun updateJoinInviteCodeInput(code: String) {
        _uiState.update { it.copy(joinInviteCodeInput = code.uppercase()) }
    }

    fun joinCapsuleByCode(onJoined: () -> Unit = {}) {
        val code = _uiState.value.joinInviteCodeInput.trim()
        if (code.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isJoiningCapsule = true) }
            try {
                val joined = try {
                    remoteRepository.joinCapsule(code)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SessionExpiredException) {
                    handleUnauthorized()
                    return@launch
                } catch (e: Exception) {
                    val reason = e.localizedMessage ?: e.javaClass.simpleName
                    _snackbarEvents.tryEmit(appContext.getString(R.string.network_error_snackbar, reason))
                    mockRepository.joinCapsule(code)
                }

                _uiState.update { state ->
                    val existing = state.capsules.filterNot { it.id == joined.id }
                    state.copy(
                        capsules = existing + joined,
                        selectedCapsuleId = joined.id,
                        showJoinDialog = false,
                        joinInviteCodeInput = ""
                    )
                }
                _snackbarEvents.tryEmit(appContext.getString(R.string.join_success_msg, joined.title))
                onJoined()
            } catch (e: Exception) {
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Ошибка присоединения к капсуле")
            } finally {
                _uiState.update { it.copy(isJoiningCapsule = false) }
            }
        }
    }

    fun notifyInviteCodeCopied(code: String) {
        _snackbarEvents.tryEmit(appContext.getString(R.string.invite_code_copied, code))
    }

    fun selectCapsule(capsuleId: String) {
        _uiState.update { it.copy(selectedCapsuleId = capsuleId) }
    }

    fun openProfileDialog() {
        _uiState.update { it.copy(showProfileDialog = true) }
    }

    fun dismissProfileDialog() {
        _uiState.update { it.copy(showProfileDialog = false) }
    }

    fun selectProfile(userId: String) {
        signInWithDemo(userId)
        dismissProfileDialog()
    }

    fun openSettingsDialog() {
        _uiState.update {
            it.copy(
                showSettingsDialog = true,
                settingsUrlInput = ApiConfig.BASE_URL,
                healthResultText = null,
                healthResultSuccess = null
            )
        }
    }

    fun dismissSettingsDialog() {
        _uiState.update {
            it.copy(
                showSettingsDialog = false,
                healthResultText = null,
                healthResultSuccess = null
            )
        }
    }

    fun updateSettingsUrlInput(url: String) {
        _uiState.update { it.copy(settingsUrlInput = url) }
    }

    fun saveServerUrl() {
        val newUrl = _uiState.value.settingsUrlInput.trim().ifEmpty { ApiConfig.BASE_URL }
        ApiConfig.saveBaseUrl(appContext, newUrl)
        _uiState.update { it.copy(showSettingsDialog = false) }
        _snackbarEvents.tryEmit(appContext.getString(R.string.server_url_saved, newUrl))
        loadCapsules()
    }

    fun checkServerHealth() {
        val target = _uiState.value.settingsUrlInput.trim().ifEmpty { ApiConfig.BASE_URL }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingHealth = true,
                    healthResultText = appContext.getString(R.string.health_checking),
                    healthResultSuccess = null
                )
            }
            try {
                val ok = remoteRepository.checkHealth(target)
                if (ok) {
                    _uiState.update {
                        it.copy(
                            isCheckingHealth = false,
                            healthResultText = appContext.getString(R.string.health_ok),
                            healthResultSuccess = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isCheckingHealth = false,
                            healthResultText = appContext.getString(R.string.health_error, "HTTP non-200"),
                            healthResultSuccess = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingHealth = false,
                        healthResultText = appContext.getString(R.string.health_error, e.localizedMessage ?: e.javaClass.simpleName),
                        healthResultSuccess = false
                    )
                }
            }
        }
    }

    fun setDemoMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isDemoMode = enabled,
                isDemoForceActive = if (!enabled) false else it.isDemoForceActive
            )
        }
    }

    fun updateLocationPermission(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
    }

    fun loadCapsules(isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isPullToRefresh) {
                _uiState.update { it.copy(isRefreshingCapsules = true) }
            } else {
                _uiState.update { it.copy(isLoading = true) }
            }

            val previousCapsules = _uiState.value.capsules

            try {
                val loaded = try {
                    remoteRepository.getCapsules()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SessionExpiredException) {
                    handleUnauthorized()
                    return@launch
                } catch (e: Exception) {
                    val reason = e.localizedMessage ?: e.javaClass.simpleName
                    _snackbarEvents.tryEmit(appContext.getString(R.string.network_error_fallback_mock, reason))
                    mockRepository.getCapsules()
                }

                _uiState.update { current ->
                    current.copy(
                        capsules = loaded,
                        selectedCapsuleId = current.selectedCapsuleId ?: loaded.firstOrNull()?.id,
                        isLoading = false,
                        isRefreshingCapsules = false,
                        capsulesLoadError = null
                    )
                }

                loaded.forEach { fresh ->
                    val old = previousCapsules.find { it.id == fresh.id }
                    if (old != null && !old.isFullyUnlocked && fresh.isFullyUnlocked) {
                        _snackbarEvents.tryEmit(appContext.getString(R.string.capsule_just_unlocked_snackbar, fresh.title))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshingCapsules = false,
                        capsulesLoadError = e.localizedMessage ?: e.javaClass.simpleName
                    )
                }
            }
        }
    }

    fun performRealHeartbeatTick(capsuleId: String) {
        viewModelScope.launch {
            val coords = locationProvider.getCurrentLocation()
            if (coords != null) {
                _uiState.update { it.copy(deviceLat = coords.latitude, deviceLon = coords.longitude) }
            }
            val lat = coords?.latitude ?: _uiState.value.deviceLat ?: return@launch
            val lon = coords?.longitude ?: _uiState.value.deviceLon ?: return@launch

            _uiState.update { it.copy(isSyncing = true) }
            try {
                val currentUid = _uiState.value.currentUserId ?: "user_maga"
                val updated = try {
                    remoteRepository.checkIn(capsuleId, CheckInRequest(userId = currentUid, lat = lat, lon = lon))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SessionExpiredException) {
                    handleUnauthorized()
                    return@launch
                } catch (_: Exception) {
                    mockRepository.checkIn(capsuleId, CheckInRequest(userId = currentUid, lat = lat, lon = lon))
                }
                updateCapsuleInState(updated)
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun performDemoForceCheckInTick(capsuleId: String, notifyUser: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                val updated = try {
                    remoteRepository.demoForceCheckIn(capsuleId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SessionExpiredException) {
                    handleUnauthorized()
                    return@launch
                } catch (_: Exception) {
                    mockRepository.demoForceCheckIn(capsuleId)
                }
                updateCapsuleInState(updated)
                if (notifyUser) {
                    _snackbarEvents.tryEmit(appContext.getString(R.string.demo_force_checkin_done))
                }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun onDemoForceCheckInClicked(capsuleId: String) {
        _uiState.update { it.copy(isDemoForceActive = true) }
        performDemoForceCheckInTick(capsuleId, notifyUser = true)
    }

    private fun updateCapsuleInState(updated: CapsuleDto) {
        _uiState.update { state ->
            val old = state.capsules.find { it.id == updated.id }
            val list = state.capsules.map { if (it.id == updated.id) updated else it }
            if (old != null && !old.isFullyUnlocked && updated.isFullyUnlocked) {
                _snackbarEvents.tryEmit(appContext.getString(R.string.capsule_just_unlocked_snackbar, updated.title))
            }
            state.copy(capsules = list)
        }
    }

    fun onCapsuleClicked(onOpenTimeline: () -> Unit) {
        val capsule = _uiState.value.selectedCapsule ?: return
        if (capsule.isFullyUnlocked) {
            onOpenTimeline()
        } else {
            val waiting = if (capsule.isGroupCapsule) {
                appContext.getString(R.string.access_locked_all_participants)
            } else {
                capsule.participants.firstOrNull()?.username ?: ""
            }
            _uiState.update { it.copy(lockedDialogMissingText = waiting) }
        }
    }

    fun dismissLockedDialog() {
        _uiState.update { it.copy(lockedDialogMissingText = null) }
    }

    // --- Search & Shelf Tabs ---
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSelectedTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    // --- Capsule Actions: Open, Archive, Unarchive ---
    fun openCapsule(capsuleId: String, onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val updated = try {
                    remoteRepository.openCapsule(capsuleId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SessionExpiredException) {
                    handleUnauthorized()
                    return@launch
                } catch (e: Exception) {
                    mockRepository.openCapsule(capsuleId)
                }
                updateCapsuleInState(updated)
                _snackbarEvents.tryEmit(appContext.getString(R.string.capsule_opened_success, updated.title))
                onOpened()
            } catch (e: Exception) {
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Ошибка открытия капсулы")
            }
        }
    }

    fun archiveCapsule(capsuleId: String) {
        viewModelScope.launch {
            try {
                val updated = try {
                    remoteRepository.archiveCapsule(capsuleId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SessionExpiredException) {
                    handleUnauthorized()
                    return@launch
                } catch (e: Exception) {
                    mockRepository.archiveCapsule(capsuleId)
                }
                updateCapsuleInState(updated)
                _snackbarEvents.tryEmit(appContext.getString(R.string.capsule_archived_success, updated.title))
            } catch (e: Exception) {
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Ошибка архивации")
            }
        }
    }

    fun unarchiveCapsule(capsuleId: String) {
        viewModelScope.launch {
            try {
                val updated = try {
                    remoteRepository.unarchiveCapsule(capsuleId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SessionExpiredException) {
                    handleUnauthorized()
                    return@launch
                } catch (e: Exception) {
                    mockRepository.unarchiveCapsule(capsuleId)
                }
                updateCapsuleInState(updated)
                _snackbarEvents.tryEmit(appContext.getString(R.string.capsule_unarchived_success, updated.title))
            } catch (e: Exception) {
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Ошибка разархивации")
            }
        }
    }

    // --- Create Capsule Form updates ---
    fun updateCreateTitle(title: String) {
        _uiState.update { it.copy(createForm = it.createForm.copy(title = title)) }
    }

    fun updateCreateKind(kind: String) {
        _uiState.update { it.copy(createForm = it.createForm.copy(kind = kind)) }
    }

    fun updateCreateRecipient(recipient: String) {
        _uiState.update { it.copy(createForm = it.createForm.copy(recipient = recipient)) }
    }

    fun updateCreateTags(tags: String) {
        _uiState.update { it.copy(createForm = it.createForm.copy(tags = tags)) }
    }

    fun updateCreateMessage(message: String) {
        _uiState.update { state ->
            val stages = state.createForm.stages
            val updatedStages = if (stages.isNotEmpty()) {
                listOf(stages[0].copy(secretText = message)) + stages.drop(1)
            } else {
                listOf(DraftStageItem(state.createForm.unlockDateMillis, message))
            }
            state.copy(
                createForm = state.createForm.copy(
                    messageText = message,
                    stages = updatedStages
                )
            )
        }
    }

    fun updateCreateIsGeoLocked(isGeo: Boolean) {
        _uiState.update { it.copy(createForm = it.createForm.copy(isGeoLocked = isGeo)) }
    }

    fun setCreateDateChip(chipKey: String) {
        val now = LocalDate.now()
        val targetMillis = when (chipKey) {
            "TOMORROW" -> now.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            "WEEK" -> now.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            "MONTH" -> now.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            "DEMO_1MIN" -> System.currentTimeMillis() + 65_000L
            else -> _uiState.value.createForm.unlockDateMillis
        }
        _uiState.update { state ->
            val updatedStages = state.createForm.stages.mapIndexed { idx, st ->
                if (idx == 0) st.copy(unlockDateMillis = targetMillis) else st
            }
            state.copy(
                createForm = state.createForm.copy(
                    selectedDateChip = chipKey,
                    unlockDateMillis = targetMillis,
                    stages = updatedStages
                )
            )
        }
    }

    fun setCreateCustomDate(dateMillis: Long) {
        _uiState.update { state ->
            val updatedStages = state.createForm.stages.mapIndexed { idx, st ->
                if (idx == 0) st.copy(unlockDateMillis = dateMillis) else st
            }
            state.copy(
                createForm = state.createForm.copy(
                    selectedDateChip = "CUSTOM",
                    unlockDateMillis = dateMillis,
                    stages = updatedStages
                )
            )
        }
    }

    fun applyTemplate(title: String, prompt: String, tags: List<String>, daysAhead: Long, onNavigateToCreate: () -> Unit) {
        val dateMillis = LocalDate.now().plusDays(daysAhead).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        _uiState.update { state ->
            state.copy(
                createForm = state.createForm.copy(
                    title = title,
                    messageText = prompt,
                    tags = tags.joinToString(", "),
                    unlockDateMillis = dateMillis,
                    selectedDateChip = "CUSTOM",
                    isGeoLocked = false,
                    stages = listOf(DraftStageItem(unlockDateMillis = dateMillis, secretText = prompt))
                )
            )
        }
        onNavigateToCreate()
    }

    fun openConfirmSealDialog() {
        val form = _uiState.value.createForm
        val message = form.messageText.ifBlank { form.stages.firstOrNull()?.secretText.orEmpty() }
        if (form.title.isBlank() || message.isBlank()) {
            _snackbarEvents.tryEmit(appContext.getString(R.string.validation_create_error))
            return
        }
        if (form.isGeoLocked && (form.latitude == null || form.longitude == null)) {
            _snackbarEvents.tryEmit(appContext.getString(R.string.validation_create_error))
            return
        }
        _uiState.update { it.copy(showConfirmSealDialog = true) }
    }

    fun dismissConfirmSealDialog() {
        _uiState.update { it.copy(showConfirmSealDialog = false) }
    }

    fun fetchLocationForCreateForm() {
        viewModelScope.launch {
            _uiState.update { it.copy(createForm = it.createForm.copy(isFetchingLocation = true)) }
            try {
                val coords = locationProvider.getCurrentLocation()
                if (coords != null) {
                    _uiState.update {
                        it.copy(
                            deviceLat = coords.latitude,
                            deviceLon = coords.longitude,
                            createForm = it.createForm.copy(
                                latitude = coords.latitude,
                                longitude = coords.longitude,
                                isFetchingLocation = false
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(createForm = it.createForm.copy(isFetchingLocation = false)) }
                    _snackbarEvents.tryEmit(appContext.getString(R.string.location_fetch_error))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(createForm = it.createForm.copy(isFetchingLocation = false)) }
                _snackbarEvents.tryEmit(appContext.getString(R.string.location_fetch_error))
            }
        }
    }

    fun updateCreateRadius(radius: Float) {
        _uiState.update { it.copy(createForm = it.createForm.copy(radiusMeters = radius)) }
    }

    fun addCreateStage() {
        _uiState.update { state ->
            val nextDate = LocalDate.now().plusMonths(state.createForm.stages.size.toLong() + 1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val list = state.createForm.stages + DraftStageItem(unlockDateMillis = nextDate, secretText = "")
            state.copy(createForm = state.createForm.copy(stages = list))
        }
    }

    fun removeCreateStage(index: Int) {
        _uiState.update { state ->
            if (state.createForm.stages.size <= 1) return@update state
            val list = state.createForm.stages.filterIndexed { i, _ -> i != index }
            state.copy(createForm = state.createForm.copy(stages = list))
        }
    }

    fun updateCreateStageDate(index: Int, millis: Long) {
        _uiState.update { state ->
            val updated = state.createForm.stages.mapIndexed { i, item ->
                if (i == index) item.copy(unlockDateMillis = millis) else item
            }
            state.copy(createForm = state.createForm.copy(stages = updated))
        }
    }

    fun updateCreateStageSecret(index: Int, text: String) {
        _uiState.update { state ->
            val updated = state.createForm.stages.mapIndexed { i, item ->
                if (i == index) item.copy(secretText = text) else item
            }
            val msg = if (index == 0) text else state.createForm.messageText
            state.copy(createForm = state.createForm.copy(stages = updated, messageText = msg))
        }
    }

    fun submitCreateCapsule(onCreated: () -> Unit) {
        dismissConfirmSealDialog()
        val state = _uiState.value
        val form = state.createForm
        val creatorId = state.currentUserId ?: "user_maga"
        val message = form.messageText.ifBlank { form.stages.firstOrNull()?.secretText.orEmpty() }

        if (form.title.isBlank() || message.isBlank()) {
            _snackbarEvents.tryEmit(appContext.getString(R.string.validation_create_error))
            return
        }

        val stages = if (form.isGeoLocked) {
            form.stages.filter { it.secretText.isNotBlank() }.ifEmpty {
                listOf(DraftStageItem(form.unlockDateMillis, message))
            }
        } else {
            listOf(DraftStageItem(form.unlockDateMillis, message))
        }

        val cleanTags = form.tags.split(",")
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .take(5)

        val participants = if (form.isGeoLocked) {
            (form.selectedParticipantIds + creatorId).distinct()
        } else {
            listOf(creatorId)
        }

        val request = CreateCapsuleRequest(
            creatorId = creatorId,
            title = form.title.trim(),
            latitude = if (form.isGeoLocked) (form.latitude ?: 0.0) else 0.0,
            longitude = if (form.isGeoLocked) (form.longitude ?: 0.0) else 0.0,
            radiusMeters = if (form.isGeoLocked) form.radiusMeters.toDouble() else 50.0,
            isGroupCapsule = form.isGeoLocked && participants.size > 1,
            participantIds = participants,
            stages = stages.mapIndexed { index, item ->
                CreateStageRequest(
                    stageNumber = index + 1,
                    unlockDate = item.unlockDateMillis,
                    secretText = item.secretText.trim()
                )
            },
            kind = form.kind.ifEmpty { "MESSAGE" },
            recipient = form.recipient.trim().takeIf { it.isNotEmpty() },
            tags = cleanTags,
            isGeoLocked = form.isGeoLocked
        )

        viewModelScope.launch {
            _uiState.update { it.copy(createForm = it.createForm.copy(isSubmitting = true)) }
            try {
                val created = try {
                    remoteRepository.createCapsule(request)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SessionExpiredException) {
                    handleUnauthorized()
                    return@launch
                } catch (e: Exception) {
                    val reason = e.localizedMessage ?: e.javaClass.simpleName
                    _snackbarEvents.tryEmit(appContext.getString(R.string.network_error_snackbar, reason))
                    mockRepository.createCapsule(request)
                }

                _uiState.update { current ->
                    current.copy(
                        capsules = current.capsules + created,
                        selectedCapsuleId = created.id,
                        createForm = CreateCapsuleFormState(
                            selectedParticipantIds = setOf("user_maga", "user_zainab") + creatorId
                        )
                    )
                }
                _snackbarEvents.tryEmit(appContext.getString(R.string.create_capsule_success, created.title))
                onCreated()
            } finally {
                _uiState.update { it.copy(createForm = it.createForm.copy(isSubmitting = false)) }
            }
        }
    }

    // --- PIN & Privacy Management ---
    fun openSetPinDialog() {
        _uiState.update { it.copy(showSetPinDialog = true, pinError = null) }
    }

    fun dismissSetPinDialog() {
        _uiState.update { it.copy(showSetPinDialog = false, pinError = null) }
    }

    fun openPinPromptDialog() {
        _uiState.update { it.copy(showPinPromptDialog = true, pinError = null) }
    }

    fun setupPin(pin: String) {
        if (pin.length != 4) return
        viewModelScope.launch {
            UserPreferences.setPin(appContext, pin)
            _uiState.update { it.copy(isPinSet = true, isPinUnlocked = true, showSetPinDialog = false) }
            _snackbarEvents.tryEmit(appContext.getString(R.string.pin_set_success))
        }
    }

    fun verifyPin(pin: String) {
        if (pin.length != 4) return
        viewModelScope.launch {
            val valid = UserPreferences.verifyPin(appContext, pin)
            if (valid) {
                _uiState.update { it.copy(isPinUnlocked = true, showPinPromptDialog = false, pinError = null) }
            } else {
                _uiState.update { it.copy(pinError = appContext.getString(R.string.pin_incorrect)) }
            }
        }
    }

    fun removePin() {
        viewModelScope.launch {
            UserPreferences.clearPin(appContext)
            _uiState.update { it.copy(isPinSet = false, isPinUnlocked = true) }
            _snackbarEvents.tryEmit(appContext.getString(R.string.pin_removed_success))
        }
    }

    fun forgotPin() {
        logout()
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            UserPreferences.setNotificationsEnabled(appContext, enabled)
            _uiState.update { it.copy(isNotificationsEnabled = enabled) }
        }
    }

    // --- Data Management: Export & Delete ---
    fun exportData(context: Context) {
        val capsulesList = _uiState.value.capsules
        val jsonArray = buildJsonObject {
            put("exportedAt", System.currentTimeMillis())
            put("user", _uiState.value.currentUser?.username ?: "")
            putJsonArray("capsules") {
                capsulesList.forEach { c ->
                    add(
                        buildJsonObject {
                            put("id", c.id)
                            put("title", c.title)
                            put("kind", c.kind)
                            put("status", c.status)
                            put("recipient", c.recipient ?: "")
                            put("isGeoLocked", c.isGeoLocked)
                            putJsonArray("tags") {
                                c.tags.forEach { tag -> add(kotlinx.serialization.json.JsonPrimitive(tag)) }
                            }
                            putJsonArray("stages") {
                                c.stages.forEach { st ->
                                    add(
                                        buildJsonObject {
                                            put("stageNumber", st.stageNumber)
                                            put("unlockDate", st.unlockDate)
                                            put("isUnlocked", st.isUnlocked)
                                            // Только расшифрованный текст для уже открытых
                                            val textExport = if (c.status == "OPENED" || st.isUnlocked) {
                                                st.text
                                            } else {
                                                context.getString(R.string.locked_stage_placeholder)
                                            }
                                            put("text", textExport)
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
        val jsonString = exportJson.encodeToString(jsonArray)

        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, jsonString)
                type = "application/json"
            }
            val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.export_chooser_title))
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
            _snackbarEvents.tryEmit(context.getString(R.string.export_copied_notice))
        } catch (_: Exception) {
            _snackbarEvents.tryEmit(jsonString.take(100) + "…")
        }
    }

    fun openConfirmDeleteDataDialog() {
        _uiState.update { it.copy(showConfirmDeleteDataDialog = true) }
    }

    fun dismissConfirmDeleteDataDialog() {
        _uiState.update { it.copy(showConfirmDeleteDataDialog = false) }
    }

    fun deleteMyData() {
        dismissConfirmDeleteDataDialog()
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingData = true) }
            try {
                try {
                    remoteRepository.deleteMyData()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    mockRepository.deleteMyData()
                }
                UserPreferences.clearPin(appContext)
                ApiConfig.clearSession(appContext)
                _uiState.update { state ->
                    state.copy(
                        isAuthenticated = false,
                        currentUser = null,
                        capsules = emptyList(),
                        selectedCapsuleId = null,
                        isDeletingData = false,
                        isPinSet = false,
                        isPinUnlocked = true
                    )
                }
                _snackbarEvents.tryEmit(appContext.getString(R.string.data_deleted_success))
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeletingData = false) }
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Ошибка удаления данных")
            }
        }
    }

    // --- About Dialogs ---
    fun openAboutDialog(type: String) {
        _uiState.update { it.copy(showAboutDialog = true, aboutDialogType = type) }
    }

    fun dismissAboutDialog() {
        _uiState.update { it.copy(showAboutDialog = false) }
    }
}
