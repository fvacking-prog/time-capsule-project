package com.example.cyberpunk.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cyberpunk.BuildConfig
import com.example.cyberpunk.R
import com.example.cyberpunk.data.ApiConfig
import com.example.cyberpunk.data.CapsuleRepository
import com.example.cyberpunk.data.CapsuleUnlockScheduler
import com.example.cyberpunk.data.GeoHelper
import com.example.cyberpunk.data.HandleLocalValidation
import com.example.cyberpunk.data.HandleValidator
import com.example.cyberpunk.data.LocationProvider
import com.example.cyberpunk.data.RemoteCapsuleRepository
import com.example.cyberpunk.data.SessionExpiredException
import com.example.cyberpunk.data.UnlockDateTimeHelper
import com.example.cyberpunk.data.UserPreferences
import com.timecapsule.shared.CapsuleDto
import com.timecapsule.shared.CheckInRequest
import com.timecapsule.shared.CreateCapsuleRequest
import com.timecapsule.shared.CreateStageRequest
import com.timecapsule.shared.UserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
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
    val unlockDateMillis: Long = UnlockDateTimeHelper.defaultUnlockMillis(daysAhead = 7),
    val selectedDateChip: String = "WEEK",
    val isGeoLocked: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Float = 50f,
    val participantSearchQuery: String = "",
    val selectedParticipantIds: Set<String> = emptySet(),
    val recipientSearchQuery: String = "",
    val selectedRecipients: List<UserDto> = emptyList(),
    val recipientSearchResults: List<UserDto> = emptyList(),
    val isSearchingRecipients: Boolean = false,
    val stages: List<DraftStageItem> = listOf(
        DraftStageItem(
            unlockDateMillis = UnlockDateTimeHelper.defaultUnlockMillis(daysAhead = 7),
            secretText = ""
        )
    ),
    val isFetchingLocation: Boolean = false,
    val isSubmitting: Boolean = false
)

enum class HandleCheckStatus {
    IDLE,
    CHECKING,
    FREE,
    TAKEN,
    RESERVED,
    INVALID_FORMAT,
    ERROR
}

data class CapsuleUiState(
    val isAuthInitialized: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isAuthenticating: Boolean = false,
    val currentUser: UserDto? = null,
    val handleInput: String = "",
    val handleCheckStatus: HandleCheckStatus = HandleCheckStatus.IDLE,
    val isSavingHandle: Boolean = false,
    val availableUsers: List<UserDto> = emptyList(),
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
    val hasLocationPermission: Boolean = false,
    val deviceLat: Double? = null,
    val deviceLon: Double? = null,
    val lockedDialogMissingText: String? = null,
    val capsulesLoadError: String? = null,
    val isServerWakingUp: Boolean = false,
    val isRefreshingCapsules: Boolean = false,
    val createForm: CreateCapsuleFormState = CreateCapsuleFormState(),
    val searchQuery: String = "",
    val selectedTab: Int = 0, // 0: SEALED, 1: INBOX, 2: READY, 3: OPENED, 4: ARCHIVED
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
                GeoHelper.haversineMeters(deviceLat, deviceLon, c.latitude, c.longitude)
            }
        }

    val currentUserId: String?
        get() = currentUser?.id

    val currentUsername: String
        get() = currentUser?.username ?: ""

    val currentUserHandle: String?
        get() = currentUser?.handle

    val currentPhotoUrl: String?
        get() = currentUser?.avatarUrl ?: currentUser?.photoUrl

    val unreadInboxCount: Int
        get() = myCapsules.count { c ->
            (c.isInbox || (c.creatorId != currentUserId && !c.isGeoLocked)) &&
                !c.archived &&
                c.status != "ARCHIVED" &&
                c.openedAt == null
        }
}

class CapsuleViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val locationProvider = LocationProvider(appContext)

    private val remoteRepository: CapsuleRepository = RemoteCapsuleRepository(
        onWakeUpStateChange = { waking ->
            val wasWaking = _uiState.value.isServerWakingUp
            _uiState.update { it.copy(isServerWakingUp = waking) }
            if (waking && !wasWaking) {
                _snackbarEvents.tryEmit(appContext.getString(R.string.server_waking_up))
            }
        }
    )

    private val _uiState = MutableStateFlow(CapsuleUiState())
    val uiState: StateFlow<CapsuleUiState> = _uiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            val msg = throwable.localizedMessage ?: throwable.message ?: appContext.getString(R.string.error_generic)
            _snackbarEvents.tryEmit(msg)
        }
    }

    private var userSearchJob: Job? = null
    private var recipientSearchJob: Job? = null
    private var handleCheckJob: Job? = null

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
                val (user, token) = savedSession
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
                refreshMeInBackground(token)
                if (!user.handle.isNullOrBlank()) {
                    loadCapsules()
                    refreshUsersList("")
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
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

    private fun refreshMeInBackground(token: String) {
        viewModelScope.launch {
            try {
                val freshUser = remoteRepository.getMe()
                ApiConfig.saveSession(appContext, freshUser, token)
                _uiState.update { state ->
                    state.copy(
                        currentUser = freshUser,
                        availableUsers = mergeUsers(state.availableUsers, listOf(freshUser))
                    )
                }
                if (!freshUser.handle.isNullOrBlank() && _uiState.value.capsules.isEmpty()) {
                    loadCapsules()
                    refreshUsersList("")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
            } catch (_: Exception) {
                // Остаёмся на кэшированной сессии при временной потере сети
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
                        handleInput = "",
                        handleCheckStatus = HandleCheckStatus.IDLE,
                        isSavingHandle = false,
                        availableUsers = mergeUsers(state.availableUsers, listOf(authResponse.user)),
                        createForm = state.createForm.copy(
                            selectedParticipantIds = state.createForm.selectedParticipantIds + authResponse.user.id
                        )
                    )
                }
                if (!authResponse.user.handle.isNullOrBlank()) {
                    loadCapsules()
                    refreshUsersList("")
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
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

    fun updateHandleInput(raw: String) {
        val clean = HandleValidator.sanitizeInput(raw)
        handleCheckJob?.cancel()

        when (HandleValidator.validateLocal(clean)) {
            HandleLocalValidation.EMPTY -> {
                _uiState.update {
                    it.copy(handleInput = clean, handleCheckStatus = HandleCheckStatus.IDLE)
                }
            }
            HandleLocalValidation.INVALID_FORMAT -> {
                _uiState.update {
                    it.copy(handleInput = clean, handleCheckStatus = HandleCheckStatus.INVALID_FORMAT)
                }
            }
            HandleLocalValidation.RESERVED -> {
                _uiState.update {
                    it.copy(handleInput = clean, handleCheckStatus = HandleCheckStatus.RESERVED)
                }
            }
            HandleLocalValidation.VALID -> {
                _uiState.update {
                    it.copy(handleInput = clean, handleCheckStatus = HandleCheckStatus.CHECKING)
                }
                handleCheckJob = viewModelScope.launch {
                    delay(400L)
                    try {
                        val response = remoteRepository.checkHandle(clean)
                        if (_uiState.value.handleInput != clean) return@launch
                        val nextStatus = when {
                            !response.valid && response.reason == "RESERVED" -> HandleCheckStatus.RESERVED
                            !response.valid -> HandleCheckStatus.INVALID_FORMAT
                            response.available -> HandleCheckStatus.FREE
                            else -> HandleCheckStatus.TAKEN
                        }
                        _uiState.update { it.copy(handleCheckStatus = nextStatus) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: SessionExpiredException) {
                        handleUnauthorized()
                    } catch (_: Exception) {
                        if (_uiState.value.handleInput == clean) {
                            _uiState.update { it.copy(handleCheckStatus = HandleCheckStatus.ERROR) }
                        }
                    }
                }
            }
        }
    }

    fun submitHandle() {
        val state = _uiState.value
        if (state.isSavingHandle) return
        val clean = HandleValidator.sanitizeInput(state.handleInput)
        if (HandleValidator.validateLocal(clean) != HandleLocalValidation.VALID) {
            _uiState.update { it.copy(handleCheckStatus = HandleCheckStatus.INVALID_FORMAT) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingHandle = true) }
            try {
                val updatedUser = remoteRepository.updateHandle(clean)
                val token = ApiConfig.sessionToken
                if (!token.isNullOrBlank()) {
                    ApiConfig.saveSession(appContext, updatedUser, token)
                }
                _uiState.update { current ->
                    current.copy(
                        currentUser = updatedUser,
                        handleInput = "",
                        handleCheckStatus = HandleCheckStatus.IDLE,
                        isSavingHandle = false,
                        availableUsers = mergeUsers(current.availableUsers, listOf(updatedUser))
                    )
                }
                _snackbarEvents.tryEmit(
                    appContext.getString(R.string.handle_saved_success, updatedUser.handle ?: clean)
                )
                loadCapsules()
                refreshUsersList("")
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                _uiState.update { it.copy(isSavingHandle = false) }
                handleUnauthorized()
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Не удалось сохранить @handle"
                val isTakenError = msg.contains("занят", ignoreCase = true) || msg.contains("409")
                _uiState.update {
                    it.copy(
                        isSavingHandle = false,
                        handleCheckStatus = if (isTakenError) HandleCheckStatus.TAKEN else HandleCheckStatus.ERROR
                    )
                }
                _snackbarEvents.tryEmit(msg)
            }
        }
    }

    fun onGoogleSignInError(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    fun logout() {
        handleCheckJob?.cancel()
        recipientSearchJob?.cancel()
        CapsuleUnlockScheduler.cancelAll(appContext)
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
                    handleInput = "",
                    handleCheckStatus = HandleCheckStatus.IDLE,
                    isSavingHandle = false,
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
                val remoteUsers = remoteRepository.searchUsers(query)
                _uiState.update { state ->
                    state.copy(availableUsers = mergeUsers(state.availableUsers, remoteUsers))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
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
        val currentCreatorId = _uiState.value.currentUserId ?: return
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

    fun updateRecipientSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                createForm = state.createForm.copy(
                    recipientSearchQuery = query,
                    isSearchingRecipients = query.trim().removePrefix("@").isNotEmpty()
                )
            )
        }
        recipientSearchJob?.cancel()
        val cleaned = query.trim()
        if (cleaned.removePrefix("@").isEmpty()) {
            _uiState.update { state ->
                state.copy(
                    createForm = state.createForm.copy(
                        recipientSearchResults = emptyList(),
                        isSearchingRecipients = false
                    )
                )
            }
            return
        }
        recipientSearchJob = viewModelScope.launch {
            delay(250L)
            try {
                val results = remoteRepository.searchUsers(cleaned)
                _uiState.update { state ->
                    val alreadySelectedIds = state.createForm.selectedRecipients.map { it.id }.toSet()
                    state.copy(
                        availableUsers = mergeUsers(state.availableUsers, results),
                        createForm = state.createForm.copy(
                            recipientSearchResults = results.filter { it.id !in alreadySelectedIds },
                            isSearchingRecipients = false
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
            } catch (_: Exception) {
                _uiState.update { state ->
                    state.copy(createForm = state.createForm.copy(isSearchingRecipients = false))
                }
            }
        }
    }

    fun addRecipient(user: UserDto) {
        val selfId = _uiState.value.currentUserId
        if (user.id == selfId) return
        val currentRecipients = _uiState.value.createForm.selectedRecipients
        if (currentRecipients.any { it.id == user.id }) {
            _uiState.update { state ->
                state.copy(
                    createForm = state.createForm.copy(
                        recipientSearchQuery = "",
                        recipientSearchResults = emptyList()
                    )
                )
            }
            return
        }
        if (currentRecipients.size >= 10) {
            _snackbarEvents.tryEmit(appContext.getString(R.string.recipients_max_limit))
            return
        }
        _uiState.update { state ->
            state.copy(
                createForm = state.createForm.copy(
                    selectedRecipients = state.createForm.selectedRecipients + user,
                    recipientSearchQuery = "",
                    recipientSearchResults = emptyList(),
                    isSearchingRecipients = false
                )
            )
        }
    }

    fun removeRecipient(userId: String) {
        _uiState.update { state ->
            state.copy(
                createForm = state.createForm.copy(
                    selectedRecipients = state.createForm.selectedRecipients.filterNot { it.id == userId }
                )
            )
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
                val joined = remoteRepository.joinCapsule(code)

                _uiState.update { state ->
                    val existing = state.capsules.filterNot { it.id == joined.id }
                    state.copy(
                        capsules = existing + joined,
                        selectedCapsuleId = joined.id,
                        showJoinDialog = false,
                        joinInviteCodeInput = ""
                    )
                }
                syncUnlockNotifications()
                _snackbarEvents.tryEmit(appContext.getString(R.string.join_success_msg, joined.title))
                onJoined()
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
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

    fun openSettingsDialog() {
        if (!BuildConfig.DEBUG) return
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
        if (!BuildConfig.DEBUG) return
        _uiState.update { it.copy(settingsUrlInput = url) }
    }

    fun saveServerUrl() {
        if (!BuildConfig.DEBUG) return
        val newUrl = _uiState.value.settingsUrlInput.trim().ifEmpty { ApiConfig.BASE_URL }
        if (!ApiConfig.isValidServerUrl(newUrl, allowCleartext = true)) {
            _snackbarEvents.tryEmit(appContext.getString(R.string.server_url_invalid))
            return
        }
        ApiConfig.saveBaseUrl(appContext, newUrl)
        _uiState.update { it.copy(showSettingsDialog = false, settingsUrlInput = ApiConfig.BASE_URL) }
        _snackbarEvents.tryEmit(appContext.getString(R.string.server_url_saved, ApiConfig.BASE_URL))
        loadCapsules()
    }

    fun checkServerHealth() {
        if (!BuildConfig.DEBUG) return
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
                val loaded = remoteRepository.getCapsules()

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
                syncUnlockNotifications()
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
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
            val currentUid = _uiState.value.currentUserId ?: return@launch

            _uiState.update { it.copy(isSyncing = true) }
            try {
                val updated = remoteRepository.checkIn(
                    capsuleId,
                    CheckInRequest(userId = currentUid, lat = lat, lon = lon)
                )
                updateCapsuleInState(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
            } catch (_: Exception) {
                // Игнорируем единичный сбой периодического чекина
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
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

    // --- Capsule Actions: Open, Archive, Unarchive, Block ---
    private fun syncUnlockNotifications() {
        val state = _uiState.value
        CapsuleUnlockScheduler.sync(appContext, state.capsules, state.isNotificationsEnabled)
    }

    fun openCapsule(capsuleId: String, onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val updated = remoteRepository.openCapsule(capsuleId)
                updateCapsuleInState(updated)
                syncUnlockNotifications()
                _snackbarEvents.tryEmit(appContext.getString(R.string.capsule_opened_success, updated.title))
                onOpened()
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
            } catch (e: Exception) {
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Ошибка открытия капсулы")
            }
        }
    }

    fun archiveCapsule(capsuleId: String) {
        viewModelScope.launch {
            try {
                val updated = remoteRepository.archiveCapsule(capsuleId)
                updateCapsuleInState(updated)
                syncUnlockNotifications()
                _snackbarEvents.tryEmit(appContext.getString(R.string.capsule_archived_success, updated.title))
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
            } catch (e: Exception) {
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Ошибка архивации")
            }
        }
    }

    fun unarchiveCapsule(capsuleId: String) {
        viewModelScope.launch {
            try {
                val updated = remoteRepository.unarchiveCapsule(capsuleId)
                updateCapsuleInState(updated)
                syncUnlockNotifications()
                _snackbarEvents.tryEmit(appContext.getString(R.string.capsule_unarchived_success, updated.title))
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
            } catch (e: Exception) {
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Ошибка разархивации")
            }
        }
    }

    fun blockSender(senderUserId: String) {
        viewModelScope.launch {
            try {
                remoteRepository.blockUser(senderUserId)
                _uiState.update { state ->
                    state.copy(
                        capsules = state.capsules.filterNot { it.creatorId == senderUserId }
                    )
                }
                syncUnlockNotifications()
                _snackbarEvents.tryEmit(appContext.getString(R.string.sender_blocked_success))
                loadCapsules(isPullToRefresh = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
            } catch (e: Exception) {
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Не удалось заблокировать отправителя")
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
        val targetMillis = UnlockDateTimeHelper.chipDateMillis(
            chipKey = chipKey,
            existingMillis = _uiState.value.createForm.unlockDateMillis
        )
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

    fun setCreateCustomDate(utcDateMillis: Long) {
        _uiState.update { state ->
            val merged = UnlockDateTimeHelper.mergePickedUtcDateWithExistingTime(
                utcDateMillis = utcDateMillis,
                existingMillis = state.createForm.unlockDateMillis
            )
            val updatedStages = state.createForm.stages.mapIndexed { idx, st ->
                if (idx == 0) st.copy(unlockDateMillis = merged) else st
            }
            state.copy(
                createForm = state.createForm.copy(
                    selectedDateChip = "CUSTOM",
                    unlockDateMillis = merged,
                    stages = updatedStages
                )
            )
        }
    }

    fun setCreateCustomTime(hour: Int, minute: Int): Boolean {
        val currentMillis = _uiState.value.createForm.unlockDateMillis
        val candidateMillis = UnlockDateTimeHelper.applyTimeOfDay(currentMillis, hour, minute)
        if (!UnlockDateTimeHelper.validateMinimumLead(candidateMillis)) {
            _snackbarEvents.tryEmit(appContext.getString(R.string.validation_unlock_min_1min))
            return false
        }
        _uiState.update { state ->
            val updatedStages = state.createForm.stages.mapIndexed { idx, st ->
                if (idx == 0) st.copy(unlockDateMillis = candidateMillis) else st
            }
            state.copy(
                createForm = state.createForm.copy(
                    selectedDateChip = "CUSTOM",
                    unlockDateMillis = candidateMillis,
                    stages = updatedStages
                )
            )
        }
        return true
    }

    fun applyTemplate(title: String, prompt: String, tags: List<String>, daysAhead: Long, onNavigateToCreate: () -> Unit) {
        val dateMillis = UnlockDateTimeHelper.defaultUnlockMillis(daysAhead = daysAhead)
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
        if (!UnlockDateTimeHelper.validateMinimumLead(form.unlockDateMillis)) {
            _snackbarEvents.tryEmit(appContext.getString(R.string.validation_unlock_min_1min))
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
            val nextDate = UnlockDateTimeHelper.defaultUnlockMillis(
                daysAhead = 30L * (state.createForm.stages.size.toLong() + 1L)
            )
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

    fun updateCreateStageDate(index: Int, utcDateMillis: Long) {
        _uiState.update { state ->
            val updated = state.createForm.stages.mapIndexed { i, item ->
                if (i == index) {
                    val merged = UnlockDateTimeHelper.mergePickedUtcDateWithExistingTime(
                        utcDateMillis = utcDateMillis,
                        existingMillis = item.unlockDateMillis
                    )
                    item.copy(unlockDateMillis = merged)
                } else {
                    item
                }
            }
            state.copy(createForm = state.createForm.copy(stages = updated))
        }
    }

    fun updateCreateStageTime(index: Int, hour: Int, minute: Int): Boolean {
        val targetItem = _uiState.value.createForm.stages.getOrNull(index) ?: return false
        val candidateMillis = UnlockDateTimeHelper.applyTimeOfDay(targetItem.unlockDateMillis, hour, minute)
        if (!UnlockDateTimeHelper.validateMinimumLead(candidateMillis)) {
            _snackbarEvents.tryEmit(appContext.getString(R.string.validation_unlock_min_1min))
            return false
        }
        _uiState.update { state ->
            val updated = state.createForm.stages.mapIndexed { i, item ->
                if (i == index) item.copy(unlockDateMillis = candidateMillis) else item
            }
            state.copy(createForm = state.createForm.copy(stages = updated))
        }
        return true
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
        val state = _uiState.value
        val form = state.createForm
        if (form.isSubmitting) return
        dismissConfirmSealDialog()
        val creatorId = state.currentUserId ?: run {
            _snackbarEvents.tryEmit(appContext.getString(R.string.auth_error_invalid_token))
            return
        }
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

        if (stages.any { !UnlockDateTimeHelper.validateMinimumLead(it.unlockDateMillis) }) {
            _snackbarEvents.tryEmit(appContext.getString(R.string.validation_unlock_min_1min))
            return
        }

        val cleanTags = form.tags.split(",")
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .take(5)

        val participants = if (form.isGeoLocked) {
            (form.selectedParticipantIds + creatorId).distinct()
        } else {
            (listOf(creatorId) + form.selectedRecipients.map { it.id }).distinct()
        }

        val recipientHandles = if (form.isGeoLocked) {
            emptyList()
        } else {
            form.selectedRecipients.mapNotNull { it.handle?.trim()?.removePrefix("@") }.filter { it.isNotEmpty() }
        }

        val recipientLabel = if (!form.isGeoLocked && form.selectedRecipients.isNotEmpty()) {
            form.selectedRecipients.joinToString(", ") { user ->
                user.handle?.let { "@$it" } ?: user.username
            }.take(255)
        } else {
            form.recipient.trim().takeIf { it.isNotEmpty() }
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
            recipient = recipientLabel,
            tags = cleanTags,
            isGeoLocked = form.isGeoLocked,
            recipientHandles = recipientHandles
        )

        viewModelScope.launch {
            _uiState.update { it.copy(createForm = it.createForm.copy(isSubmitting = true)) }
            try {
                val created = remoteRepository.createCapsule(request)

                _uiState.update { current ->
                    current.copy(
                        capsules = current.capsules + created,
                        selectedCapsuleId = created.id,
                        createForm = CreateCapsuleFormState(
                            selectedParticipantIds = setOf(creatorId)
                        )
                    )
                }
                syncUnlockNotifications()
                _snackbarEvents.tryEmit(appContext.getString(R.string.create_capsule_success, created.title))
                onCreated()
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                handleUnauthorized()
            } catch (e: Exception) {
                _snackbarEvents.tryEmit(e.localizedMessage ?: "Ошибка создания капсулы")
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
        viewModelScope.launch(coroutineExceptionHandler) {
            UserPreferences.clearPin(appContext)
            logout()
            _uiState.update { it.copy(isPinSet = false, isPinUnlocked = true, pinError = null) }
            _snackbarEvents.tryEmit(appContext.getString(R.string.pin_reset_success))
        }
    }

    fun onAppForegrounded() {
        val current = _uiState.value
        if (current.isPinSet) {
            _uiState.update { it.copy(isPinUnlocked = false) }
        }
        if (current.isAuthenticated && !current.currentUserHandle.isNullOrBlank()) {
            loadCapsules(isPullToRefresh = false)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            UserPreferences.setNotificationsEnabled(appContext, enabled)
            _uiState.update { it.copy(isNotificationsEnabled = enabled) }
            if (enabled) {
                syncUnlockNotifications()
            } else {
                CapsuleUnlockScheduler.cancelAll(appContext)
            }
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
                remoteRepository.deleteMyData()
                CapsuleUnlockScheduler.cancelAll(appContext)
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                _uiState.update { it.copy(isDeletingData = false) }
                handleUnauthorized()
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
