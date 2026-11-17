package com.v.assistant

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Gguf {
    class Info(
        val arch: String, val name: String, val sizeLabel: String, val quant: String,
        val ctxLen: Long, val multimodal: Boolean, val fileBytes: Long
    )

    private val QUANT = mapOf(
        0L to "F32", 1L to "F16", 2L to "Q4_0", 3L to "Q4_1", 7L to "Q8_0", 8L to "Q5_0", 9L to "Q5_1",
        10L to "Q2_K", 11L to "Q3_K_S", 12L to "Q3_K_M", 13L to "Q3_K_L", 14L to "Q4_K_S",
        15L to "Q4_K_M", 16L to "Q5_K_S", 17L to "Q5_K_M", 18L to "Q6_K"
    )

    private fun rd(i: DataInputStream, n: Int): ByteBuffer {
        val b = ByteArray(n)
        i.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
    }
    private fun u32(i: DataInputStream): Long = rd(i, 4).int.toLong() and 0xFFFFFFFFL
    private fun u64(i: DataInputStream): Long = rd(i, 8).long
    private fun str(i: DataInputStream): String {
        val len = u64(i).toInt()
        val b = ByteArray(len)
        i.readFully(b)
        return String(b, Charsets.UTF_8)
    }
    private fun skip(i: DataInputStream, n: Long) {
        var left = n
        while (left > 0) {
            val s = i.skipBytes(minOf(left, Int.MAX_VALUE.toLong()).toInt())
            if (s <= 0) { i.readByte(); left-- } else left -= s
        }
    }

    private fun skipArray(i: DataInputStream, et: Int, cnt: Long) {
        when (et) {
            8 -> { var j = 0L; while (j < cnt) { skip(i, u64(i)); j++ } }
            0, 1, 7 -> skip(i, cnt)
            2, 3 -> skip(i, cnt * 2)
            4, 5, 6 -> skip(i, cnt * 4)
            10, 11, 12 -> skip(i, cnt * 8)
            else -> throw IOException("array type $et")
        }
    }

    private fun value(i: DataInputStream, type: Int): Any? = when (type) {
        0, 1 -> i.readByte().toLong()
        7 -> i.readByte().toInt() != 0
        2, 3 -> rd(i, 2).short.toLong()
        4, 5 -> rd(i, 4).int.toLong()
        6 -> rd(i, 4).float.toDouble()
        8 -> str(i)
        9 -> { val et = u32(i).toInt(); val cnt = u64(i); skipArray(i, et, cnt); null }
        10, 11 -> rd(i, 8).long
        12 -> rd(i, 8).double
        else -> throw IOException("type $type")
    }

    fun read(f: File): Info? {
        try {
            DataInputStream(BufferedInputStream(FileInputStream(f), 1 shl 20)).use { i ->
                val magic = ByteArray(4)
                i.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != "GGUF") return null
                u32(i); u64(i)
                val kv = u64(i)
                var arch = ""; var name = ""; var size = ""; var ft = -1L; var mm = false
                val ctxMap = HashMap<String, Long>()
                var idx = 0L
                while (idx < kv) {
                    val key = str(i)
                    val type = u32(i).toInt()
                    val v = value(i, type)
                    if (key == "general.architecture" && v is String) arch = v
                    if (key == "general.name" && v is String) name = v
                    if (key == "general.size_label" && v is String) size = v
                    if (key == "general.file_type" && v is Long) ft = v
                    if (key.startsWith("clip.") || key.contains("vision")) mm = true
                    if (key.endsWith(".context_length") && v is Long) ctxMap[key] = v
                    idx++
                }
                if (arch == "clip") mm = true
                val ctx = ctxMap["$arch.context_length"] ?: ctxMap.values.firstOrNull() ?: 0L
                return Info(arch, name, size, QUANT[ft] ?: "?", ctx, mm, f.length())
            }
        } catch (e: Exception) {
            return null
        }
    }
}
