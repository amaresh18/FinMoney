package com.example

import com.example.data.bank.BankTxType
import com.example.service.statement.PdfTextExtractor
import com.example.service.statement.UniversalStatementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class StatementImportParserTest {

    private val phonePePdfText = """
        Transaction Statement for 9493833887
        01 Oct, 2026 - 31 Oct, 2026
        Date
        Transaction Details
        Type
        Amount
        Oct 08, 2026
        11:44 AM
        DEBIT
        ₹50,000
        Paid to VOORE ANAND KUMAR
        Transaction ID T2610081144007692819738
        UTR No. 566646548861
        Paid by
        XXXXXX0989
        Oct 06, 2026
        03:45 PM
        CREDIT
        ₹350
        Received from Sunil Paysafe
        Transaction ID T2610061544536894881874
        UTR No. 109266130455
        Credited to
        XXXXXX0989
        Oct 04, 2026
        12:21 PM
        DEBIT
        ₹1,010.80
        FASTag Recharge for TS08EV2288
        Transaction ID NB26100412205987435870042
        UTR No. 584391112014
        Paid by
        HDFC CREDIT
    """.trimIndent()

    private val gpayPdfText = """
        Transaction statement
        Transaction statement period: 01 September 2026 - 30 September 2026
        Sent: ₹30,000 | Received: ₹0

        01 Sep, 2026
        10:14 AM
        Paid to Sai Kishore
        UPI Transaction ID: 624445564610
        Paid by ICICI Bank 5501
        ₹15,000

        02 Sep, 2026
        10:41 AM
        Paid to Sai Kishore
        UPI Transaction ID: 128889777050
        Paid by HDFC Bank 0989
        ₹15,000
    """.trimIndent()

    @Test
    fun `PhonePe multi-line PDF text creates debit and credit entries`() {
        val result = UniversalStatementParser.parse(phonePePdfText, "PhonePe_Statement_Oct2026_Oct2026.pdf")

        assertTrue("Should detect PhonePe", result.detectedSystem.contains("PhonePe", ignoreCase = true))
        assertTrue("Expected >= 3 successful txs, got ${result.successfulTransactions.size}", result.successfulTransactions.size >= 3)

        val debits = result.successfulTransactions.filter { it.type == BankTxType.DEBIT }
        val credits = result.successfulTransactions.filter { it.type == BankTxType.CREDIT }
        assertTrue("Should have debits", debits.isNotEmpty())
        assertTrue("Should have credits", credits.isNotEmpty())

        val bigDebit = debits.first { it.amount == 50000.0 }
        assertTrue(bigDebit.merchantOrName.contains("VOORE", ignoreCase = true))
        assertEquals("566646548861", bigDebit.referenceNumber)

        val credit = credits.first { it.amount == 350.0 }
        assertTrue(credit.merchantOrName.contains("Sunil", ignoreCase = true))
    }

    @Test
    fun `GPay multi-line PDF text creates debit entries`() {
        val result = UniversalStatementParser.parse(gpayPdfText, "gpay_statement_20260901_20260930.pdf")

        assertTrue(result.detectedSystem.contains("Google Pay", ignoreCase = true) || result.detectedSystem.contains("GPay", ignoreCase = true))
        assertEquals(2, result.successfulTransactions.size)
        assertTrue(result.successfulTransactions.all { it.type == BankTxType.DEBIT })
        assertEquals(30000.0, result.totalDebitAmount, 0.01)
        assertTrue(result.successfulTransactions[0].merchantOrName.contains("Sai", ignoreCase = true))
    }

    @Test
    fun `PhonePe CSV sample still parses`() {
        val csv = """
            Date,Time,Transaction Details,Transaction ID,UTR,Transaction Type,Credit/debit instrument,Amount
            "Oct 08, 2026","11:44 AM","Paid to VOORE ANAND KUMAR","T2610081144007692819738","566646548861","DEBIT","Paid by XXXXXX0989","50000"
            "Oct 06, 2026","03:45 PM","Received from Sunil Paysafe","T2610061544536894881874","109266130455","CREDIT","Credited to XXXXXX0989","350"
        """.trimIndent()

        val result = UniversalStatementParser.parse(csv, "PhonePe_Statement.csv")
        assertEquals(2, result.successfulTransactions.size)
        assertEquals(1, result.successfulTransactions.count { it.type == BankTxType.DEBIT })
        assertEquals(1, result.successfulTransactions.count { it.type == BankTxType.CREDIT })
    }

    @Test
    fun `extracts readable text from real PhonePe PDF when present`() {
        val file = File("C:/Users/dell/Downloads/PhonePe_Statement_Oct2026_Oct2026.pdf")
        assumeTrue("PhonePe PDF not on disk", file.exists())

        val text = FileInputStream(file).use { PdfTextExtractor.extractText(it) }
        assertTrue("Extracted text should not be blank", text.isNotBlank())
        assertTrue(
            "Should contain Paid to / DEBIT / Oct after ToUnicode decode. Got head:\n${text.take(500)}",
            text.contains("Paid to", ignoreCase = true) ||
                text.contains("DEBIT", ignoreCase = true) ||
                text.contains("PhonePe", ignoreCase = true)
        )

        val parsed = UniversalStatementParser.parse(text, file.name)
        assertTrue(
            "Expected imported PhonePe txs from PDF, got ${parsed.successfulTransactions.size}. Detected=${parsed.detectedSystem}",
            parsed.successfulTransactions.size >= 5
        )
        assertTrue(parsed.successfulTransactions.any { it.type == BankTxType.DEBIT })
        assertTrue(parsed.successfulTransactions.any { it.type == BankTxType.CREDIT })
    }

    @Test
    fun `extracts readable text from real GPay PDF when present`() {
        val file = File("C:/Users/dell/Downloads/gpay_statement_20260901_20260930.pdf")
        assumeTrue("GPay PDF not on disk", file.exists())

        val text = FileInputStream(file).use { PdfTextExtractor.extractText(it) }
        assertTrue("Extracted text should not be blank", text.isNotBlank())
        assertTrue(
            "Should contain Paid to / UPI / Sep after ToUnicode decode. Got head:\n${text.take(800)}",
            text.contains("Paid to", ignoreCase = true) ||
                text.contains("UPI", ignoreCase = true) ||
                text.contains("Transaction", ignoreCase = true)
        )

        val parsed = UniversalStatementParser.parse(text, file.name)
        assertTrue(
            "Expected imported GPay txs from PDF, got ${parsed.successfulTransactions.size}. Text head:\n${text.take(800)}",
            parsed.successfulTransactions.isNotEmpty()
        )
        assertTrue(parsed.successfulTransactions.any { it.type == BankTxType.DEBIT })
    }
}
