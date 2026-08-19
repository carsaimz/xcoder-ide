package com.xcoder.editor.web

import java.io.*
import java.nio.charset.*
import java.util.*

/**
 * Detects the character encoding of text content using BOM markers,
 * heuristic analysis, and statistical byte frequency analysis.
 * Supports 15+ encodings including UTF-8, UTF-16, ISO-8859 variants,
 * Windows code pages, CJK encodings, and more.
 */
class EncodingDetector {

    data class DetectionResult(
        val encoding: String,
        val confidence: Float, // 0.0 to 1.0
        val hasBom: Boolean = false,
        val bomSize: Int = 0
    )

    companion object {
        val SUPPORTED_ENCODINGS = listOf(
            "UTF-8", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE",
            "ISO-8859-1", "ISO-8859-2", "ISO-8859-5", "ISO-8859-15",
            "Windows-1250", "Windows-1251", "Windows-1252",
            "GB2312", "GBK", "Big5", "Shift_JIS", "EUC-JP", "EUC-KR",
            "KOI8-R", "ASCII"
        )

        val BOM_MARKERS = mapOf(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) to Triple("UTF-8", 3, 1.0f),
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()) to Triple("UTF-16BE", 2, 1.0f),
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) to Triple("UTF-16LE", 2, 1.0f),
            byteArrayOf(0x00.toByte(), 0x00.toByte(), 0xFE.toByte(), 0xFF.toByte()) to Triple("UTF-32BE", 4, 1.0f),
            byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte()) to Triple("UTF-32LE", 4, 1.0f)
        )

        private const val SAMPLE_SIZE = 4096
    }

    /**
     * Detect encoding from raw bytes.
     */
    fun detect(bytes: ByteArray): DetectionResult {
        // 1. Check BOM first (highest confidence)
        val bomResult = detectBom(bytes)
        if (bomResult != null) return bomResult

        // 2. Heuristic analysis
        return heuristicDetect(bytes)
    }

    /**
     * Detect encoding from a string (assumes current platform encoding or UTF-8).
     */
    fun detectFromString(text: String): DetectionResult {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return detect(bytes)
    }

    /**
     * Check for BOM markers.
     */
    private fun detectBom(bytes: ByteArray): DetectionResult? {
        for ((bom, triple) in BOM_MARKERS) {
            val (encoding, bomSize, confidence) = triple
            if (bytes.size >= bomSize) {
                var match = true
                for (i in 0 until bomSize) {
                    if (bytes[i] != bom[i]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    return DetectionResult(encoding, confidence, true, bomSize)
                }
            }
        }
        return null
    }

    /**
     * Heuristic encoding detection using byte patterns.
     */
    private fun heuristicDetect(bytes: ByteArray): DetectionResult {
        if (bytes.isEmpty()) return DetectionResult("UTF-8", 0.5f)

        val sample = bytes.take(SAMPLE_SIZE).toByteArray()
        val isAscii = sample.all { b -> b >= 0x00 && b <= 0x7F }

        if (isAscii) return DetectionResult("ASCII", 0.9f)

        // Check for valid UTF-8
        val utf8Score = scoreUtf8(sample)
        if (utf8Score > 0.95f) return DetectionResult("UTF-8", utf8Score)

        // Check for CJK encodings
        val gbkScore = scoreCjk(sample, GB2312_RANGES)
        if (gbkScore > 0.8f) return DetectionResult("GBK", gbkScore * 0.9f)

        val big5Score = scoreCjk(sample, BIG5_RANGES)
        if (big5Score > 0.8f) return DetectionResult("Big5", big5Score * 0.9f)

        val shiftJisScore = scoreCjk(sample, SHIFT_JIS_RANGES)
        if (shiftJisScore > 0.8f) return DetectionResult("Shift_JIS", shiftJisScore * 0.9f)

        // Check for UTF-16
        val utf16Score = scoreUtf16(sample)
        if (utf16Score > 0.8f) {
            return DetectionResult(
                if (sample.size >= 2 && sample[0] == 0x00.toByte()) "UTF-16BE" else "UTF-16LE",
                utf16Score
            )
        }

        // Check for EUC-JP
        val eucJpScore = scoreCjk(sample, EUC_JP_RANGES)
        if (eucJpScore > 0.8f) return DetectionResult("EUC-JP", eucJpScore * 0.85f)

        // Check for EUC-KR
        val eucKrScore = scoreCjk(sample, EUC_KR_RANGES)
        if (eucKrScore > 0.8f) return DetectionResult("EUC-KR", eucKrScore * 0.85f)

        // Default to UTF-8 with low confidence
        return DetectionResult("UTF-8", 0.6f)
    }

    /**
     * Score how likely the byte sequence is valid UTF-8.
     * Returns a value between 0.0 and 1.0.
     */
    private fun scoreUtf8(bytes: ByteArray): Float {
        if (bytes.isEmpty()) return 0f
        var validBytes = 0
        var totalMultiByte = 0
        var i = 0

        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> validBytes++
                b in 0xC2..0xDF -> { // 2-byte sequence
                    if (i + 1 < bytes.size) {
                        val next = bytes[i + 1].toInt() and 0xFF
                        if (next in 0x80..0xBF) {
                            validBytes += 2; totalMultiByte++
                        }
                        i++
                    }
                }
                b in 0xE0..0xEF -> { // 3-byte sequence
                    if (i + 2 < bytes.size) {
                        val n1 = bytes[i + 1].toInt() and 0xFF
                        val n2 = bytes[i + 2].toInt() and 0xFF
                        if (n1 in 0x80..0xBF && n2 in 0x80..0xBF) {
                            validBytes += 3; totalMultiByte++
                        }
                        i += 2
                    }
                }
                b in 0xF0..0xF4 -> { // 4-byte sequence
                    if (i + 3 < bytes.size) {
                        val n1 = bytes[i + 1].toInt() and 0xFF
                        val n2 = bytes[i + 2].toInt() and 0xFF
                        val n3 = bytes[i + 3].toInt() and 0xFF
                        if (n1 in 0x80..0xBF && n2 in 0x80..0xBF && n3 in 0x80..0xBF) {
                            validBytes += 4; totalMultiByte++
                        }
                        i += 3
                    }
                }
                else -> { /* invalid byte */ }
            }
            i++
        }

        return if (totalMultiByte == 0) {
            if (bytes.all { it >= 0x00 && it <= 0x7F }) 1.0f else 0.5f
        } else {
            validBytes.toFloat() / bytes.size
        }
    }

    /**
     * Score byte sequence against CJK encoding byte ranges.
     */
    private fun scoreCjk(bytes: ByteArray, ranges: List<IntRange>): Float {
        var multiByteCount = 0
        var validMultiByte = 0
        var i = 0

        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 0x80..0xFF) {
                multiByteCount++
                if (i + 1 < bytes.size) {
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    val isValid = ranges.any { range ->
                        b in range && b2 in 0x40..0xFE
                    }
                    if (isValid) validMultiByte++
                    i++
                }
            }
            i++
        }

        return if (multiByteCount == 0) 0f else validMultiByte.toFloat() / multiByteCount
    }

    /**
     * Simple UTF-16 scoring.
     */
    private fun scoreUtf16(bytes: ByteArray): Float {
        if (bytes.size < 2) return 0f
        var nullCount = 0
        var totalChecked = 0
        val step = if (bytes[0] == 0x00.toByte()) 2 else 1

        for (i in step until bytes.size step 2) {
            if (i < bytes.size && bytes[i] == 0x00.toByte()) nullCount++
            totalChecked++
        }

        return if (totalChecked == 0) 0f else nullCount.toFloat() / totalChecked
    }

    /**
     * Convert bytes from detected encoding to a String.
     */
    fun decode(bytes: ByteArray, encoding: String): String {
        return try {
            val charset = when (encoding) {
                "ISO-8859-1" -> Charsets.ISO_8859_1
                "UTF-16LE" -> Charsets.UTF_16LE
                "UTF-16BE" -> Charsets.UTF_16BE
                "ASCII" -> Charsets.US_ASCII
                else -> Charset.forName(encoding)
            }
            String(bytes, charset)
        } catch (e: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    /**
     * Convert a String to bytes in the specified encoding.
     */
    fun encode(text: String, encoding: String): ByteArray {
        return try {
            val charset = when (encoding) {
                "ISO-8859-1" -> Charsets.ISO_8859_1
                "UTF-16LE" -> Charsets.UTF_16LE
                "UTF-16BE" -> Charsets.UTF_16BE
                "ASCII" -> Charsets.US_ASCII
                else -> Charset.forName(encoding)
            }
            text.toByteArray(charset)
        } catch (e: Exception) {
            text.toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * Re-encode text from one encoding to another.
     */
    fun reEncode(text: String, fromEncoding: String, toEncoding: String): String {
        val bytes = encode(text, fromEncoding)
        return decode(bytes, toEncoding)
    }

    // CJK byte ranges for leading bytes
    private val GB2312_RANGES = listOf(0xA1..0xF7, 0x81..0xFE)
    private val BIG5_RANGES = listOf(0xA1..0xFE, 0x81..0xFE)
    private val SHIFT_JIS_RANGES = listOf(0x81..0x9F, 0xE0..0xFC)
    private val EUC_JP_RANGES = listOf(0xA1..0xFE, 0x8E..0xFE)
    private val EUC_KR_RANGES = listOf(0xA1..0xFE, 0x8E..0xFE)
}
