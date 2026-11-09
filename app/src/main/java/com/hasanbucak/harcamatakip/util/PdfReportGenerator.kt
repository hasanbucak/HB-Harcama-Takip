package com.hasanbucak.harcamatakip.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.hasanbucak.harcamatakip.data.local.CardEntity
import com.hasanbucak.harcamatakip.data.local.CardGroupEntity
import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransactionType
import com.hasanbucak.harcamatakip.ui.BankBreakdownItem
import com.hasanbucak.harcamatakip.ui.CategoryBreakdownItem
import com.hasanbucak.harcamatakip.ui.CurrencySummary
import com.hasanbucak.harcamatakip.ui.Formatters
import java.io.File
import java.io.FileOutputStream

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val CONTENT_BOTTOM = PAGE_HEIGHT - 44f

    fun generateReportFile(
        outFile: File,
        reportTitle: String,
        scopeSubtitle: String,
        currencySummaries: List<CurrencySummary>,
        bankBreakdown: List<BankBreakdownItem>,
        categoryBreakdown: List<CategoryBreakdownItem>,
        transactions: List<TransactionEntity>,
        cardsByKey: Map<String, CardEntity>,
        groupsById: Map<String, CardGroupEntity>
    ): File {
        val doc = PdfDocument()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 8.5f
        }
        val refundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(21, 128, 61)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.8f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 8f
        }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = MARGIN

        fun drawFooter(c: Canvas, pNum: Int) {
            c.drawLine(MARGIN, PAGE_HEIGHT - 32f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 32f, linePaint)
            c.drawText(
                "Cihaz üzerinde çevrimdışı üretildi • Sayfa $pNum",
                MARGIN,
                PAGE_HEIGHT - 18f,
                footerPaint
            )
        }

        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > CONTENT_BOTTOM) {
                drawFooter(canvas, pageNumber)
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
            }
        }

        // Report Header
        canvas.drawText(reportTitle, MARGIN, y + 14f, titlePaint)
        y += 28f
        canvas.drawText(scopeSubtitle, MARGIN, y, subtitlePaint)
        y += 14f
        canvas.drawText("Oluşturulma: ${Formatters.formatDateTime(System.currentTimeMillis())}", MARGIN, y, subtitlePaint)
        y += 12f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 18f

        // 1. Currency Summary
        if (currencySummaries.isNotEmpty()) {
            ensureSpace(40f + currencySummaries.size * 16f)
            canvas.drawText("1. Dönem Özeti", MARGIN, y, sectionPaint)
            y += 16f
            canvas.drawText("Para Birimi", MARGIN, y, headerPaint)
            canvas.drawText("Toplam Harcama", MARGIN + 110f, y, headerPaint)
            canvas.drawText("Toplam İade", MARGIN + 250f, y, headerPaint)
            canvas.drawText("Net Harcama", MARGIN + 380f, y, headerPaint)
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 13f
            for (s in currencySummaries) {
                canvas.drawText(s.currency, MARGIN, y, cellPaint)
                canvas.drawText(Formatters.formatMoney(s.grossExpense, s.currency), MARGIN + 110f, y, cellPaint)
                canvas.drawText(Formatters.formatMoney(s.totalRefund, s.currency), MARGIN + 250f, y, cellPaint)
                canvas.drawText(Formatters.formatMoney(s.netExpense, s.currency), MARGIN + 380f, y, headerPaint)
                y += 15f
            }
            y += 10f
        }

        // 2. Bank Breakdown
        if (bankBreakdown.isNotEmpty()) {
            ensureSpace(42f)
            canvas.drawText("2. Bankalara Göre Dağılım (TL)", MARGIN, y, sectionPaint)
            y += 16f
            canvas.drawText("Banka", MARGIN, y, headerPaint)
            canvas.drawText("Adet", MARGIN + 160f, y, headerPaint)
            canvas.drawText("Harcama", MARGIN + 210f, y, headerPaint)
            canvas.drawText("İade", MARGIN + 315f, y, headerPaint)
            canvas.drawText("Net / Pay", MARGIN + 405f, y, headerPaint)
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 13f

            for (b in bankBreakdown) {
                ensureSpace(16f)
                val pct = (b.ratio * 100f).toInt()
                canvas.drawText(truncate(b.bankName, 26), MARGIN, y, cellPaint)
                canvas.drawText(b.count.toString(), MARGIN + 160f, y, cellPaint)
                canvas.drawText(Formatters.formatMoney(b.grossExpense, "TL"), MARGIN + 210f, y, cellPaint)
                canvas.drawText(Formatters.formatMoney(b.totalRefund, "TL"), MARGIN + 315f, y, cellPaint)
                canvas.drawText("${Formatters.formatMoney(b.netExpense, "TL")} (%$pct)", MARGIN + 405f, y, cellPaint)
                y += 15f
            }
            y += 10f
        }

        // 3. Category Breakdown
        if (categoryBreakdown.isNotEmpty()) {
            ensureSpace(42f)
            canvas.drawText("3. Kategorilere Göre Dağılım (TL)", MARGIN, y, sectionPaint)
            y += 16f
            canvas.drawText("Kategori", MARGIN, y, headerPaint)
            canvas.drawText("İşlem Adedi", MARGIN + 200f, y, headerPaint)
            canvas.drawText("Net Tutar", MARGIN + 300f, y, headerPaint)
            canvas.drawText("Oran", MARGIN + 430f, y, headerPaint)
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 13f

            for (c in categoryBreakdown) {
                ensureSpace(16f)
                val pct = (c.ratio * 100f).toInt()
                canvas.drawText(truncate(c.category, 30), MARGIN, y, cellPaint)
                canvas.drawText(c.count.toString(), MARGIN + 200f, y, cellPaint)
                canvas.drawText(Formatters.formatMoney(c.amount, "TL"), MARGIN + 300f, y, cellPaint)
                canvas.drawText("%$pct", MARGIN + 430f, y, cellPaint)
                y += 15f
            }
            y += 10f
        }

        // 4. Transactions List
        ensureSpace(44f)
        canvas.drawText("4. İşlem Dökümü (${transactions.size} kayıt)", MARGIN, y, sectionPaint)
        y += 16f
        canvas.drawText("Tarih", MARGIN, y, headerPaint)
        canvas.drawText("Bölüm", MARGIN + 72f, y, headerPaint)
        canvas.drawText("Banka / Kart", MARGIN + 132f, y, headerPaint)
        canvas.drawText("İşyeri", MARGIN + 246f, y, headerPaint)
        canvas.drawText("Kategori", MARGIN + 368f, y, headerPaint)
        canvas.drawText("Tutar", MARGIN + 448f, y, headerPaint)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 13f

        for (tx in transactions) {
            ensureSpace(16f)
            val groupName = tx.groupId?.let { groupsById[it]?.name } ?: "Bölümsüz"
            val cardTypeShort = when (DetectedCardType.fromCode(tx.cardType)) {
                DetectedCardType.CREDIT -> "KK"
                DetectedCardType.VIRTUAL -> "Sanal"
                DetectedCardType.DEBIT -> "BK"
                DetectedCardType.CASH -> "Nakit"
            }
            val bankCard = "${tx.bankName} • ${tx.cardLast4} ($cardTypeShort)"
            val isRefund = tx.transactionType == ParsedTransactionType.REFUND.name
            val amountStr = (if (isRefund) "+" else "") + Formatters.formatMoney(tx.amount, tx.currency)

            canvas.drawText(Formatters.formatDateInput(tx.timestamp), MARGIN, y, cellPaint)
            canvas.drawText(truncate(groupName, 11), MARGIN + 72f, y, cellPaint)
            canvas.drawText(truncate(bankCard, 21), MARGIN + 132f, y, cellPaint)
            canvas.drawText(truncate(tx.merchant, 22), MARGIN + 246f, y, cellPaint)
            canvas.drawText(truncate(tx.category, 14), MARGIN + 368f, y, cellPaint)
            canvas.drawText(amountStr, MARGIN + 448f, y, if (isRefund) refundPaint else cellPaint)
            y += 14f
        }

        drawFooter(canvas, pageNumber)
        doc.finishPage(page)

        FileOutputStream(outFile).use { out ->
            doc.writeTo(out)
        }
        doc.close()
        return outFile
    }

    private fun truncate(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars - 1) + "…"
    }
}
