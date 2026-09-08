package com.gulabastro.app.astro

import swisseph.SweConst
import swisseph.SweDate
import swisseph.SwissEph
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.floor

data class BirthData(val name: String, val localDateTime: LocalDateTime, val latitude: Double, val longitude: Double, val zoneId: ZoneId)
data class PlanetPosition(val name: String, val longitude: Double, val speed: Double = 0.0, val retrograde: Boolean = false)
data class ChartResult(
    val birth: BirthData,
    val ascendant: Double,
    val planets: List<PlanetPosition>,
    val houses: IntArray,
    val moonSign: String,
    val moonNakshatra: String,
    val nakshatraPada: Int,
    val ayanamsha: Double,
    val navtara: List<String>,
    val dasha: List<DashaPeriod>,
    val kp: List<KpInfo>,
    val hits: List<String>
)
data class DashaPeriod(val lord: String, val start: LocalDateTime, val end: LocalDateTime)
data class KpInfo(val planet: String, val starLord: String, val subLord: String)

enum class Sign(val hi: String, val en: String) {
    ARIES("मेष", "Aries"), TAURUS("वृषभ", "Taurus"), GEMINI("मिथुन", "Gemini"), CANCER("कर्क", "Cancer"),
    LEO("सिंह", "Leo"), VIRGO("कन्या", "Virgo"), LIBRA("तुला", "Libra"), SCORPIO("वृश्चिक", "Scorpio"),
    SAGITTARIUS("धनु", "Sagittarius"), CAPRICORN("मकर", "Capricorn"), AQUARIUS("कुंभ", "Aquarius"), PISCES("मीन", "Pisces")
}

object AstroEngine {
    private val swe = SwissEph()
    private val signNames = Sign.entries
    private val dashaYears = mapOf("Ketu" to 7.0, "Venus" to 20.0, "Sun" to 6.0, "Moon" to 10.0, "Mars" to 7.0, "Rahu" to 18.0, "Jupiter" to 16.0, "Saturn" to 19.0, "Mercury" to 17.0)
    private val dashaOrder = listOf("Ketu", "Venus", "Sun", "Moon", "Mars", "Rahu", "Jupiter", "Saturn", "Mercury")
    private val nakshatras = listOf("Ashwini","Bharani","Krittika","Rohini","Mrigashira","Ardra","Punarvasu","Pushya","Ashlesha","Magha","Purva Phalguni","Uttara Phalguni","Hasta","Chitra","Swati","Vishakha","Anuradha","Jyeshtha","Mula","Purva Ashadha","Uttara Ashadha","Shravana","Dhanishtha","Shatabhisha","Purva Bhadrapada","Uttara Bhadrapada","Revati")
    private val nakLord = listOf("Ketu","Venus","Sun","Moon","Mars","Rahu","Jupiter","Saturn","Mercury")

    init {
        // Lahiri Ayanamsha set (Standard Indian Vedic Astrology)
        swe.swe_set_sid_mode(SweConst.SE_SIDM_LAHIRI, 0.0, 0.0)
    }

