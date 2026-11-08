package com.example.data.remote

import retrofit2.Response
import retrofit2.http.*

data class PayNearbyBillFetchRequest(
    val billerId: String,
    val consumerNumber: String,
    val mobileNumber: String,
    val agentId: String
)

data class PayNearbyBillData(
    val billerName: String,
    val consumerName: String,
    val billAmount: Double,
    val billDate: String,
    val dueDate: String,
    val referenceId: String,
    val billNumber: String? = null
)

data class PayNearbyBillFetchResponse(
    val status: String, // "SUCCESS", "PENDING", "FAILED"
    val responseCode: String = "200",
    val message: String = "",
    val data: PayNearbyBillData? = null
)

data class PayNearbyBillPaymentRequest(
    val billerId: String,
    val consumerNumber: String,
    val amount: Double,
    val referenceId: String,
    val agentId: String,
    val paymentMode: String = "UPI"
)

data class PayNearbyBillPaymentResponse(
    val status: String, // "SUCCESS", "FAILED", "PENDING"
    val responseCode: String = "200",
    val message: String = "",
    val transactionId: String? = null,
    val utrNumber: String? = null,
    val commissionAmount: Double? = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

data class PayNearbyRechargeRequest(
    val mobileNumber: String,
    val operatorCode: String,
    val amount: Double,
    val circleCode: String = "1",
    val agentId: String
)

data class PayNearbyRechargeResponse(
    val status: String,
    val message: String = "",
    val transactionId: String? = null,
    val operatorRef: String? = null,
    val commissionAmount: Double? = 0.0
)

data class PayNearbyOperator(
    val operatorCode: String,
    val operatorName: String,
    val category: String, // "ELECTRICITY", "WATER", "GAS", "MOBILE", "DTH", "CREDIT_CARD", "INSURANCE", "LOAN", "FASTAG"
    val bbpsSupported: Boolean = true
)

data class PayNearbyOperatorsResponse(
    val status: String = "SUCCESS",
    val message: String = "",
    val operators: List<PayNearbyOperator> = emptyList()
)

data class PayNearbyWalletResponse(
    val status: String = "SUCCESS",
    val agentId: String = "",
    val availableBalance: Double = 0.0,
    val currency: String = "INR"
)

interface PayNearbyApiService {
    @POST("v1/bbps/bill/fetch")
    suspend fun fetchBillDetails(
        @Header("x-api-key") apiKey: String,
        @Header("x-agent-id") agentId: String,
        @Body request: PayNearbyBillFetchRequest
    ): Response<PayNearbyBillFetchResponse>

    @POST("v1/bbps/bill/pay")
    suspend fun payBill(
        @Header("x-api-key") apiKey: String,
        @Header("x-agent-id") agentId: String,
        @Body request: PayNearbyBillPaymentRequest
    ): Response<PayNearbyBillPaymentResponse>

    @POST("v1/recharge/pay")
    suspend fun processRecharge(
        @Header("x-api-key") apiKey: String,
        @Header("x-agent-id") agentId: String,
        @Body request: PayNearbyRechargeRequest
    ): Response<PayNearbyRechargeResponse>

    @GET("v1/utility/operators")
    suspend fun getOperators(
        @Header("x-api-key") apiKey: String,
        @Query("category") category: String? = null
    ): Response<PayNearbyOperatorsResponse>

    @GET("v1/partner/wallet/balance")
    suspend fun getWalletBalance(
        @Header("x-api-key") apiKey: String,
        @Header("x-agent-id") agentId: String
    ): Response<PayNearbyWalletResponse>
}
