package com.example.cyberpunk.ui.screens

import androidx.compose.runtime.Composable
import com.example.cyberpunk.ui.viewmodel.CapsuleUiState

@Composable
fun MyCapsulesScreen(
    uiState: CapsuleUiState,
    onRefresh: () -> Unit,
    onOpenJoinDialog: () -> Unit,
    onCodeCopied: (String) -> Unit,
    onSelectCapsule: (String) -> Unit,
    onOpenRadar: (String) -> Unit,
    onOpenTimeline: (String) -> Unit,
    onNavigateToCreate: () -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onTabSelected: (Int) -> Unit = {},
    onOpenCapsule: (String) -> Unit = onOpenTimeline,
    onArchiveCapsule: (String) -> Unit = {},
    onUnarchiveCapsule: (String) -> Unit = {},
    onBlockSender: (String) -> Unit = {}
) {
    CapsulesShelfScreen(
        uiState = uiState,
        onRefresh = onRefresh,
        onSearchQueryChange = onSearchQueryChange,
        onTabSelected = onTabSelected,
        onCodeCopied = onCodeCopied,
        onOpenCapsule = onOpenCapsule,
        onArchiveCapsule = onArchiveCapsule,
        onUnarchiveCapsule = onUnarchiveCapsule,
        onOpenRadar = onOpenRadar,
        onOpenTimeline = onOpenTimeline,
        onNavigateToCreate = onNavigateToCreate,
        onBlockSender = onBlockSender
    )
}
