package com.example

import com.example.identity.UserIdentityConflictException
import com.example.identity.UserIdentityNormalizer
import com.example.service.FinMoneyAuthService
import org.junit.Assert.*
import org.junit.Test

class AuthAndBiometricTest {

    @Test
    fun testUserIdentityNormalizer_validPhone() {
        assertEquals("9493833887", UserIdentityNormalizer.phone10("+919493833887"))
        assertEquals("9493833887", UserIdentityNormalizer.phone10("919493833887"))
        assertEquals("9493833887", UserIdentityNormalizer.phone10("09493833887"))
        assertEquals("9493833887", UserIdentityNormalizer.phone10("94938 33887"))
        assertTrue(UserIdentityNormalizer.isValidPhone10("9493833887"))
        assertFalse(UserIdentityNormalizer.isValidPhone10("12345"))
    }

    @Test
    fun testUserIdentityNormalizer_validEmail() {
        assertEquals("g.amaresh18@gmail.com", UserIdentityNormalizer.email(" G.Amaresh18@Gmail.com "))
        assertTrue(UserIdentityNormalizer.isValidEmail("g.amaresh18@gmail.com"))
        assertFalse(UserIdentityNormalizer.isValidEmail("invalid-email"))
    }

    @Test
    fun testUserIdentityConflictException_messages() {
        val phoneBoundEx = UserIdentityConflictException.phoneBoundToEmail("existing@gmail.com")
        assertTrue(phoneBoundEx.message!!.contains("existing@gmail.com"))

        val emailBoundEx = UserIdentityConflictException.emailBoundToPhone("9493833887")
        assertTrue(emailBoundEx.message!!.contains("3887"))
    }

    @Test
    fun testPhoneAuthErrorMessage_operationNotAllowed() {
        val fakeEx = RuntimeException("OPERATION_NOT_ALLOWED: Phone OTP is disabled")
        val message = FinMoneyAuthService.userMessageForPhoneAuthError(fakeEx)
        assertTrue(message.contains("SHA-1") || message.contains("Google Sign-In") || message.contains("123456"))
    }

    @Test
    fun testPhoneAuthErrorMessage_generic() {
        val fakeEx = RuntimeException("Network unreachable")
        val message = FinMoneyAuthService.userMessageForPhoneAuthError(fakeEx)
        assertTrue(message.contains("Network unreachable") || message.contains("Could not send OTP"))
    }
}
