package io.ntole.kvizic.design

import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.samples
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every skin's bank of sounds is whole and sane: a sample for every cue, each a short, mono, 16-bit WAV that
 * starts and ends on silence, is neither silent nor clipped, and a bank fits its size budget. Reads the files and
 * draws nothing, so it costs the design tests next to nothing.
 */
class SoundBankTest {
    private class Wav(
        val sampleRate: Int,
        val channels: Int,
        val bits: Int,
        val pcm: ShortArray,
    ) {
        val seconds: Double get() = pcm.size.toDouble() / sampleRate
        val peak: Int get() = pcm.maxOf { abs(it.toInt()) }
    }

    /** The format's chunks read as they are, so a file written another way than the renderer's fails too. */
    private fun wav(bytes: ByteArray): Wav {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        fun tag(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
        assertEquals("RIFF", tag(0))
        assertEquals("WAVE", tag(8))
        var at = 12
        var format: Triple<Int, Int, Int>? = null
        while (at + 8 <= bytes.size) {
            val size = buffer.getInt(at + 4)
            when (tag(at)) {
                "fmt " -> {
                    assertEquals(1, buffer.getShort(at + 8).toInt(), "PCM")
                    format =
                        Triple(
                            buffer.getShort(at + 10).toInt(),
                            buffer.getInt(at + 12),
                            buffer.getShort(at + 22).toInt(),
                        )
                }

                "data" -> {
                    val (channels, rate, bits) = checkNotNull(format) { "a data chunk before the format" }
                    val pcm = ShortArray(size / 2) { buffer.getShort(at + 8 + it * 2) }
                    return Wav(rate, channels, bits, pcm)
                }
            }
            at += 8 + size + size % 2
        }
        error("no data chunk")
    }

    private fun banks() = Skins.ALL.map { it.sound to runBlocking { it.sound.samples() } }

    @Test
    fun `every skin has a bank of its own`() {
        assertEquals(
            Skins.ALL.size,
            Skins.ALL
                .map { it.sound.bank }
                .toSet()
                .size,
        )
    }

    @Test
    fun `every bank has a sample for every cue`() {
        banks().forEach { (sound, samples) ->
            assertEquals(Cue.entries.toSet(), samples.keys, "the ${sound.bank} bank")
        }
    }

    @Test
    fun `every sample is a short mono 16-bit wav that neither clips nor stays silent`() {
        banks().forEach { (sound, samples) ->
            samples.forEach { (cue, bytes) ->
                val name = "${sound.bank}/${cue.file}"
                val sample = wav(bytes)
                assertEquals(1, sample.channels, "$name is mono")
                assertEquals(16, sample.bits, "$name is 16-bit")
                assertEquals(22_050, sample.sampleRate, "$name's rate")
                assertTrue(sample.seconds in 0.01..2.0, "$name lasts ${sample.seconds} s")
                assertTrue(sample.peak in 3_000..30_000, "$name peaks at ${sample.peak}")
                assertTrue(abs(sample.pcm.first().toInt()) < 100, "$name starts on silence")
                assertTrue(abs(sample.pcm.last().toInt()) < 100, "$name ends on silence")
            }
        }
    }

    @Test
    fun `a bank stays under its size budget`() {
        banks().forEach { (sound, samples) ->
            val kilobytes = samples.values.sumOf { it.size } / 1_000
            assertTrue(kilobytes <= 1_200, "the ${sound.bank} bank is $kilobytes kB")
        }
    }
}
