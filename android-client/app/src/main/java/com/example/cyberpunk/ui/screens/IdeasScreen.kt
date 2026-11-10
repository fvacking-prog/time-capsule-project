package com.example.cyberpunk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cyberpunk.R
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSecondary
import com.example.cyberpunk.ui.theme.TokyoSurface

data class CapsuleIdeaTemplate(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val iconTint: Color,
    val promptText: String,
    val tags: List<String>,
    val daysAhead: Long,
    val isComingSoon: Boolean = false
)

private val IDEAS_CATALOG = listOf(
    CapsuleIdeaTemplate(
        id = "letter_10y",
        titleRes = R.string.idea_letter_10y_title,
        descRes = R.string.idea_letter_10y_desc,
        icon = Icons.Default.HistoryEdu,
        iconTint = TokyoPrimary,
        promptText = "Привет я из 2026 года! Каким стал мир? Достиг ли ты своих главных целей? Чему научился за эти 10 лет?",
        tags = listOf("себе", "будущее", "цели"),
        daysAhead = 3650L
    ),
    CapsuleIdeaTemplate(
        id = "predictions_2036",
        titleRes = R.string.idea_predictions_2036_title,
        descRes = R.string.idea_predictions_2036_desc,
        icon = Icons.Default.Psychology,
        iconTint = TokyoAccent,
        promptText = "Мои прогнозы на 2036 год:\n1. Технологии и ИИ:\n2. Моя профессия и жизнь:\n3. Общество и мир:\nПроверим через 10 лет!",
        tags = listOf("прогнозы", "2036", "технологии"),
        daysAhead = 3650L
    ),
    CapsuleIdeaTemplate(
        id = "child_18",
        titleRes = R.string.idea_child_18_title,
        descRes = R.string.idea_child_18_desc,
        icon = Icons.Default.ChildCare,
        iconTint = TokyoSecondary,
        promptText = "Дорогой мой ребёнок, сегодня тебе исполняется 18 лет. Хочу рассказать, каким маленьким и забавным ты был, и как сильно я тебя люблю...",
        tags = listOf("семья", "ребёнок", "любовь"),
        daysAhead = 6570L
    ),
    CapsuleIdeaTemplate(
        id = "anniversary",
        titleRes = R.string.idea_anniversary_title,
        descRes = R.string.idea_anniversary_desc,
        icon = Icons.Default.Favorite,
        iconTint = Color(0xFFF7768E),
        promptText = "Письмо в нашу годовщину. Я помню этот день во всех деталях и хочу, чтобы спустя годы мы вспомнили, с чего всё начиналось...",
        tags = listOf("любовь", "годовщина", "память"),
        daysAhead = 365L
    ),
    CapsuleIdeaTemplate(
        id = "birthday",
        titleRes = R.string.idea_birthday_title,
        descRes = R.string.idea_birthday_desc,
        icon = Icons.Default.Cake,
        iconTint = TokyoAccent,
        promptText = "В этот день рождения я благодарен за людей вокруг и главные уроки года. В следующем году я обещаю себе...",
        tags = listOf("деньрождения", "эмоции", "праздник"),
        daysAhead = 365L
    ),
    CapsuleIdeaTemplate(
        id = "family_history",
        titleRes = R.string.idea_family_history_title,
        descRes = R.string.idea_family_history_desc,
        icon = Icons.Default.Diversity3,
        iconTint = TokyoPrimary,
        promptText = "История нашей семьи, рассказанная для будущих поколений. Родовые традиции, имена и воспоминания...",
        tags = listOf("семья", "история", "корни"),
        daysAhead = 1825L
    ),
    CapsuleIdeaTemplate(
        id = "photo_archive",
        titleRes = R.string.idea_photo_archive_title,
        descRes = R.string.idea_photo_archive_desc,
        icon = Icons.Default.PhotoCamera,
        iconTint = TokyoOnSurface,
        promptText = "",
        tags = listOf("фото", "молодость"),
        daysAhead = 365L,
        isComingSoon = true
    ),
    CapsuleIdeaTemplate(
        id = "voice_2026",
        titleRes = R.string.idea_voice_2026_title,
        descRes = R.string.idea_voice_2026_desc,
        icon = Icons.Default.Mic,
        iconTint = TokyoOnSurface,
        promptText = "",
        tags = listOf("голос", "аудио"),
        daysAhead = 365L,
        isComingSoon = true
    )
)

@Composable
fun IdeasScreen(
    onSelectTemplate: (title: String, prompt: String, tags: List<String>, daysAhead: Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TokyoBackground)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ideas_title),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    )
                )
                Spacer(modifier = Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = TokyoAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = stringResource(R.string.ideas_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnSurface)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(IDEAS_CATALOG, key = { it.id }) { template ->
                val title = stringResource(template.titleRes)
                val desc = stringResource(template.descRes)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !template.isComingSoon) {
                            onSelectTemplate(title, template.promptText, template.tags, template.daysAhead)
                        }
                        .border(
                            1.dp,
                            if (template.isComingSoon) TokyoOnSurface.copy(alpha = 0.15f) else template.iconTint.copy(alpha = 0.3f),
                            RoundedCornerShape(18.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = TokyoSurface
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(template.iconTint.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = template.icon,
                                contentDescription = null,
                                tint = template.iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (template.isComingSoon) TokyoOnSurface else TokyoOnBackground
                                    )
                                )

                                if (template.isComingSoon) {
                                    Box(
                                        modifier = Modifier
                                            .background(TokyoOnSurface.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.badge_soon),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = TokyoOnSurface,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TokyoOnSurface
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

