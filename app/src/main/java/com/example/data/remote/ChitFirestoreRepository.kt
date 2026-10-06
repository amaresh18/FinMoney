package com.example.data.remote

import android.util.Log
import com.example.model.ChitAuction
import com.example.model.ChitGroup
import com.example.model.ChitMember
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChitFirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val chitsCollection = firestore.collection("chits")

    fun observeChits(): Flow<List<ChitGroup>> = callbackFlow {
        var listener: ListenerRegistration? = null
        try {
            listener = chitsCollection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ChitRepo", "Listen failed.", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val groups = snapshot.documents.mapNotNull { doc ->
                        try {
                            val id = doc.id
                            val name = doc.getString("name") ?: "Unnamed Chit"
                            val joinCode = doc.getString("joinCode") ?: ""
                            val monthlyInstallment = doc.getDouble("monthlyInstallment") ?: 0.0
                            val totalMonths = (doc.getLong("totalMonths") ?: 1L).toInt()
                            val currentMonth = (doc.getLong("currentMonth") ?: 1L).toInt()
                            val totalMembers = (doc.getLong("totalMembers") ?: 1L).toInt()
                            val managerUid = doc.getString("managerUid") ?: ""
                            val managerName = doc.getString("managerName") ?: "Manager"

                            val rawMembers = doc.get("members") as? List<Map<String, Any>> ?: emptyList()
                            val members = rawMembers.map { m ->
                                ChitMember(
                                    uid = m["uid"] as? String ?: "",
                                    name = m["name"] as? String ?: "",
                                    email = m["email"] as? String ?: "",
                                    monthsPaid = (m["monthsPaid"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
                                    hasLifted = m["hasLifted"] as? Boolean ?: false,
                                    liftedMonth = (m["liftedMonth"] as? Number)?.toInt(),
                                    liftedBidAmount = (m["liftedBidAmount"] as? Number)?.toDouble()
                                )
                            }

                            val rawAuctions = doc.get("auctions") as? List<Map<String, Any>> ?: emptyList()
                            val auctions = rawAuctions.map { a ->
                                ChitAuction(
                                    month = (a["month"] as? Number)?.toInt() ?: 1,
                                    winnerUid = a["winnerUid"] as? String ?: "",
                                    winnerName = a["winnerName"] as? String ?: "",
                                    bidAmount = (a["bidAmount"] as? Number)?.toDouble() ?: 0.0,
                                    dividendPerMember = (a["dividendPerMember"] as? Number)?.toDouble() ?: 0.0,
                                    netPayablePerMember = (a["netPayablePerMember"] as? Number)?.toDouble() ?: 0.0,
                                    dateTimestamp = (a["dateTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                )
                            }

                            ChitGroup(
                                id = id,
                                name = name,
                                joinCode = joinCode,
                                monthlyInstallment = monthlyInstallment,
                                totalMonths = totalMonths,
                                currentMonth = currentMonth,
                                totalMembers = totalMembers,
                                managerUid = managerUid,
                                managerName = managerName,
                                members = members,
                                auctions = auctions
                            )
                        } catch (e: Exception) {
                            Log.e("ChitRepo", "Error mapping chit doc ${doc.id}", e)
                            null
                        }
                    }
                    trySend(groups)
                }
            }
        } catch (e: Exception) {
            Log.e("ChitRepo", "Error observing chits", e)
            trySend(emptyList())
        }

        awaitClose {
            listener?.remove()
        }
    }

    suspend fun createChit(
        name: String,
        monthlyInstallment: Double,
        totalMonths: Int,
        totalMembers: Int,
        managerUid: String,
        managerName: String,
        managerEmail: String
    ): ChitGroup? {
        return try {
            val joinCode = "CHIT-" + UUID.randomUUID().toString().take(6).uppercase()
            val initialMember = ChitMember(
                uid = managerUid,
                name = managerName,
                email = managerEmail,
                monthsPaid = emptyList(),
                hasLifted = false
            )

            val docData = hashMapOf(
                "name" to name,
                "joinCode" to joinCode,
                "monthlyInstallment" to monthlyInstallment,
                "totalMonths" to totalMonths,
                "currentMonth" to 1,
                "totalMembers" to totalMembers,
                "managerUid" to managerUid,
                "managerName" to managerName,
                "members" to listOf(
                    hashMapOf(
                        "uid" to initialMember.uid,
                        "name" to initialMember.name,
                        "email" to initialMember.email,
                        "monthsPaid" to initialMember.monthsPaid,
                        "hasLifted" to initialMember.hasLifted
                    )
                ),
                "auctions" to emptyList<Map<String, Any>>()
            )

            val docRef = chitsCollection.document()
            docRef.set(docData).await()

            ChitGroup(
                id = docRef.id,
                name = name,
                joinCode = joinCode,
                monthlyInstallment = monthlyInstallment,
                totalMonths = totalMonths,
                currentMonth = 1,
                totalMembers = totalMembers,
                managerUid = managerUid,
                managerName = managerName,
                members = listOf(initialMember),
                auctions = emptyList()
            )
        } catch (e: Exception) {
            Log.e("ChitRepo", "Failed to create chit", e)
            null
        }
    }

    suspend fun joinChitByCode(
        joinCode: String,
        userUid: String,
        userName: String,
        userEmail: String
    ): Boolean {
        return try {
            val snapshot = chitsCollection.whereEqualTo("joinCode", joinCode.trim().uppercase()).get().await()
            if (snapshot.isEmpty) return false
            val doc = snapshot.documents.first()
            val chitId = doc.id
            val rawMembers = doc.get("members") as? List<Map<String, Any>> ?: emptyList()

            if (rawMembers.any { it["uid"] == userUid }) {
                return true // already member
            }

            val totalMembers = (doc.getLong("totalMembers") ?: 10L).toInt()
            if (rawMembers.size >= totalMembers) {
                return false // full
            }

            val newMemberMap = hashMapOf(
                "uid" to userUid,
                "name" to userName,
                "email" to userEmail,
                "monthsPaid" to emptyList<Int>(),
                "hasLifted" to false
            )

            val updatedMembers = rawMembers + newMemberMap
            chitsCollection.document(chitId).update("members", updatedMembers).await()
            true
        } catch (e: Exception) {
            Log.e("ChitRepo", "Failed to join chit", e)
            false
        }
    }

    suspend fun conductMonthlyAuction(
        chitId: String,
        month: Int,
        winnerUid: String,
        winnerName: String,
        bidDiscountAmount: Double,
        chitGroup: ChitGroup
    ): Boolean {
        return try {
            val totalPool = chitGroup.monthlyInstallment * chitGroup.totalMembers
            val dividendPerMember = if (chitGroup.totalMembers > 0) bidDiscountAmount / chitGroup.totalMembers else 0.0
            val netPayablePerMember = chitGroup.monthlyInstallment - dividendPerMember

            val auction = ChitAuction(
                month = month,
                winnerUid = winnerUid,
                winnerName = winnerName,
                bidAmount = bidDiscountAmount,
                dividendPerMember = dividendPerMember,
                netPayablePerMember = netPayablePerMember,
                dateTimestamp = System.currentTimeMillis()
            )

            val auctionMap = hashMapOf(
                "month" to auction.month,
                "winnerUid" to auction.winnerUid,
                "winnerName" to auction.winnerName,
                "bidAmount" to auction.bidAmount,
                "dividendPerMember" to auction.dividendPerMember,
                "netPayablePerMember" to auction.netPayablePerMember,
                "dateTimestamp" to auction.dateTimestamp
            )

            val updatedMembers = chitGroup.members.map { m ->
                if (m.uid == winnerUid) {
                    hashMapOf(
                        "uid" to m.uid,
                        "name" to m.name,
                        "email" to m.email,
                        "monthsPaid" to m.monthsPaid,
                        "hasLifted" to true,
                        "liftedMonth" to month,
                        "liftedBidAmount" to bidDiscountAmount
                    )
                } else {
                    hashMapOf(
                        "uid" to m.uid,
                        "name" to m.name,
                        "email" to m.email,
                        "monthsPaid" to m.monthsPaid,
                        "hasLifted" to m.hasLifted,
                        "liftedMonth" to m.liftedMonth,
                        "liftedBidAmount" to m.liftedBidAmount
                    )
                }
            }

            val nextMonth = if (month < chitGroup.totalMonths) month + 1 else month

            val doc = chitsCollection.document(chitId).get().await()
            val existingAuctions = doc.get("auctions") as? List<Map<String, Any>> ?: emptyList()
            val newAuctions = existingAuctions + auctionMap

            chitsCollection.document(chitId).update(
                mapOf(
                    "auctions" to newAuctions,
                    "members" to updatedMembers,
                    "currentMonth" to nextMonth
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e("ChitRepo", "Failed to conduct auction", e)
            false
        }
    }

    suspend fun toggleMemberPayment(
        chitId: String,
        memberUid: String,
        month: Int,
        chitGroup: ChitGroup
    ): Boolean {
        return try {
            val updatedMembers = chitGroup.members.map { m ->
                if (m.uid == memberUid) {
                    val updatedMonths = if (m.monthsPaid.contains(month)) {
                        m.monthsPaid - month
                    } else {
                        m.monthsPaid + month
                    }
                    hashMapOf(
                        "uid" to m.uid,
                        "name" to m.name,
                        "email" to m.email,
                        "monthsPaid" to updatedMonths,
                        "hasLifted" to m.hasLifted,
                        "liftedMonth" to m.liftedMonth,
                        "liftedBidAmount" to m.liftedBidAmount
                    )
                } else {
                    hashMapOf(
                        "uid" to m.uid,
                        "name" to m.name,
                        "email" to m.email,
                        "monthsPaid" to m.monthsPaid,
                        "hasLifted" to m.hasLifted,
                        "liftedMonth" to m.liftedMonth,
                        "liftedBidAmount" to m.liftedBidAmount
                    )
                }
            }

            chitsCollection.document(chitId).update("members", updatedMembers).await()
            true
        } catch (e: Exception) {
            Log.e("ChitRepo", "Failed to toggle payment", e)
            false
        }
    }
}
