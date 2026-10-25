package com.example.findme.ui.screen.pairing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.findme.ui.theme.AccentWork
import com.example.findme.ui.theme.DarkBackground
import com.example.findme.ui.theme.DarkCardBg
import com.example.findme.ui.theme.DarkCardBorder
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary

@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onPairingSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onPairingSuccess()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (uiState.selectedRole == null) {
                    // Seleccion de Rol
                    Text(
                        text = "Bienvenido a FindMe",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Selecciona tu rol para continuar",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onRoleSelected("MONITOR") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        border = BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = AccentWork.copy(alpha = 0.15f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = AccentWork, modifier = Modifier.padding(12.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Soy Monitor / Familiar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Ver el estado de un ser querido", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onRoleSelected("ADMIN") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        border = BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF0A500).copy(alpha = 0.15f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFFF0A500), modifier = Modifier.padding(12.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Soy Administrador", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Configuración del dispositivo", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }

                } else {
                    // Back button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = { viewModel.onRoleSelected(null) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Header Icon
                    Surface(
                        shape = CircleShape,
                        color = AccentWork.copy(alpha = 0.15f),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.selectedRole == "ADMIN") Icons.Default.Lock else Icons.Default.Key,
                                contentDescription = null,
                                tint = AccentWork,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Titles
                    Text(
                        text = if (uiState.selectedRole == "ADMIN") "Acceso Administrador" else "Vinculación Familiar",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (uiState.selectedRole == "ADMIN") "Ingresa la contraseña maestra" else "Ingresa tu nombre e introduce el código familiar",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (uiState.selectedRole == "ADMIN") {
                        OutlinedTextField(
                            value = uiState.adminPasswordInput,
                            onValueChange = { viewModel.onAdminPasswordChanged(it) },
                            label = { Text("Contraseña Maestra") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary)
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCardBg,
                                unfocusedContainerColor = DarkCardBg,
                                focusedBorderColor = AccentWork,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedLabelColor = AccentWork,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = AccentWork
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    viewModel.submitPairingCode()
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Mother Name Input Field
                        OutlinedTextField(
                            value = uiState.motherNameInput,
                            onValueChange = { viewModel.onMotherNameChanged(it) },
                            label = { Text("¿Cómo te llamas?") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCardBg,
                                unfocusedContainerColor = DarkCardBg,
                                focusedBorderColor = AccentWork,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedLabelColor = AccentWork,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = AccentWork
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Family Code Input Field
                        OutlinedTextField(
                            value = uiState.familyCodeInput,
                            onValueChange = { viewModel.onCodeChanged(it) },
                            label = { Text("Código Familiar") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCardBg,
                                unfocusedContainerColor = DarkCardBg,
                                focusedBorderColor = AccentWork,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedLabelColor = AccentWork,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = AccentWork
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    viewModel.submitPairingCode()
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Error Card
                    if (!uiState.errorMessage.isNullOrEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(14.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Submit Button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.submitPairingCode()
                        },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentWork),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = if (uiState.selectedRole == "ADMIN") "Ingresar" else "Vincular",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
