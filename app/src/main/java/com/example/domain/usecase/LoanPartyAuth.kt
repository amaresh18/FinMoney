package com.example.domain.usecase

object LoanPartyAuth {
    fun phone10(raw: String): String = raw.filter { it.isDigit() }.takeLast(10)

    fun phonesMatch(a: String, b: String): Boolean {
        val pa = phone10(a)
        val pb = phone10(b)
        return pa.length == 10 && pb.length == 10 && pa == pb
    }

    fun isCreator(creatorPhone: String, userPhone: String, creatorUserId: String, activeUserId: String): Boolean {
        if (creatorUserId.isNotBlank() && activeUserId.isNotBlank() && creatorUserId == activeUserId) return true
        return phonesMatch(creatorPhone, userPhone)
    }

    fun isCounterparty(counterpartyPhone: String, userPhone: String): Boolean {
        return phonesMatch(counterpartyPhone, userPhone)
    }
}