    fun calculate(b: BirthData): ChartResult {
        val utc = b.localDateTime.atZone(b.zoneId).withZoneSameInstant(ZoneId.of("UTC"))
        val hourDecimal = utc.hour + (utc.minute / 60.0) + (utc.second / 3600.0)
        val sweDate = SweDate(utc.year, utc.monthValue, utc.dayOfMonth, hourDecimal)
        val tjdUt = sweDate.julDay

        val ayanamsha = swe.swe_get_ayanamsa_ut(tjdUt)

        val cusps = DoubleArray(13)
        val ascmc = DoubleArray(10)
        swe.swe_houses_ex(tjdUt, SweConst.SEFLG_SIDEREAL, b.latitude, b.longitude, 'P'.code, cusps, ascmc)
        val ascSidereal = ascmc[0]

        val planetIds = listOf(
            SweConst.SE_SUN to "Sun",
            SweConst.SE_MOON to "Moon",
            SweConst.SE_MARS to "Mars",
            SweConst.SE_MERCURY to "Mercury",
            SweConst.SE_JUPITER to "Jupiter",
            SweConst.SE_VENUS to "Venus",
            SweConst.SE_SATURN to "Saturn",
            SweConst.SE_MEAN_NODE to "Rahu"
        )

        val planetList = mutableListOf<PlanetPosition>()
        val xx = DoubleArray(6)
        val serr = StringBuffer()
        val flags = SweConst.SEFLG_SIDEREAL or SweConst.SEFLG_SPEED

        for ((id, name) in planetIds) {
            swe.swe_calc_ut(tjdUt, id, flags, xx, serr)
            val lon = xx[0]
            val speed = xx[3]
            planetList.add(PlanetPosition(name, lon, speed, speed < 0.0))
        }

        // Ketu is exactly 180° opposite to Rahu
        val rahu = planetList.first { it.name == "Rahu" }
        val ketuLon = (rahu.longitude + 180.0) % 360.0
        planetList.add(PlanetPosition("Ketu", ketuLon, rahu.speed, true))

        val moon = planetList.first { it.name == "Moon" }
        val moonNak = nakIndex(moon.longitude)
        val pada = floor((moon.longitude % 13.3333333333) / 3.3333333333).toInt() + 1
        val houses = IntArray(12) { i -> (signIndex(ascSidereal) + i) % 12 }
        val dasha = vimshottari(b.localDateTime, moon.longitude)
        val kp = planetList.map { p -> KpInfo(p.name, nakLord[nakIndex(p.longitude) % 9], subLord(p.longitude)) }
        val hits = computeHits(planetList, ascSidereal)

        return ChartResult(
            birth = b,
            ascendant = ascSidereal,
            planets = planetList,
            houses = houses,
            moonSign = signNames[signIndex(moon.longitude)].en,
            moonNakshatra = nakshatras[moonNak],
            nakshatraPada = pada,
            ayanamsha = ayanamsha,
            navtara = navtara(moonNak),
            dasha = dasha,
            kp = kp,
            hits = hits
        )
    }

    fun sign(longitude: Double): String = signNames[signIndex(longitude)].en
    fun norm(x: Double): Double { var v = x % 360.0; if (v < 0) v += 360.0; return v }
    private fun signIndex(lon: Double) = floor(norm(lon) / 30.0).toInt().coerceIn(0, 11)
    private fun nakIndex(lon: Double) = floor(norm(lon) / (360.0 / 27.0)).toInt().coerceIn(0, 26)
    fun houseOf(longitude: Double, ascendant: Double): Int = (floor(norm(longitude - ascendant) / 30.0).toInt() + 1).coerceIn(1, 12)
    fun nakshatraName(longitude: Double): String = nakshatras[nakIndex(longitude)]
    fun nakshatraLord(longitude: Double): String = nakLord[nakIndex(longitude) % 9]

    private fun subLord(lon: Double): String {
        val frac = (norm(lon) % (360.0 / 27.0)) / (360.0 / 27.0)
        val total = dashaYears.values.sum()
        var x = frac * total
        for (l in dashaOrder) {
            x -= dashaYears[l]!!
            if (x <= 0) return l
        }
        return dashaOrder.last()
    }

    private fun vimshottari(birth: LocalDateTime, moonLon: Double): List<DashaPeriod> {
        val nak = nakIndex(moonLon)
        val lordIndex = nak % 9
        val lord = dashaOrder[lordIndex]
        val span = 13.3333333333
        val elapsed = (moonLon - nak * span) / span
        val balance = dashaYears[lord]!! * (1.0 - elapsed)
        val list = mutableListOf<DashaPeriod>()
        var start = birth
        var idx = lordIndex
        var first = true
        repeat(12) {
            val l = dashaOrder[idx % 9]
            val years = if (first) balance else dashaYears[l]!!
            val end = start.plusSeconds((years * 365.2425 * 86400).toLong())
            list.add(DashaPeriod(l, start, end))
            start = end
            idx++
            first = false
        }
        return list
    }

    private fun navtara(nak: Int): List<String> {
        val names = listOf("Janma Tara","Sampat Tara","Vipat Tara","Kshema Tara","Pratyari Tara","Sadhaka Tara","Naidhana Tara","Mitra Tara","Param Mitra Tara")
        return names.mapIndexed { i, n -> "$n: ${nakshatras[(nak + i) % 27]}" }
    }

