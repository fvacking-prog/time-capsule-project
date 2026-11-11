package com.example.cyberpunk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.cyberpunk.ui.components.JoinCapsuleDialog
import com.example.cyberpunk.ui.components.ProfileSelectionDialog
import com.example.cyberpunk.ui.components.ServerSettingsDialog
import com.example.cyberpunk.ui.components.TimeCapsuleTopBar
import com.example.cyberpunk.ui.screens.CapsuleStagesTimeline
import com.example.cyberpunk.ui.screens.CapsulesShelfScreen
import com.example.cyberpunk.ui.screens.ChooseHandleScreen
import com.example.cyberpunk.ui.screens.CreateCapsuleScreen
import com.example.cyberpunk.ui.screens.EnterPinDialog
import com.example.cyberpunk.ui.screens.HomeScreen
import com.example.cyberpunk.ui.screens.IdeasScreen
import com.example.cyberpunk.ui.screens.LoginScreen
import com.example.cyberpunk.ui.screens.MyCapsulesScreen
import com.example.cyberpunk.ui.screens.RadarScreen
import com.example.cyberpunk.ui.screens.SetPinDialog
import com.example.cyberpunk.ui.screens.SettingsScreen
import com.example.cyberpunk.ui.theme.TimeCapsuleTheme
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.example.cyberpunk.ui.viewmodel.CapsuleViewModel
import kotlinx.coroutines.flow.collectLatest

private const val ROUTE_HOME = "home"
private const val ROUTE_CAPSULES = "capsules"
private const val ROUTE_CREATE = "create"
private const val ROUTE_IDEAS = "ideas"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_RADAR = "radar"
private const val ROUTE_TIMELINE = "timeline"
private const val ROUTE_MY_CAPSULES = "my_capsules"

