package com.smartspend.app.ui.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.PhoneAuthCredential
import com.smartspend.app.RetrofitClient
import com.smartspend.app.PhoneLoginPayload
import com.smartspend.app.ui.theme.SmartSpendTheme
import com.smartspend.app.ui.util.findComponentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://hhaarrss.github.io/smart-spend/privacy-policy.html"
private const val PREFS_NAME = "smart_spend_prefs"
private const val INDIA_DIAL_CODE = "+91"
private const val RESEND_COOLDOWN_SECONDS = 30

private sealed interface AuthStep {
    data object Welcome : AuthStep
    data object PhoneEntry : AuthStep
    data class OtpEntry(val phoneNumber: String, val verificationId: String) : AuthStep
    data object ExchangingToken : AuthStep
}

/**
 * Welcome -> phone number -> OTP -> signed in. The only way into the app for a real user;
 * DEV_SKIP_AUTH builds never reach this (see MainActivity).
 */
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phoneController = remember { PhoneAuthController() }

    var step by remember { mutableStateOf<AuthStep>(AuthStep.Welcome) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun completeSignIn(idToken: String) {
        step = AuthStep.ExchangingToken
        scope.launch {
            try {
                val response = RetrofitClient.apiService.phoneLogin(PhoneLoginPayload(id_token = idToken))
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("jwt_token", body.access_token)
                        .apply()
                    onAuthenticated()
                } else if (response.code() == 503) {
                    errorMessage = "Sign-in isn't set up on the server yet. Try again shortly."
                    step = AuthStep.Welcome
                } else {
                    errorMessage = "Couldn't complete sign-in (${response.code()}). Try again."
                    step = AuthStep.Welcome
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Couldn't reach SmartSpend. Check your connection."
                step = AuthStep.Welcome
            }
        }
    }

    fun onAutoVerified(credential: PhoneAuthCredential) {
        scope.launch {
            phoneController.signInWithCredential(credential)
                .onSuccess { idToken -> completeSignIn(idToken) }
                .onFailure { e ->
                    errorMessage = e.localizedMessage ?: "Verification failed. Try again."
                    step = AuthStep.PhoneEntry
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = step) {
            AuthStep.Welcome -> WelcomeContent(
                errorMessage = errorMessage,
                onContinue = {
                    errorMessage = null
                    step = AuthStep.PhoneEntry
                }
            )

            AuthStep.PhoneEntry -> PhoneEntryContent(
                errorMessage = errorMessage,
                onBack = { step = AuthStep.Welcome },
                onSendCode = { phoneNumber ->
                    errorMessage = null
                    val activity = context.findComponentActivity()
                    if (activity == null) {
                        errorMessage = "Couldn't start verification. Try again."
                        return@PhoneEntryContent
                    }
                    scope.launch {
                        phoneController.verifyPhoneNumber(activity, phoneNumber).collect { event ->
                            when (event) {
                                is PhoneVerificationEvent.AutoVerified -> onAutoVerified(event.credential)
                                is PhoneVerificationEvent.CodeSent ->
                                    step = AuthStep.OtpEntry(phoneNumber, event.verificationId)
                                is PhoneVerificationEvent.Failed -> errorMessage = event.message
                            }
                        }
                    }
                }
            )

            is AuthStep.OtpEntry -> OtpEntryContent(
                phoneNumber = current.phoneNumber,
                errorMessage = errorMessage,
                onBack = { step = AuthStep.PhoneEntry },
                onVerify = { code ->
                    errorMessage = null
                    scope.launch {
                        phoneController.signInWithCode(current.verificationId, code)
                            .onSuccess { idToken -> completeSignIn(idToken) }
                            .onFailure { e -> errorMessage = e.localizedMessage ?: "Verification failed." }
                    }
                },
                onResend = {
                    errorMessage = null
                    val activity = context.findComponentActivity() ?: return@OtpEntryContent
                    scope.launch {
                        phoneController.verifyPhoneNumber(activity, current.phoneNumber).collect { event ->
                            when (event) {
                                is PhoneVerificationEvent.AutoVerified -> onAutoVerified(event.credential)
                                is PhoneVerificationEvent.CodeSent ->
                                    step = AuthStep.OtpEntry(current.phoneNumber, event.verificationId)
                                is PhoneVerificationEvent.Failed -> errorMessage = event.message
                            }
                        }
                    }
                }
            )

            AuthStep.ExchangingToken -> ExchangingTokenContent()
        }
    }
}

@Composable
private fun WelcomeContent(errorMessage: String?, onContinue: () -> Unit) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val logoScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "logo-scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "content-alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SmartSpendTheme.colors.heroSurface, MaterialTheme.colorScheme.background)
                )
            )
            .padding(28.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size((88 * logoScale).dp)
                    .background(SmartSpendTheme.colors.onHeroSurface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "₹",
                    style = MaterialTheme.typography.displaySmall,
                    color = SmartSpendTheme.colors.heroSurface,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Spending, sorted.",
                style = MaterialTheme.typography.displaySmall,
                color = SmartSpendTheme.colors.onHeroSurface,
                modifier = Modifier.alpha(contentAlpha)
            )
            Text(
                "Your bank SMS, turned into a budget you actually understand.",
                style = MaterialTheme.typography.bodyLarge,
                color = SmartSpendTheme.colors.onHeroSurface.copy(alpha = 0.85f),
                modifier = Modifier.alpha(contentAlpha)
            )

            Spacer(Modifier.height(8.dp))

            if (errorMessage != null) {
                Text(
                    errorMessage,
                    color = SmartSpendTheme.colors.negativeContainer,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
            }

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SmartSpendTheme.colors.onHeroSurface,
                    contentColor = SmartSpendTheme.colors.heroSurface
                )
            ) {
                Text("Continue with phone number", fontWeight = FontWeight.Black)
            }

            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                },
                modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
            ) {
                Text("Privacy Policy", color = SmartSpendTheme.colors.onHeroSurface.copy(alpha = 0.8f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneEntryContent(
    errorMessage: String?,
    onBack: () -> Unit,
    onSendCode: (phoneNumberE164: String) -> Unit
) {
    var digits by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val isValid = digits.length == 10

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        IconTextBack(onBack)
        Spacer(Modifier.height(24.dp))
        Text(
            "What's your number?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "We'll text you a one-time code. Standard rates may apply.",
            style = MaterialTheme.typography.bodyMedium,
            color = SmartSpendTheme.colors.inkMuted
        )
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = SmartSpendTheme.colors.subtleSurface)
            ) {
                Text(
                    INDIA_DIAL_CODE,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = digits,
                onValueChange = { input ->
                    digits = input.filter { it.isDigit() }.take(10)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("98765 43210") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = MaterialTheme.shapes.medium
            )
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(errorMessage, color = SmartSpendTheme.colors.negative, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                sending = true
                onSendCode("$INDIA_DIAL_CODE$digits")
            },
            enabled = isValid && !sending,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            if (sending) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Send code", fontWeight = FontWeight.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtpEntryContent(
    phoneNumber: String,
    errorMessage: String?,
    onBack: () -> Unit,
    onVerify: (code: String) -> Unit,
    onResend: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(RESEND_COOLDOWN_SECONDS) }

    LaunchedEffect(phoneNumber) {
        cooldown = RESEND_COOLDOWN_SECONDS
        while (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    // Reset the "verifying" spinner if a fresh error arrives for this attempt.
    DisposableEffect(errorMessage) {
        if (errorMessage != null) verifying = false
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        IconTextBack(onBack)
        Spacer(Modifier.height(24.dp))
        Text(
            "Enter the code",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sent to $phoneNumber",
            style = MaterialTheme.typography.bodyMedium,
            color = SmartSpendTheme.colors.inkMuted
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { input -> code = input.filter { it.isDigit() }.take(6) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("123456") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = MaterialTheme.shapes.medium
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(errorMessage, color = SmartSpendTheme.colors.negative, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                verifying = true
                onVerify(code)
            },
            enabled = code.length == 6 && !verifying,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            if (verifying) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Verify", fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = {
                onResend()
                cooldown = RESEND_COOLDOWN_SECONDS
            },
            enabled = cooldown == 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (cooldown > 0) "Resend code in ${cooldown}s" else "Resend code")
        }
    }
}

@Composable
private fun ExchangingTokenContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Signing you in...", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun IconTextBack(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}
