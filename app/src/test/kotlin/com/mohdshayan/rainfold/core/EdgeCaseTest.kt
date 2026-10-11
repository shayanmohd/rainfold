package com.mohdshayan.rainfold.core

import com.mohdshayan.rainfold.core.dsp.Limiter
import com.mohdshayan.rainfold.core.mix.EngineKind
import com.mohdshayan.rainfold.core.mix.FadeCurve
import com.mohdshayan.rainfold.core.mix.FreeTier
import com.mohdshayan.rainfold.core.mix.LayerSpec
import com.mohdshayan.rainfold.core.mix.MixCodec
import com.mohdshayan.rainfold.core.mix.MixSpec
import com.mohdshayan.rainfold.core.mix.Mixer
import com.mohdshayan.rainfold.core.mix.SleepPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** The edges: empty, zero, negative, huge, corrupt files, unit and clock boundaries. */
class EdgeCaseTest {

    private fun mixes(text: String) = MixCodec.decode(text) as MixCodec.Decoded.Mixes

    @Test
    fun corruptOrOddImportFilesNeverThrow() {
        val bad = listOf(
            "",
            "   ",
            "{",
            "null",
            "\"rainfold-mixes\"",
            """{"format":{"x":1},"schema":1,"mixes":[]}""",
            """{"format":["rainfold-mixes"],"schema":1,"mixes":[]}""",
            """{"format":"rainfold-mixes","schema":{},"mixes":[]}""",
            """{"format":"rainfold-mixes","schema":"one","mixes":[]}""",
            """{"format":"rainfold-mixes","schema":1}""",
            """{"format":"rainfold-mixes","schema":1,"mixes":null}""",
            """{"format":"rainfold-mixes","schema":1,"mixes":{"name":"Desk"}}""",
            """{"format":"rainfold-mixes","schema":1,"mixes":[{"layers":[]}]}""",
            """{"format":"rainfold-mixes","schema":1,"mixes":[{"name":"Desk","layers":[{"volume":1}]}]}""",
            """{"format":"rainfold-mixes","schema":1,"mixes":[{"name":"Desk","layers":[{"engine":"BROWN","volume":NaN}]}]}""",
            """{"format":"rainfold-mixes","schema":1,"mixes":[{"name":"Desk","layers":[{"engine":"BROWN","volume":1e400}]}]}""",
            """{"format":"rainfold-mixes","schema":1,"mixes":[{"name":"Desk","layers":[{"engine":"BROWN","volume":"loud"}]}]}""",
            "[".repeat(5000) + "]".repeat(5000),
            """{"format":"rainfold-mixes","schema":1,"mixes":[""" + "[".repeat(200_000) + "]".repeat(200_000) + "]}",
            "{\"a\":".repeat(50_000) + "1" + "}".repeat(50_000),
            String(ByteArray(4096) { (it * 31 % 256).toByte() }, Charsets.UTF_8),
        )
        for (text in bad) {
            assertEquals("input: ${text.take(60)}", MixCodec.Decoded.NotAMixFile, MixCodec.decode(text))
        }
    }

    @Test
    fun outOfRangeValuesAreClampedAndLongNamesCut() {
        val text = """{"format":"rainfold-mixes","schema":1,"mixes":[
            {"name":"${"x".repeat(500)}","driftRange":-4,"layers":[{"engine":"PINK","volume":5,"tone":-9,"intensity":-1}]},
            {"name":"   ","driftRange":99,"layers":[{"engine":"FAN","volume":-0.5,"tone":3,"intensity":42}]}]}"""
        val decoded = mixes(text)
        val a = decoded.mixes[0]
        assertEquals(MixCodec.MAX_NAME, a.name.length)
        assertEquals(0f, a.driftRange, 0f)
        assertEquals(LayerSpec(EngineKind.PINK, 1f, -1f, 0f, key = 0), a.layers[0])
        val b = decoded.mixes[1]
        assertEquals("Mix", b.name)
        assertEquals(0.5f, b.driftRange, 0f)
        assertEquals(LayerSpec(EngineKind.FAN, 0f, 1f, 1f, key = 0), b.layers[0])
    }