class MainActivity : ComponentActivity() {
    private val capsuleViewModel: CapsuleViewModel by viewModels()
    private var appWentToBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimeCapsuleTheme {
                TimeCapsuleApp(capsuleViewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            appWentToBackground = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (appWentToBackground) {
            appWentToBackground = false
            capsuleViewModel.onAppForegrounded()
        }
    }
}

@Composable
fun TimeCapsuleApp(
    capsuleViewModel: CapsuleViewModel = viewModel()
) {
    val uiState by capsuleViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(capsuleViewModel) {
        capsuleViewModel.snackbarEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (BuildConfig.DEBUG && uiState.showSettingsDialog) {
        ServerSettingsDialog(
            urlInput = uiState.settingsUrlInput,
            isChecking = uiState.isCheckingHealth,
            healthText = uiState.healthResultText,
            healthSuccess = uiState.healthResultSuccess,
            onUrlChange = capsuleViewModel::updateSettingsUrlInput,
            onCheckHealth = capsuleViewModel::checkServerHealth,
            onSave = capsuleViewModel::saveServerUrl,
            onDismiss = capsuleViewModel::dismissSettingsDialog
        )
    }

    if (uiState.showSetPinDialog) {
        SetPinDialog(
            onDismiss = capsuleViewModel::dismissSetPinDialog,
            onPinSet = capsuleViewModel::setupPin
        )
    }

    if (uiState.isPinSet && !uiState.isPinUnlocked) {
        EnterPinDialog(
            errorText = uiState.pinError,
            onPinEntered = capsuleViewModel::verifyPin,
            onForgotPin = capsuleViewModel::forgotPin
        )
    }

    if (!uiState.isAuthInitialized) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = TokyoBackground,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TokyoPrimary)
            }
        }
        return
    }

    if (!uiState.isAuthenticated) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = TokyoBackground,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LoginScreen(
                    isAuthenticating = uiState.isAuthenticating,
                    onGoogleIdTokenReceived = capsuleViewModel::signInWithGoogle,
                    onGoogleSignInError = capsuleViewModel::onGoogleSignInError,
                    onOpenSettings = capsuleViewModel::openSettingsDialog
                )
            }
        }
        return
    }

    if (uiState.currentUser?.handle.isNullOrBlank()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = TokyoBackground,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ChooseHandleScreen(
                    currentUser = uiState.currentUser,
                    handleInput = uiState.handleInput,
                    handleStatus = uiState.handleCheckStatus,
                    isSaving = uiState.isSavingHandle,
                    onHandleInputChange = capsuleViewModel::updateHandleInput,
                    onSubmit = capsuleViewModel::submitHandle,
                    onLogout = capsuleViewModel::logout
                )
            }
        }
        return
    }

    if (uiState.showProfileDialog) {
        ProfileSelectionDialog(
            currentUser = uiState.currentUser,
            onLogout = capsuleViewModel::logout,
            onDismiss = capsuleViewModel::dismissProfileDialog
        )
    }

    if (uiState.showJoinDialog) {
        JoinCapsuleDialog(
            codeInput = uiState.joinInviteCodeInput,
            isJoining = uiState.isJoiningCapsule,
            onCodeChange = capsuleViewModel::updateJoinInviteCodeInput,
            onJoin = {
                capsuleViewModel.joinCapsuleByCode {
                    navController.navigate(ROUTE_CAPSULES) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                    }
                }
            },
            onDismiss = capsuleViewModel::dismissJoinDialog
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = TokyoBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TimeCapsuleTopBar(
                currentUsername = uiState.currentUsername,
                currentUserHandle = uiState.currentUserHandle,
                currentPhotoUrl = uiState.currentPhotoUrl,
                onProfileClick = capsuleViewModel::openProfileDialog,
                onSettingsClick = capsuleViewModel::openSettingsDialog
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = TokyoSurface
            ) {
                // 1. Главная
                NavigationBarItem(
                    selected = currentRoute == ROUTE_HOME,
                    onClick = {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = stringResource(R.string.nav_home)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_home)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TokyoPrimary,
                        selectedTextColor = TokyoPrimary,
                        indicatorColor = TokyoPrimary.copy(alpha = 0.18f),
                        unselectedIconColor = TokyoOnSurface,
                        unselectedTextColor = TokyoOnSurface
                    )
                )

                // 2. Капсулы
                NavigationBarItem(
                    selected = currentRoute == ROUTE_CAPSULES || currentRoute == ROUTE_MY_CAPSULES,
                    onClick = {
                        navController.navigate(ROUTE_CAPSULES) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadInboxCount > 0) {
                                    Badge(
                                        containerColor = TokyoAccent,
                                        contentColor = TokyoBackground
                                    ) {
                                        Text(
                                            text = if (uiState.unreadInboxCount > 99) "99+" else "${uiState.unreadInboxCount}",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AllInbox,
                                contentDescription = stringResource(R.string.nav_capsules)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.nav_capsules)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TokyoPrimary,
                        selectedTextColor = TokyoPrimary,
                        indicatorColor = TokyoPrimary.copy(alpha = 0.18f),
                        unselectedIconColor = TokyoOnSurface,
                        unselectedTextColor = TokyoOnSurface
                    )
                )

                // 3. Создать
                NavigationBarItem(
                    selected = currentRoute == ROUTE_CREATE,
                    onClick = {
                        navController.navigate(ROUTE_CREATE) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = stringResource(R.string.nav_create)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_create)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TokyoPrimary,
                        selectedTextColor = TokyoPrimary,
                        indicatorColor = TokyoPrimary.copy(alpha = 0.18f),
                        unselectedIconColor = TokyoOnSurface,
                        unselectedTextColor = TokyoOnSurface
                    )
                )

                // 4. Идеи
                NavigationBarItem(
                    selected = currentRoute == ROUTE_IDEAS,
                    onClick = {
                        navController.navigate(ROUTE_IDEAS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.nav_ideas)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_ideas)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TokyoPrimary,
                        selectedTextColor = TokyoPrimary,
                        indicatorColor = TokyoPrimary.copy(alpha = 0.18f),
                        unselectedIconColor = TokyoOnSurface,
                        unselectedTextColor = TokyoOnSurface
                    )
                )

                // 5. Настройки
                NavigationBarItem(
                    selected = currentRoute == ROUTE_SETTINGS,
                    onClick = {
                        navController.navigate(ROUTE_SETTINGS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TokyoPrimary,
                        selectedTextColor = TokyoPrimary,
                        indicatorColor = TokyoPrimary.copy(alpha = 0.18f),
                        unselectedIconColor = TokyoOnSurface,
                        unselectedTextColor = TokyoOnSurface
                    )
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    uiState = uiState,
                    onNavigateToCreate = {
                        navController.navigate(ROUTE_CREATE) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToRadar = {
                        navController.navigate(ROUTE_RADAR)
                    },
                    onOpenJoinDialog = capsuleViewModel::openJoinDialog,
                    onNavigateToCapsules = {
                        navController.navigate(ROUTE_CAPSULES)
                    }
                )
            }

            composable(ROUTE_CAPSULES) {
                CapsulesShelfScreen(
                    uiState = uiState,
                    onRefresh = { capsuleViewModel.loadCapsules(isPullToRefresh = true) },
                    onSearchQueryChange = capsuleViewModel::setSearchQuery,
                    onTabSelected = capsuleViewModel::setSelectedTab,
                    onCodeCopied = capsuleViewModel::notifyInviteCodeCopied,
                    onOpenCapsule = { id ->
                        capsuleViewModel.selectCapsule(id)
                        capsuleViewModel.openCapsule(id) {
                            navController.navigate(ROUTE_TIMELINE)
                        }
                    },
                    onArchiveCapsule = capsuleViewModel::archiveCapsule,
                    onUnarchiveCapsule = capsuleViewModel::unarchiveCapsule,
                    onOpenRadar = { id ->
                        capsuleViewModel.selectCapsule(id)
                        navController.navigate(ROUTE_RADAR)
                    },
                    onOpenTimeline = { id ->
                        capsuleViewModel.selectCapsule(id)
                        navController.navigate(ROUTE_TIMELINE)
                    },
                    onNavigateToCreate = {
                        navController.navigate(ROUTE_CREATE)
                    },
                    onBlockSender = capsuleViewModel::blockSender
                )
            }

            composable(ROUTE_MY_CAPSULES) {
                MyCapsulesScreen(
                    uiState = uiState,
                    onRefresh = { capsuleViewModel.loadCapsules(isPullToRefresh = true) },
                    onOpenJoinDialog = capsuleViewModel::openJoinDialog,
                    onCodeCopied = capsuleViewModel::notifyInviteCodeCopied,
                    onSelectCapsule = capsuleViewModel::selectCapsule,
                    onOpenRadar = { capsuleId ->
                        capsuleViewModel.selectCapsule(capsuleId)
                        navController.navigate(ROUTE_RADAR)
                    },
                    onOpenTimeline = { capsuleId ->
                        capsuleViewModel.selectCapsule(capsuleId)
                        navController.navigate(ROUTE_TIMELINE)
                    },
                    onNavigateToCreate = {
                        navController.navigate(ROUTE_CREATE)
                    },
                    onSearchQueryChange = capsuleViewModel::setSearchQuery,
                    onTabSelected = capsuleViewModel::setSelectedTab,
                    onOpenCapsule = { id ->
                        capsuleViewModel.selectCapsule(id)
                        capsuleViewModel.openCapsule(id) {
                            navController.navigate(ROUTE_TIMELINE)
                        }
                    },
                    onArchiveCapsule = capsuleViewModel::archiveCapsule,
                    onUnarchiveCapsule = capsuleViewModel::unarchiveCapsule,
                    onBlockSender = capsuleViewModel::blockSender
                )
            }

            composable(ROUTE_CREATE) {
                CreateCapsuleScreen(
                    formState = uiState.createForm,
                    currentUserId = uiState.currentUserId,
                    availableUsers = uiState.availableUsers,
                    showConfirmDialog = uiState.showConfirmSealDialog,
                    onTitleChange = capsuleViewModel::updateCreateTitle,
                    onKindChange = capsuleViewModel::updateCreateKind,
                    onRecipientChange = capsuleViewModel::updateCreateRecipient,
                    onRecipientSearchQueryChange = capsuleViewModel::updateRecipientSearchQuery,
                    onAddRecipient = capsuleViewModel::addRecipient,
                    onRemoveRecipient = capsuleViewModel::removeRecipient,
                    onTagsChange = capsuleViewModel::updateCreateTags,
                    onMessageChange = capsuleViewModel::updateCreateMessage,
                    onDateChipClick = capsuleViewModel::setCreateDateChip,
                    onCustomDateSelect = capsuleViewModel::setCreateCustomDate,
                    onCustomTimeSelect = capsuleViewModel::setCreateCustomTime,
                    onGeoLockedToggle = capsuleViewModel::updateCreateIsGeoLocked,
                    onTakeMyLocation = capsuleViewModel::fetchLocationForCreateForm,
                    onRadiusChange = capsuleViewModel::updateCreateRadius,
                    onParticipantSearchQueryChange = capsuleViewModel::updateParticipantSearchQuery,
                    onToggleParticipant = capsuleViewModel::toggleParticipantSelection,
                    onAddStage = capsuleViewModel::addCreateStage,
                    onRemoveStage = capsuleViewModel::removeCreateStage,
                    onStageDateChange = capsuleViewModel::updateCreateStageDate,
                    onStageTimeChange = capsuleViewModel::updateCreateStageTime,
                    onStageSecretChange = capsuleViewModel::updateCreateStageSecret,
                    onOpenConfirmDialog = capsuleViewModel::openConfirmSealDialog,
                    onDismissConfirmDialog = capsuleViewModel::dismissConfirmSealDialog,
                    onSubmit = {
                        capsuleViewModel.submitCreateCapsule(
                            onCreated = {
                                navController.navigate(ROUTE_CAPSULES) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                )
            }

            composable(ROUTE_IDEAS) {
                IdeasScreen(
                    onSelectTemplate = { title, prompt, tags, daysAhead ->
                        capsuleViewModel.applyTemplate(title, prompt, tags, daysAhead) {
                            navController.navigate(ROUTE_CREATE) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    uiState = uiState,
                    onOpenServerSettings = capsuleViewModel::openSettingsDialog,
                    onOpenSetPinDialog = capsuleViewModel::openSetPinDialog,
                    onRemovePin = capsuleViewModel::removePin,
                    onToggleNotifications = capsuleViewModel::toggleNotifications,
                    onExportData = { capsuleViewModel.exportData(context) },
                    onOpenDeleteDataDialog = capsuleViewModel::openConfirmDeleteDataDialog,
                    onDismissDeleteDataDialog = capsuleViewModel::dismissConfirmDeleteDataDialog,
                    onConfirmDeleteData = capsuleViewModel::deleteMyData,
                    onOpenAboutDialog = capsuleViewModel::openAboutDialog,
                    onDismissAboutDialog = capsuleViewModel::dismissAboutDialog,
                    onLogout = capsuleViewModel::logout
                )
            }

            composable(ROUTE_RADAR) {
                RadarScreen(
                    uiState = uiState,
                    onPermissionResult = capsuleViewModel::updateLocationPermission,
                    onSelectCapsule = capsuleViewModel::selectCapsule,
                    onCapsule3DClick = {
                        capsuleViewModel.onCapsuleClicked(
                            onOpenTimeline = { navController.navigate(ROUTE_TIMELINE) }
                        )
                    },
                    onDismissLockedDialog = capsuleViewModel::dismissLockedDialog,
                    onRealHeartbeatTick = { cid, _ -> capsuleViewModel.performRealHeartbeatTick(cid) },
                    onRefreshClick = capsuleViewModel::loadCapsules
                )
            }

            composable(ROUTE_TIMELINE) {
                CapsuleStagesTimeline(
                    capsule = uiState.selectedCapsule,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
