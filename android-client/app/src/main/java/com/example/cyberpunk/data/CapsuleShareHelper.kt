package com.example.cyberpunk.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.timecapsule.shared.CapsuleDto
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ShareCardMetadata(
    val title: String,
    val createdDateFormatted: String,
    val openedDateFormatted: String,
    val creatorHandleDisplay: String,
    val recipientDisplay: String?,
    val tagsDisplay: List<String>,
    val unlockedStagesSummary: String
)

object CapsuleShareHelper {

    private const val SHARE_DIR_NAME = "capsule-share"

    fun buildShareCardMetadata(
        capsule: CapsuleDto,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ShareCardMetadata {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zoneId)
        val firstUnlockTs = capsule.stages.minOfOrNull { it.unlockDate } ?: System.currentTimeMillis()
        val createdTs = if (capsule.createdAt > 0L) capsule.createdAt else firstUnlockTs
        val openedTs = capsule.openedAt ?: firstUnlockTs

        val createdStr = runCatching { formatter.format(Instant.ofEpochMilli(createdTs)) }.getOrDefault("—")
        val openedStr = runCatching { formatter.format(Instant.ofEpochMilli(openedTs)) }.getOrDefault("—")

        val creatorParticipant = capsule.participants.firstOrNull { it.userId == capsule.creatorId }
        val handleDisplay = when {
            !capsule.creatorHandle.isNullOrBlank() -> "@${capsule.creatorHandle.trim().removePrefix("@")}"
            !creatorParticipant?.handle.isNullOrBlank() -> "@${creatorParticipant!!.handle!!.trim().removePrefix("@")}"
            !creatorParticipant?.username.isNullOrBlank() -> creatorParticipant!!.username.trim()
            else -> "Участник Time Capsule"
        }

        val cleanTags = capsule.tags
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .map { "#$it" }

        val totalStages = capsule.stages.size.coerceAtLeast(1)
        val unlockedCount = capsule.stages.count { it.isUnlocked }.coerceAtLeast(if (capsule.hasUnlockedStage) 1 else 0)

        return ShareCardMetadata(
            title = capsule.title.ifBlank { "Капсула времени" },
            createdDateFormatted = createdStr,
            openedDateFormatted = openedStr,
            creatorHandleDisplay = handleDisplay,
            recipientDisplay = capsule.recipient?.trim()?.takeIf { it.isNotEmpty() },
            tagsDisplay = cleanTags,
            unlockedStagesSummary = "Открыто этапов: $unlockedCount из $totalStages"
        )
    }

    /**
     * Generates a 1080x1350 neon Tokyo Night PNG card (strictly WITHOUT secret letter text)
     * and opens the Android system Share Sheet via FileProvider.
     */
    fun shareCapsuleCardPng(context: Context, capsule: CapsuleDto) {
        val metadata = buildShareCardMetadata(capsule)
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Tokyo Night Background Gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    Color.parseColor("#0F111A"),
                    Color.parseColor("#1A1B26"),
                    Color.parseColor("#131522")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Subtle Cyberpunk Grid
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(18, 122, 162, 247)
            strokeWidth = 1.5f
        }
        val step = 72f
        var gx = step
        while (gx < width) {
            canvas.drawLine(gx, 0f, gx, height.toFloat(), gridPaint)
            gx += step
        }
        var gy = step
        while (gy < height) {
            canvas.drawLine(0f, gy, width.toFloat(), gy, gridPaint)
            gy += step
        }