    @Test
    fun emptyFilesAndMixesDecodeAndExtraLayersAreCut() {
        assertEquals(0, mixes("""{"format":"rainfold-mixes","schema":1,"mixes":[]}""").mixes.size)
        val empty = mixes("""{"format":"rainfold-mixes","schema":1,"mixes":[{"name":"Quiet","layers":[]}]}""")
        assertTrue(empty.mixes[0].layers.isEmpty())
        val ten = (1..10).joinToString(",") { """{"engine":"WHITE"}""" }
        val cut = mixes("""{"format":"rainfold-mixes","schema":1,"mixes":[{"name":"Wall","layers":[$ten]}]}""")
        assertEquals(FreeTier.MAX_LAYERS, cut.mixes[0].layers.size)
        assertEquals((0 until FreeTier.MAX_LAYERS).toList(), cut.mixes[0].layers.map { it.key })
        // Encoding nothing is still a valid file.
        assertEquals(0, mixes(MixCodec.encode(emptyList())).mixes.size)
    }

    @Test
    fun uniqueNamesSurviveManyClashesAndOddCase() {
        val taken = listOf("Desk") + (2..50).map { "Desk ($it)" }
        assertEquals("Desk (51)", MixCodec.uniqueName("Desk", taken))
        assertEquals("DESK (2)", MixCodec.uniqueName("DESK", listOf("desk")))
        assertEquals("Desk", MixCodec.uniqueName("Desk", emptyList()))
    }

    @Test
    fun freeTierEdges() {
        assertTrue(FreeTier.canAddLayer(false, 0))
        assertTrue(FreeTier.canSaveMix(false, 0))
        assertTrue(FreeTier.canSaveMix(false, -1))
        assertEquals(emptyList<Boolean>(), FreeTier.playableLayers(false, emptyList()))
        // Only locked sounds: nothing plays, nothing is removed.
        val locked = listOf(EngineKind.STORM, EngineKind.CAFE).mapIndexed { i, k -> LayerSpec(k, key = i) }
        assertEquals(listOf(false, false), FreeTier.playableLayers(false, locked))
        // Twelve free layers unlocked: still only six sound.
        val twelve = List(12) { LayerSpec(EngineKind.WHITE, key = it) }
        assertEquals(6, FreeTier.playableLayers(true, twelve).count { it })
        assertEquals(2, FreeTier.playableLayers(false, twelve).count { it })
    }