    private fun computeHits(ps: List<PlanetPosition>, asc: Double): List<String> {
        val out = mutableListOf<String>()
        for (i in ps.indices) for (j in i + 1 until ps.size) {
            val d = kotlin.math.abs(norm(ps[i].longitude - ps[j].longitude))
            val sep = kotlin.math.min(d, 360.0 - d)
            if (sep <= 8.0) out += "${ps[i].name} conjunct ${ps[j].name} (${"%.1f".format(sep)}°)"
            if (kotlin.math.abs(sep - 120.0) <= 6.0) out += "${ps[i].name} trine ${ps[j].name}"
            if (kotlin.math.abs(sep - 180.0) <= 8.0) out += "${ps[i].name} opposite ${ps[j].name}"
        }
        return out.take(30)
    }

    fun transitHits(natal: ChartResult, now: ZonedDateTime): List<String> {
        val utc = now.withZoneSameInstant(ZoneId.of("UTC"))
        val hourDecimal = utc.hour + (utc.minute / 60.0) + (utc.second / 3600.0)
        val sweDate = SweDate(utc.year, utc.monthValue, utc.dayOfMonth, hourDecimal)
        val xx = DoubleArray(6)
        val flags = SweConst.SEFLG_SIDEREAL
        val out = mutableListOf<String>()

        val transits = listOf(
            SweConst.SE_SUN to "Sun", SweConst.SE_MOON to "Moon",
            SweConst.SE_MARS to "Mars", SweConst.SE_JUPITER to "Jupiter", SweConst.SE_SATURN to "Saturn"
        ).map { (id, name) ->
            swe.swe_calc_ut(sweDate.julDay, id, flags, xx, StringBuffer())
            name to xx[0]
        }

        for ((tName, tLon) in transits) for (n in natal.planets) {
            val d = kotlin.math.abs(norm(tLon - n.longitude))
            val sep = kotlin.math.min(d, 360.0 - d)
            if (sep <= 3.0) out += "$tName transit conjunct natal ${n.name} (${String.format("%.1f", sep)}°)"
        }
        return out
    }

    fun chandraAshtam(natal: ChartResult, now: ZonedDateTime): Boolean {
        val utc = now.withZoneSameInstant(ZoneId.of("UTC"))
        val hourDecimal = utc.hour + (utc.minute / 60.0) + (utc.second / 3600.0)
        val sweDate = SweDate(utc.year, utc.monthValue, utc.dayOfMonth, hourDecimal)
        val xx = DoubleArray(6)
        swe.swe_calc_ut(sweDate.julDay, SweConst.SE_MOON, SweConst.SEFLG_SIDEREAL, xx, StringBuffer())
        val currentMoonSign = signIndex(xx[0])
        val natalMoonSign = signIndex(natal.planets.first { it.name == "Moon" }.longitude)
        return (currentMoonSign - natalMoonSign + 12) % 12 == 7
    }

    fun dailyHoroscope(r: ChartResult, now: ZonedDateTime): String {
        val utc = now.withZoneSameInstant(ZoneId.of("UTC"))
        val hourDecimal = utc.hour + (utc.minute / 60.0) + (utc.second / 3600.0)
        val sweDate = SweDate(utc.year, utc.monthValue, utc.dayOfMonth, hourDecimal)
        val xx = DoubleArray(6)
        swe.swe_calc_ut(sweDate.julDay, SweConst.SE_MOON, SweConst.SEFLG_SIDEREAL, xx, StringBuffer())
        val house = houseOf(xx[0], r.ascendant)
        return when (house) {
            1 -> "आज self-confidence और नए decisions के लिए अच्छा दिन है।"
            2 -> "धन और परिवार से जुड़े विषयों में practical रहें।"
            3 -> "Communication, travel और skill-building पर focus करें।"
            4 -> "घर-परिवार और emotional balance को priority दें।"
            5 -> "Creativity, romance और learning में initiative लें।"
            6 -> "Routine, health habits और pending work पूरा करें।"
            7 -> "Partnership और relationships में खुलकर बात करें।"
            8 -> "Risky decisions से बचें और documents ध्यान से देखें।"
            9 -> "Learning, travel और spiritual activities लाभ दे सकती हैं।"
            10 -> "Career में visibility और responsibility बढ़ सकती है।"
            11 -> "Network और gains से जुड़े opportunities पर ध्यान दें।"
            else -> "Rest, reflection और planning के लिए समय निकालें।"
        }
    }
}
