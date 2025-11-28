package com.aak.tilsynsapp

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(viewModel: VejmanViewModel) {
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
                    // Close the app completely
                    activity?.finishAffinity()
                }) {
                    Text("Luk app")
                }
            },
            title = { Text("Opdatering påkrævet") },
            text = { Text(versionMessage!!) }
        )
    }
    // Reset UI state if login state goes back to Input
    LaunchedEffect(loginState) {
        if (loginState is LoginState.Input) {
            isSending.value = false
            email = TextFieldValue("")
        }
    }

    when (val state = loginState) {
        is LoginState.Input -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Log ind med din e-mail", style = MaterialTheme.typography.titleLarge)
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

        is LoginState.Waiting -> {
            val pollingAttempts = remember { mutableIntStateOf(0) }
            val pollingMessage = remember { mutableStateOf("Venter på godkendelse...") }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(3000)
                    pollingAttempts.intValue++
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("E-mail sendt til:", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(state.email, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(pollingMessage.value, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.resetLogin() }) {
                        Text("Skift e-mail")
                    }
                }
            }
        }

        is LoginState.LoggedIn -> {
            // Nothing to show here. Main screen will take over.
        }
    }
}
