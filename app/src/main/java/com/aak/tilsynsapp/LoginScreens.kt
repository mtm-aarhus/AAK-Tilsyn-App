package com.aak.tilsynsapp

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(viewModel: TilsynViewModel) {
    val loginState by viewModel.loginState.collectAsState()
    val versionMessage by viewModel.versionMessage.collectAsState(initial = null)
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var email by remember { mutableStateOf(TextFieldValue("")) }
    val isSending = remember { mutableStateOf(false) }

    if (versionMessage != null) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClick = {
                    activity?.finishAffinity()
                }) {
                    Text("Luk app")
                }
            },
            title = { Text("Opdatering påkrævet") },
            text = { Text(versionMessage!!) }
        )
    }

    LaunchedEffect(loginState) {
        if (loginState is TilsynLoginState.Input) {
            isSending.value = false
            email = TextFieldValue("")
        }
    }

    when (val state = loginState) {
        is TilsynLoginState.Loading -> {
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Henter din profil...",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.graphicsLayer { this.alpha = alpha }
                    )
                    Text(
                        text = "Vent venligst et øjeblik",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        is TilsynLoginState.Input -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Log ind med din e-mail", 
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("E-mail") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSending.value
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                isSending.value = true
                                viewModel.sendLoginEmail(email.text)
                                Toast.makeText(context, "E-mail sendt, tjek din indbakke", Toast.LENGTH_LONG).show()
                            },
                            enabled = !isSending.value,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            if (isSending.value) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Send login-link")
                        }
                    }
                }
            }
        }

        is TilsynLoginState.Waiting -> {
            val pollingMessage = remember { mutableStateOf("Venter på godkendelse...") }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(3000)
                    pollingMessage.value = "Afventer godkendelse ..."
                    val token = viewModel.loadSavedToken()
                    if (token != null) {
                        viewModel.pollAuthToken(token) { success ->
                            if (success) {
                                pollingMessage.value = "Godkendt! Logger ind..."
                            }
                        }
                    } else {
                        pollingMessage.value = "Venter på token..."
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Tjek din indbakke",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Vi har sendt et login-link til:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                state.email,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(40.dp))
                            
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                pollingMessage.value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(40.dp))
                            
                            TextButton(
                                onClick = { viewModel.resetLogin() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Skift e-mail adresse")
                            }
                        }
                    }
                }
            }
        }

        is TilsynLoginState.LoggedIn -> {
            // Nothing to show here. Main screen will take over.
        }
    }
}