    @Test
    fun sleepPlanClampsZeroNegativeAndHugeInput() {
        val zero = SleepPlan.of(0L, 0, 0)
        assertEquals(SleepPlan.MIN_MINUTES * 60_000L, zero.durationMs)
        assertEquals(SleepPlan.MIN_FADE * 60_000L, zero.fadeMs)
        val negative = SleepPlan.of(0L, -30, -5)
        assertEquals(zero, negative)
        val huge = SleepPlan.of(0L, Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals(SleepPlan.MAX_MINUTES * 60_000L, huge.durationMs)
        assertEquals(SleepPlan.MAX_FADE * 60_000L, huge.fadeMs)
        // A fade longer than the timer starts at once and never runs past the end.
        val short = SleepPlan.of(1_000L, 15, 30)
        assertEquals(short.startedAt, short.fadeStartsAt)
        assertEquals(1f, short.gainAt(short.startedAt), 1e-6f)
        assertEquals(0f, short.gainAt(short.endsAt), 0f)
        assertTrue(short.finishedAt(short.endsAt))
        assertFalse(short.finishedAt(short.endsAt - 1))
        assertEquals(0L, short.remainingMs(short.endsAt + 60_000))
        // Before the timer starts (a clock set back) the level stays full.
        assertEquals(1f, short.gainAt(-10_000_000L), 0f)
    }

    @Test
    fun fadeCurveBoundaries() {
        assertEquals(1f, FadeCurve.gain(0, 60_000), 0f)
        assertEquals(1f, FadeCurve.gain(-5, 60_000), 0f)
        assertEquals(0f, FadeCurve.gain(60_000, 60_000), 0f)
        assertEquals(0f, FadeCurve.gain(Long.MAX_VALUE, 60_000), 0f)
        assertEquals(0f, FadeCurve.gain(10, 0), 0f)
        assertEquals(0f, FadeCurve.gain(10, -1), 0f)
        assertEquals(0.70710677f, FadeCurve.gain(30_000, 60_000), 1e-4f)
    }

    @Test
    fun aTimerAcrossADaylightSavingChangeRunsItsRealDuration() {
        // The plan is wall-clock independent: epoch milliseconds, so a DST jump in either direction
        // neither shortens nor stretches the night.
        for ((zone, start) in listOf(
            "America/Phoenix" to LocalDateTime.of(2026, 3, 8, 0, 30),
            "America/New_York" to LocalDateTime.of(2026, 3, 8, 0, 30),
            "America/New_York" to LocalDateTime.of(2026, 11, 1, 0, 30),
            "Europe/Dublin" to LocalDateTime.of(2026, 10, 25, 0, 30),
            "Asia/Kolkata" to LocalDateTime.of(2026, 10, 11, 23, 50),
        )) {
            val startMs = start.atZone(ZoneId.of(zone)).toInstant().toEpochMilli()
            val plan = SleepPlan.of(startMs, 240, 15)
            assertEquals(4 * 3_600_000L, plan.endsAt - startMs)
            assertEquals(plan.endsAt - 15 * 60_000L, plan.fadeStartsAt)
        }
    }

    @Test
    fun limiterTurnsNaNAndInfinityIntoSafeSamples() {
        val lim = Limiter(48_000)
        val buf = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN)
        lim.process(buf, 2)
        buf.forEach { assertTrue("sample $it", it.isFinite() && kotlin.math.abs(it) <= lim.ceiling) }
        // A bad sample must not silence or unguard the bus for good: quiet audio comes straight back.
        val after = FloatArray(2 * 480) { 0.1f }
        lim.process(after, 480)
        assertEquals(0.1f, after.last(), 0.01f)
        // A huge finite spike is held under the ceiling too.
        val spike = floatArrayOf(1e30f, -1e30f, 0f, -0f)
        lim.process(spike, 2)
        spike.forEach { assertTrue("sample $it", it.isFinite() && kotlin.math.abs(it) <= lim.ceiling) }
    }

    @Test
    fun anEmptyMixRendersSilenceAndZeroFramesIsANoOp() {
        val mixer = Mixer(seed = 3)
        mixer.setMasterTarget(1f)
        mixer.update(MixSpec("Empty", emptyList()), emptyList(), evolvingAllowed = false)
        val out = FloatArray(Mixer.BLOCK * 2) { 9f }
        mixer.render(out, Mixer.BLOCK)
        assertTrue(out.all { it == 0f })
        val untouched = FloatArray(8) { 7f }
        mixer.render(untouched, 0)
        assertTrue(untouched.all { it == 7f })
        // A render length that is not a multiple of the block size fills exactly what was asked.
        mixer.update(MixSpec("One", listOf(LayerSpec(EngineKind.BROWN, 1f))), listOf(true), false)
        val odd = FloatArray(1500 * 2 + 2) { 5f }
        mixer.render(odd, 1500)
        assertEquals(5f, odd[3000], 0f)
        assertTrue(odd.take(3000).all { it.isFinite() && kotlin.math.abs(it) < 1f })
    }

    @Test
    fun releaseCodeCarriesNoDebugUnlock() {
        // The billing core and every gate read Google Play's answer only. No source file may grant the
        // unlock from a build flag, and there is no src/release or src/debug override that could.
        val root = listOf(File("src/main/kotlin"), File("app/src/main/kotlin")).first { it.exists() }
        val sources = root.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.size > 20)
        for (file in sources) {
            val text = file.readText()
            assertFalse("${file.name} reads BuildConfig.DEBUG", text.contains("BuildConfig.DEBUG"))
            assertFalse("${file.name} forces the unlock", Regex("(?i)(debug|force|fake)_?unlock").containsMatchIn(text))
        }
        val srcDir = root.parentFile.parentFile
        assertFalse(File(srcDir, "release").exists())
    }
}
