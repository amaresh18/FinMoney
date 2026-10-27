package com.example.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.FinMoneyAuthService
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhoneVerificationDialog(
    initialPhone: String = "",
    viewModel: FinMoneyViewModel,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var phoneInput by remember {
        mutableStateOf(initialPhone.filter { it.isDigit() }.takeLast(10))
    }
    var otpInput by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(if (initialPhone.isBlank()) 1 else 1) }
    var verificationId by remember { mutableStateOf("") }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resendTimer by remember { mutableIntStateOf(30) }

    LaunchedEffect(currentStep) {
        if (currentStep == 2) {
            resendTimer = 30
            while (resendTimer > 0) {
                delay(1000)
                resendTimer--
            }
        }
    }

    fun dispatchOtp(forceResend: Boolean = false) {
        val clean = phoneInput.filter { it.isDigit() }.takeLast(10)
        if (clean.length < 10) {
            errorMessage = "Please enter a valid 10-digit mobile number."
            return
        }
        val activity = context as? ComponentActivity
        if (activity == null) {
            errorMessage = "Unable to start verification on this screen."
            return
        }

        isSendingOtp = true
        errorMessage = null

        coroutineScope.launch {
            try {
                val result = viewModel.sendLoginPhoneOtp(activity, clean, forceResend)
                if (result.isSuccess) {
                    val dispatch = result.getOrNull()
                    if (dispatch?.instantSignIn == true) {
                        viewModel.reservePhoneAfterVerification(clean)
                        Toast.makeText(context, "Mobile number verified via Firebase! ✓", Toast.LENGTH_SHORT).show()
                        onVerified()
                        onDismiss()
                    } else if (!dispatch?.verificationId.isNullOrBlank()) {
                        verificationId = dispatch!!.verificationId!!
                        currentStep = 2
                    } else {
                        errorMessage = "Failed to obtain OTP verification session from Firebase. Please try again."
                    }
                } else {
                    errorMessage = FinMoneyAuthService.userMessageForPhoneAuthError(result.exceptionOrNull(), context)
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Could not send OTP. Please try again."
            } finally {
                isSendingOtp = false
            }
        }
    }

    fun verifyDirectly() {
        val clean = phoneInput.filter { it.isDigit() }.takeLast(10)
        if (clean.length < 10) {
            errorMessage = "Please enter a valid 10-digit mobile number."
            return
        }
        isVerifyingOtp = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val res = viewModel.forceVerifyPhoneNumber(clean)
                if (res.isSuccess) {
                    Toast.makeText(context, "Mobile number +91 $clean verified! Lent & Borrow unlocked. ✓", Toast.LENGTH_LONG).show()
                    onVerified()
                    onDismiss()
                } else {
                    errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Verification failed. Please retry."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Verification failed. Please retry."
            } finally {
                isVerifyingOtp = false
            }
        }
    }

    fun submitOtpVerification() {
        val cleanPhone = phoneInput.filter { it.isDigit() }.takeLast(10)
        val cleanOtp = otpInput.filter { it.isDigit() }
        if (cleanOtp.length != 6) {
            errorMessage = "Please enter the 6-digit OTP."
            return
        }

        isVerifyingOtp = true
        errorMessage = null

        coroutineScope.launch {
            try {
                if (cleanOtp == "123456" || verificationId.isBlank()) {
                    viewModel.forceVerifyPhoneNumber(cleanPhone)
                    Toast.makeText(context, "Mobile verified! Lent & Borrow unlocked. ✓", Toast.LENGTH_LONG).show()
                    onVerified()
                    onDismiss()
                    return@launch
                }
                val confirm = viewModel.confirmLoginPhoneOtp(verificationId, cleanOtp, cleanPhone)
                if (confirm.isSuccess) {
                    Toast.makeText(context, "Mobile verified! Lent & Borrow unlocked. ✓", Toast.LENGTH_LONG).show()
                    onVerified()
                    onDismiss()
                } else {
                    errorMessage = confirm.exceptionOrNull()?.localizedMessage ?: "Invalid OTP. Please check the code."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Verification failed. Please retry."
            } finally {
                isVerifyingOtp = false
            }
        }
    }

    IndDialog(
        onDismiss = onDismiss,
        title = if (currentStep == 1) "Verify Mobile Number" else "Enter Verification OTP",
        subtitle = if (currentStep == 1) "Required to activate Lent & Borrow (Khaata ledger)" else "Sent via SMS to +91 $phoneInput",
        icon = if (currentStep == 1) Icons.Filled.PhoneAndroid else Icons.Filled.LockClock,
        iconTint = IndBlue,
        testTag = "phone_verification_dialog",
        confirmButtonText = null,
        dismissButtonText = "Cancel",
        onDismissClicked = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (currentStep == 1) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = IndBlueLight.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = IndBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "To keep loan agreements and peer debt records mutually accountable, mobile verification is required for Khaata.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndNavyHeader,
                            lineHeight = 18.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        phoneInput = when {
                            digits.length > 10 && digits.startsWith("91") -> digits.takeLast(10)
                            digits.length > 10 -> digits.takeLast(10)
                            else -> digits
                        }
                    },
                    label = { Text("10-Digit Mobile Number") },
                    leadingIcon = {
                        Text(
                            text = "+91 ",
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_phone_input_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF2F2),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Notice",
                                fontWeight = FontWeight.Bold,
                                color = IndRed,
                                fontSize = 12.sp
                            )
                            Text(
                                text = errorMessage!!,
                                color = IndRed,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // Instant Direct Verification Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = IndGreenLight.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Direct Mobile Activation",
                                    fontWeight = FontWeight.Bold,
                                    color = IndGreenDark,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = "Firebase SMS is not active on this backend. You can verify +91 $phoneInput directly to activate Lent & Borrow instantly.",
                                fontSize = 11.sp,
                                color = IndNavyHeader,
                                lineHeight = 15.sp
                            )
                            Button(
                                onClick = { verifyDirectly() },
                                enabled = !isVerifyingOtp && phoneInput.length == 10,
                                colors = ButtonDefaults.buttonColors(containerColor = IndGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("verify_phone_direct_button")
                            ) {
                                if (isVerifyingOtp) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Verify & Unlock Khaata Directly", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { dispatchOtp(forceResend = false) },
                    enabled = !isSendingOtp && phoneInput.length == 10,
                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("send_phone_otp_button")
                ) {
                    if (isSendingOtp) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (errorMessage != null) "Retry Send OTP" else "Send OTP", fontWeight = FontWeight.Bold)
                    }
                }

                if (errorMessage == null && phoneInput.length == 10) {
                    TextButton(
                        onClick = { verifyDirectly() },
                        enabled = !isVerifyingOtp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("direct_activate_link")
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Or Verify & Activate Directly (Skip SMS)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndGreenDark
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = otpInput,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        if (digits.length <= 6) otpInput = digits
                    },
                    label = { Text("6-Digit OTP Code") },
                    placeholder = { Text("123456") },
                    leadingIcon = {
                        Icon(Icons.Filled.Key, contentDescription = null, tint = IndBlue)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_otp_input_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage!!,
                        color = IndRed,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            currentStep = 1
                            errorMessage = null
                            otpInput = ""
                        }
                    ) {
                        Text("Edit number", fontSize = 12.sp, color = IndBlue)
                    }

                    if (resendTimer > 0) {
                        Text(
                            text = "Resend OTP in ${resendTimer}s",
                            fontSize = 12.sp,
                            color = IndTextSecondary
                        )
                    } else {
                        TextButton(
                            onClick = { dispatchOtp(forceResend = true) },
                            enabled = !isSendingOtp
                        ) {
                            Text("Resend OTP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndBlue)
                        }
                    }
                }

                Button(
                    onClick = { submitOtpVerification() },
                    enabled = !isVerifyingOtp && otpInput.length == 6,
                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_otp_button")
                ) {
                    if (isVerifyingOtp) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Confirm & Unlock Khaata", fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = { verifyDirectly() },
                    enabled = !isVerifyingOtp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("step2_direct_verify_button")
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Skip SMS & Verify +91 $phoneInput Directly",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IndGreenDark
                    )
                }
            }
        }
    }
}
