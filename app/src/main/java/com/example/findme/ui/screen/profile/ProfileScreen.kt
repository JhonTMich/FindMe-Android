package com.example.findme.ui.screen.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.data.local.UserSession
import com.example.findme.ui.components.AdminQuickActionsCard
import com.example.findme.ui.theme.AccentFree
import com.example.findme.ui.theme.AccentUniversity
import com.example.findme.ui.theme.AccentWork
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.DarkInnerBg
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary

@Composable
fun ProfileScreen(
    userSession: UserSession?,
    isAdminRole: Boolean,
    onAuthenticateAdmin: (String) -> Boolean,
    onToggleAdminMode: (Boolean) -> Unit,
    onSetOnTheWay: () -> Unit,
    onSetOnTheStreet: () -> Unit,
    onSetArrivedHome: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "profilePulse")
    val alphaPulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaPulse"
    )

    var showAdminDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // User Profile Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = AccentWork.copy(alpha = 0.15f),
                    modifier = Modifier.size(84.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = AccentWork,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App user name (motherName)
                Text(
                    text = userSession?.motherName ?: "Mamá",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isAdminRole) "Modo Administrador Activo 🔓" else "Titular de la cuenta",
                    fontSize = 13.sp,
                    color = if (isAdminRole) AccentWork else TextSecondary,
                    fontWeight = if (isAdminRole) FontWeight.Bold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Family Code Chip
                Surface(
                    shape = RoundedCornerShape(50),
                    color = DarkInnerBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = AccentWork,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Código: ${userSession?.familyCode ?: "JH-7842"}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentWork
                        )
                    }
                }
            }
        }

        // Connection Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = DarkInnerBg
                ) {
                    Box(
                        modifier = Modifier.padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .alpha(alphaPulse)
                                .background(AccentFree, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Sincronizado con ${userSession?.displayName ?: "Usuario"}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentFree
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Conexión en tiempo real",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Quick Actions Card (Visible if Admin)
        if (isAdminRole) {
            AdminQuickActionsCard(
                onSetOnTheWay = onSetOnTheWay,
                onSetOnTheStreet = onSetOnTheStreet,
                onSetArrivedHome = onSetArrivedHome
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Logout / Unlink Button
        Button(
            onClick = onLogout,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentUniversity),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Desvincular",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Discrete "Modo Administrador" at the end
        Row(
            modifier = Modifier
                .clickable {
                    passwordInput = ""
                    passwordError = false
                    showAdminDialog = true
                }
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isAdminRole) Icons.Default.AdminPanelSettings else Icons.Default.Lock,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isAdminRole) "Modo Administrador (Activo)" else "Modo Administrador",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }
    }

    // Admin Access Dialog
    if (showAdminDialog) {
        if (!isAdminRole) {
            AlertDialog(
                onDismissRequest = { showAdminDialog = false },
                containerColor = DarkCardBg,
                title = {
                    Text(
                        text = "Modo Administrador",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Ingresa la contraseña maestra para desbloquear las acciones rápidas.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                passwordError = false
                            },
                            label = { Text("Contraseña Maestra") },
                            isError = passwordError,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = AccentWork,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedLabelColor = AccentWork,
                                unfocusedLabelColor = TextSecondary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (passwordError) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Contraseña incorrecta",
                                fontSize = 12.sp,
                                color = AccentUniversity
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val success = onAuthenticateAdmin(passwordInput)
                            if (success) {
                                showAdminDialog = false
                            } else {
                                passwordError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentWork)
                    ) {
                        Text("Activar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAdminDialog = false }) {
                        Text("Cancelar", color = TextSecondary)
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showAdminDialog = false },
                containerColor = DarkCardBg,
                title = {
                    Text(
                        text = "Modo Administrador",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "El modo administrador está actualmente activo. ¿Deseas volver al modo monitor?",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onToggleAdminMode(false)
                            showAdminDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentUniversity)
                    ) {
                        Text("Volver a Modo Monitor")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAdminDialog = false }) {
                        Text("Cancelar", color = TextSecondary)
                    }
                }
            )
        }
    }
}