        // 3. Outer Neon Card Frame
        val cardRect = RectF(64f, 64f, width - 64f, height - 64f)
        val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 26, 27, 38)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, surfacePaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                intArrayOf(
                    Color.parseColor("#7AA2F7"),
                    Color.parseColor("#BB9AF7"),
                    Color.parseColor("#7DCFFF")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

        // 4. Top Neon Badge
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7DCFFF")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("✦ TIME CAPSULE 2.0 • ОТКРЫТАЯ КАПСУЛА ✦", 120f, 164f, badgePaint)

        // Divider line
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 122, 162, 247)
            strokeWidth = 2f
        }
        canvas.drawLine(120f, 200f, width - 120f, 200f, dividerPaint)

        // 5. Title (wrapped up to 3 lines)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C0CAF5")
            textSize = 62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLines = wrapText(metadata.title, titlePaint, width - 240f, maxLines = 3)
        var currentY = 300f
        for (line in titleLines) {
            canvas.drawText(line, 120f, currentY, titlePaint)
            currentY += 76f
        }

        // 6. Author (@handle) & Recipient
        currentY += 28f
        val authorLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A9B1D6")
            textSize = 30f
        }
        val authorValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BB9AF7")
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("АВТОР КАПСУЛЫ", 120f, currentY, authorLabelPaint)
        currentY += 52f
        canvas.drawText(metadata.creatorHandleDisplay, 120f, currentY, authorValuePaint)

        if (metadata.recipientDisplay != null) {
            currentY += 56f
            canvas.drawText("ПОЛУЧАТЕЛЬ: ${metadata.recipientDisplay}", 120f, currentY, authorLabelPaint)
        }

        // 7. Dates Box (Sealed & Opened)
        currentY += 64f
        val datesBoxRect = RectF(120f, currentY, width - 120f, currentY + 260f)
        val datesBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F111A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(datesBoxRect, 28f, 28f, datesBoxPaint)

        val datesBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 122, 162, 247)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(datesBoxRect, 28f, 28f, datesBorderPaint)

        val dateHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A9B1D6")
            textSize = 28f
        }
        val dateValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7AA2F7")
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val openedValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9ECE6A")
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText("ДАТА ЗАПЕЧАТЫВАНИЯ", 160f, currentY + 64f, dateHeaderPaint)
        canvas.drawText(metadata.createdDateFormatted, 160f, currentY + 112f, dateValuePaint)

        canvas.drawText("ДАТА ОТКРЫТИЯ", 160f, currentY + 178f, dateHeaderPaint)
        canvas.drawText(metadata.openedDateFormatted, 160f, currentY + 226f, openedValuePaint)

        currentY += 320f

        // 8. Stages status badge
        val stageSummaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7DCFFF")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(metadata.unlockedStagesSummary, 120f, currentY, stageSummaryPaint)

        // 9. Tags chips row
        if (metadata.tagsDisplay.isNotEmpty()) {
            currentY += 68f
            val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7AA2F7")
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(45, 122, 162, 247)
                style = Paint.Style.FILL
            }
            var chipX = 120f
            for (tag in metadata.tagsDisplay.take(5)) {
                val textWidth = tagTextPaint.measureText(tag)
                val chipWidth = textWidth + 48f
                if (chipX + chipWidth > width - 120f) break
                val chipRect = RectF(chipX, currentY - 38f, chipX + chipWidth, currentY + 18f)
                canvas.drawRoundRect(chipRect, 28f, 28f, tagBgPaint)
                canvas.drawText(tag, chipX + 24f, currentY, tagTextPaint)
                chipX += chipWidth + 18f
            }
        }

        // 10. Privacy Footer (explicitly noting secret text is excluded)
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A9B1D6")
            textSize = 24f
        }
        canvas.drawLine(120f, height - 180f, width - 120f, height - 180f, dividerPaint)
        canvas.drawText(
            "Текст письма скрыт из соображений приватности • Time Capsule 2.0",
            120f,
            height - 125f,
            footerPaint
        )

        val shareDir = File(context.cacheDir, SHARE_DIR_NAME).apply { mkdirs() }
        val safeId = capsule.id.replace(Regex("[^a-zA-Z0-9_-]"), "").take(12).ifEmpty { "capsule" }
        val outFile = File(shareDir, "capsule_card_$safeId.png")
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        launchShareFileIntent(
            context = context,
            file = outFile,
            mimeType = "image/png",
            chooserTitle = "Поделиться карточкой капсулы"
        )
    }

    /**
     * Generates a PDF document containing the unlocked capsule stages and letter text
     * using Android's native PdfDocument and opens the system Share Sheet via FileProvider.
     */
    fun shareCapsuleLetterPdf(context: Context, capsule: CapsuleDto) {
        val metadata = buildShareCardMetadata(capsule)
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())

        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        val margin = 48f
        val contentWidth = pageWidth - margin * 2

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1B26")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#414868")
            textSize = 11f
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3D59A1")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1B26")
            textSize = 12f
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C0CAF5")
            strokeWidth = 1f
        }

        var pageNumber = 1
        var currentPage = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = currentPage.canvas
        var y = margin + 16f

        fun ensureSpace(requiredHeight: Float) {
            if (y + requiredHeight > pageHeight - margin) {
                pdfDocument.finishPage(currentPage)
                pageNumber += 1
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage.canvas
                y = margin + 16f
            }
        }

        // Header
        canvas.drawText("TIME CAPSULE 2.0 — ПИСЬМО ИЗ КАПСУЛЫ ВРЕМЕНИ", margin, y, sectionTitlePaint)
        y += 26f

        for (line in wrapText(metadata.title, titlePaint, contentWidth, maxLines = 4)) {
            ensureSpace(26f)
            canvas.drawText(line, margin, y, titlePaint)
            y += 24f
        }

        y += 6f
        canvas.drawText("Автор: ${metadata.creatorHandleDisplay}", margin, y, subtitlePaint)
        y += 16f
        if (metadata.recipientDisplay != null) {
            canvas.drawText("Получатель: ${metadata.recipientDisplay}", margin, y, subtitlePaint)
            y += 16f
        }
        canvas.drawText(
            "Запечатана: ${metadata.createdDateFormatted}   |   Открыта: ${metadata.openedDateFormatted}",
            margin,
            y,
            subtitlePaint
        )
        y += 16f
        if (metadata.tagsDisplay.isNotEmpty()) {
            canvas.drawText("Теги: ${metadata.tagsDisplay.joinToString(" ")}", margin, y, subtitlePaint)
            y += 16f
        }

        y += 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 24f

        val unlockedStages = capsule.stages
            .filter { it.isUnlocked }
            .sortedBy { it.stageNumber }

        if (unlockedStages.isEmpty()) {
            ensureSpace(24f)
            canvas.drawText("Нет открытых этапов для экспорта.", margin, y, bodyPaint)
        } else {
            for (stage in unlockedStages) {
                ensureSpace(44f)
                val stageDateStr = runCatching {
                    formatter.format(Instant.ofEpochMilli(stage.unlockDate))
                }.getOrDefault("")
                canvas.drawText(
                    "Этап #${stage.stageNumber} (дата открытия: $stageDateStr)",
                    margin,
                    y,
                    sectionTitlePaint
                )
                y += 20f

                val paragraphs = stage.text.split("\n")
                for (paragraph in paragraphs) {
                    val lines = wrapText(paragraph.ifBlank { " " }, bodyPaint, contentWidth, maxLines = 500)
                    for (line in lines) {
                        ensureSpace(18f)
                        canvas.drawText(line, margin, y, bodyPaint)
                        y += 17f
                    }
                }
                y += 16f
            }
        }

        pdfDocument.finishPage(currentPage)

        val shareDir = File(context.cacheDir, SHARE_DIR_NAME).apply { mkdirs() }
        val safeId = capsule.id.replace(Regex("[^a-zA-Z0-9_-]"), "").take(12).ifEmpty { "capsule" }
        val outFile = File(shareDir, "capsule_letter_$safeId.pdf")
        FileOutputStream(outFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        launchShareFileIntent(
            context = context,
            file = outFile,
            mimeType = "application/pdf",
            chooserTitle = "Сохранить или отправить PDF-письмо"
        )
    }

    private fun launchShareFileIntent(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun wrapText(
        text: String,
        paint: Paint,
        maxWidth: Float,
        maxLines: Int
    ): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    if (lines.size >= maxLines) {
                        val lastIdx = lines.lastIndex
                        lines[lastIdx] = lines[lastIdx].take((lines[lastIdx].length - 1).coerceAtLeast(1)) + "…"
                        return lines
                    }
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty() && lines.size < maxLines) {
            lines.add(currentLine.toString())
        }
        return lines
    }
}
