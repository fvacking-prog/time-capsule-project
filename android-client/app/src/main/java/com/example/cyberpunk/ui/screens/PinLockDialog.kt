package com.example.cyberpunk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cyberpunk.R
import com.example.cyberpunk.ui.theme.TokyoAccent
import com.example.cyberpunk.ui.theme.TokyoBackground
import com.example.cyberpunk.ui.theme.TokyoBorderGradient
import com.example.cyberpunk.ui.theme.TokyoError
import com.example.cyberpunk.ui.theme.TokyoOnBackground
import com.example.cyberpunk.ui.theme.TokyoOnSurface
import com.example.cyberpunk.ui.theme.TokyoPrimary
import com.example.cyberpunk.ui.theme.TokyoSecondary
import com.example.cyberpunk.ui.theme.TokyoSurface

@Composable
fun SetPinDialog(
    onDismiss: () -> Unit,
    onPinSet: (String) -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_set_pin_title),
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Придумайте 4-значный цифровой код для защиты ваших капсул",
                    style = MaterialTheme.typography.bodySmall.copy(color = TokyoOnSurface)
                )

                PinDotsRow(pinLength = enteredPin.length)

                PinNumpad(
                    enabled = true,
                    onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSet(newPin)
                            }
                        }
                    },
                    onBackspace = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                        }
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = TokyoOnSurface)
            }
        },
        containerColor = TokyoSurface
    )
}

@Composable
fun EnterPinDialog(
    errorText: String? = null,
    lockoutRemainingSeconds: Int = 0,
    showBiometricButton: Boolean = false,
    onBiometricClick: () -> Unit = {},
    onPinEntered: (String) -> Unit,
    onForgotPin: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var showForgotConfirm by remember { mutableStateOf(false) }
    val isLockedOut = lockoutRemainingSeconds > 0

    if (showForgotConfirm) {
        AlertDialog(
            onDismissRequest = { showForgotConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.btn_forgot_pin),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoError
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.forgot_pin_confirm),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TokyoOnSurface)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForgotConfirm = false
                        onForgotPin()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TokyoError,
                        contentColor = TokyoBackground
                    )
                ) {
                    Text(stringResource(R.string.btn_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel), color = TokyoOnSurface)
                }
            },
            containerColor = TokyoSurface
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TokyoBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TokyoBorderGradient, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = TokyoSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(TokyoPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.dialog_enter_pin_title),
                        tint = TokyoPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.dialog_enter_pin_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TokyoPrimary
                    ),
                    textAlign = TextAlign.Center
                )

                PinDotsRow(pinLength = enteredPin.length)

                if (isLockedOut) {
                    Text(
                        text = "Слишком много попыток. Повторите через $lockoutRemainingSeconds с.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TokyoError,
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center
                    )
                } else if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall.copy(color = TokyoError),
                        textAlign = TextAlign.Center
                    )
                }

                PinNumpad(
                    enabled = !isLockedOut,
                    onDigitClick = { digit ->
                        if (!isLockedOut && enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinEntered(newPin)
                                enteredPin = ""
                            }
                        }
                    },
                    onBackspace = {
                        if (!isLockedOut && enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                        }
                    }
                )

                if (showBiometricButton) {
                    OutlinedButton(
                        onClick = onBiometricClick,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Вход по отпечатку",
                            tint = TokyoAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Войти по отпечатку", color = TokyoAccent)
                    }
                }

                TextButton(
                    onClick = { showForgotConfirm = true }
                ) {
                    Text(
                        text = "${stringResource(R.string.btn_forgot_pin)} (${stringResource(R.string.btn_logout)})",
                        color = TokyoSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PinDotsRow(pinLength: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 4) {
            val filled = i < pinLength
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = if (filled) TokyoPrimary else TokyoBackground,
                        shape = CircleShape
                    )
                    .border(
                        1.dp,
                        if (filled) TokyoPrimary else TokyoOnSurface.copy(alpha = 0.4f),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PinNumpad(
    enabled: Boolean = true,
    onDigitClick: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val digits = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "<")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        digits.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { item ->
                    if (item.isEmpty()) {
                        Spacer(modifier = Modifier.size(56.dp))
                    } else if (item == "<") {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(TokyoBackground.copy(alpha = 0.5f), CircleShape)
                                .clickable(enabled = enabled) { onBackspace() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Удалить",
                                tint = if (enabled) TokyoOnSurface else TokyoOnSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(TokyoBackground, CircleShape)
                                .border(
                                    1.dp,
                                    if (enabled) TokyoPrimary.copy(alpha = 0.25f) else TokyoOnSurface.copy(alpha = 0.12f),
                                    CircleShape
                                )
                                .clickable(enabled = enabled) { onDigitClick(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabled) TokyoOnBackground else TokyoOnSurface.copy(alpha = 0.35f),
                                    fontSize = 20.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
