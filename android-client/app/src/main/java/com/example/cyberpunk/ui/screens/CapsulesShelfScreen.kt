package com.example.cyberpunk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cyberpunk.R
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoError
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSecondary
import com.example.cyberpunk.ui.theme.TokyoSuccess
import com.example.cyberpunk.ui.theme.TokyoSurface
import com.example.cyberpunk.ui.viewmodel.CapsuleUiState
import com.timecapsule.shared.CapsuleDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CapsulesShelfScreen(
    uiState: CapsuleUiState,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTabSelected: (Int) -> Unit,
    onCodeCopied: (String) -> Unit,
    onOpenCapsule: (String) -> Unit,
    onArchiveCapsule: (String) -> Unit,
    onUnarchiveCapsule: (String) -> Unit,
    onOpenRadar: (String) -> Unit,
    onOpenTimeline: (String) -> Unit,
    onNavigateToCreate: () -> Unit
) {
    val myCapsules = uiState.myCapsules
    val searchQuery = uiState.searchQuery.trim()

    // 1. Client-side Search filter: title, kind, recipient, #tag
    val searchedList = if (searchQuery.isEmpty()) {
        myCapsules
    } else {
        val cleanTagQuery = searchQuery.removePrefix("#")
        myCapsules.filter { c ->
            c.title.contains(searchQuery, ignoreCase = true) ||
                c.kind.contains(searchQuery, ignoreCase = true) ||
                (c.recipient?.contains(searchQuery, ignoreCase = true) == true) ||
                c.tags.any { it.contains(cleanTagQuery, ignoreCase = true) }
        }
    }

    // 2. Tab filter: 0 = SEALED, 1 = READY, 2 = OPENED, 3 = ARCHIVED
    val displayedList = when (uiState.selectedTab) {
        0 -> searchedList.filter { (it.status == "SEALED" || (!it.isFullyUnlocked && it.status != "OPENED")) && !it.archived && it.status != "ARCHIVED" }
        1 -> searchedList.filter { (it.status == "READY" || (it.isFullyUnlocked && it.status != "OPENED")) && !it.archived && it.status != "ARCHIVED" }
        2 -> searchedList.filter { it.status == "OPENED" && !it.archived }
        3 -> searchedList.filter { it.status == "ARCHIVED" || it.archived }
        else -> searchedList
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshingCapsules,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(TokyoBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header & Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.capsules_shelf_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    )
                )

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_capsules_hint),
                            color = TokyoOnSurface.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TokyoPrimary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TokyoSurface,
                        unfocusedContainerColor = TokyoSurface,
                        focusedBorderColor = TokyoPrimary,
                        unfocusedBorderColor = TokyoPrimary.copy(alpha = 0.25f),
                        focusedTextColor = TokyoOnBackground,
                        unfocusedTextColor = TokyoOnBackground
                    )
                )
            }

            // Status Tabs
            val tabTitles = listOf(
                stringResource(R.string.tab_sealed),
                stringResource(R.string.tab_ready),
                stringResource(R.string.tab_opened),
                stringResource(R.string.tab_archive)
            )

            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = TokyoBackground,
                contentColor = TokyoPrimary,
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (uiState.selectedTab == index) TokyoPrimary else TokyoOnSurface
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content List or Empty State
            if (uiState.isLoading && uiState.capsules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TokyoPrimary)
                }
            } else if (displayedList.isEmpty()) {
                EmptyTabState(
                    selectedTab = uiState.selectedTab,
                    onNavigateToCreate = onNavigateToCreate
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(displayedList, key = { it.id }) { capsule ->
                        ShelfCapsuleCard(
                            capsule = capsule,
                            currentUserId = uiState.currentUserId,
                            onCodeCopied = onCodeCopied,
                            onOpenCapsule = {
                                onOpenCapsule(capsule.id)
                                onOpenTimeline(capsule.id)
                            },
                            onOpenTimeline = { onOpenTimeline(capsule.id) },
                            onOpenRadar = { onOpenRadar(capsule.id) },
                            onArchive = { onArchiveCapsule(capsule.id) },
                            onUnarchive = { onUnarchiveCapsule(capsule.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfCapsuleCard(
    capsule: CapsuleDto,
    currentUserId: String?,
    onCodeCopied: (String) -> Unit,
    onOpenCapsule: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenRadar: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }

    val statusColor = when (capsule.status) {
        "READY" -> TokyoAccent
        "OPENED" -> TokyoSuccess
        "ARCHIVED" -> TokyoOnSurface
        else -> TokyoPrimary
    }

    val statusBadgeText = when (capsule.status) {
        "READY" -> stringResource(R.string.capsule_badge_ready)
        "OPENED" -> stringResource(R.string.capsule_badge_opened)
        "ARCHIVED" -> stringResource(R.string.capsule_badge_archived)
        else -> stringResource(R.string.capsule_badge_sealed)
    }

    val isReady = capsule.status == "READY"
    val isOpened = capsule.status == "OPENED"
    val isArchived = capsule.status == "ARCHIVED" || capsule.archived

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when {
                    isReady -> onOpenCapsule()
                    isOpened -> onOpenTimeline()
                    capsule.isGeoLocked -> onOpenRadar()
                    else -> onOpenTimeline()
                }
            }
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = TokyoSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Status Badge, Kind, Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusBadgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontSize = 11.sp
                            )
                        )
                    }

                    if (capsule.isGeoLocked) {
                        Box(
                            modifier = Modifier
                                .background(TokyoSecondary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ГЕО",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TokyoSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = TokyoOnSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(TokyoSurface)
                    ) {
                        if (isArchived) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_unarchive), color = TokyoOnBackground) },
                                leadingIcon = { Icon(Icons.Default.Unarchive, null, tint = TokyoPrimary) },
                                onClick = {
                                    showMenu = false
                                    onUnarchive()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_archive), color = TokyoOnBackground) },
                                leadingIcon = { Icon(Icons.Default.Archive, null, tint = TokyoSecondary) },
                                onClick = {
                                    showMenu = false
                                    onArchive()
                                }
                            )
                        }
                    }
                }
            }

            // Title
            Text(
                text = capsule.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TokyoOnBackground
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Recipient if present
            if (!capsule.recipient.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.recipient_format, capsule.recipient),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TokyoSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // Tags
            if (capsule.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    capsule.tags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(TokyoBackground.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TokyoAccent,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // Unlock Date
            val firstStage = capsule.stages.firstOrNull()
            if (firstStage != null) {
                val dateStr = formatDateTime(firstStage.unlockDate)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (firstStage.isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (firstStage.isUnlocked) TokyoSuccess else TokyoOnSurface,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.stage_unlock_date_format, dateStr),
                        style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                    )
                }
            }

            // Geo Capsule: Invite Code Chip
            if (!capsule.inviteCode.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(capsule.inviteCode))
                            onCodeCopied(capsule.inviteCode)
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.invite_code_badge, capsule.inviteCode),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = TokyoAccent
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = TokyoAccent.copy(alpha = 0.12f),
                            labelColor = TokyoAccent
                        ),
                        border = BorderStroke(1.dp, TokyoAccent.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Action button for READY
            if (isReady) {
                Button(
                    onClick = onOpenCapsule,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TokyoAccent,
                        contentColor = TokyoBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_open_now),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTabState(
    selectedTab: Int,
    onNavigateToCreate: () -> Unit
) {
    val (titleRes, descRes) = when (selectedTab) {
        0 -> R.string.empty_sealed_title to R.string.empty_sealed_desc
        1 -> R.string.empty_ready_title to R.string.empty_ready_desc
        2 -> R.string.empty_opened_title to R.string.empty_opened_desc
        3 -> R.string.empty_archive_title to R.string.empty_archive_desc
        else -> R.string.my_capsules_empty_title to R.string.my_capsules_empty_desc
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(TokyoSurface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.HourglassTop,
                contentDescription = null,
                tint = TokyoPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TokyoOnBackground,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(descRes),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TokyoOnSurface,
                textAlign = TextAlign.Center
            )
        )

        if (selectedTab == 0) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onNavigateToCreate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TokyoPrimary,
                    contentColor = TokyoBackground
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.btn_create_first_capsule))
            }
        }
    }
}

private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatDateTime(millis: Long): String {
    return runCatching {
        formatter.format(Instant.ofEpochMilli(millis))
    }.getOrDefault("$millis")
}

