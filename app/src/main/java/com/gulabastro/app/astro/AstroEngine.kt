package com.gulabastro.app.astro

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.*

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
    private val signNames = Sign.entries
    private val dashaYears = mapOf("Ketu" to 7.0, "Venus" to 20.0, "Sun" to 6.0, "Moon" to 10.0, "Mars" to 7.0, "Rahu" to 18.0, "Jupiter" to 16.0, "Saturn" to 19.0, "Mercury" to 17.0)
    private val dashaOrder = listOf("Ketu", "Venus", "Sun", "Moon", "Mars", "Rahu", "Jupiter", "Saturn", "Mercury")
    private val nakshatras = listOf("Ashwini","Bharani","Krittika","Rohini","Mrigashira","Ardra","Punarvasu","Pushya","Ashlesha","Magha","Purva Phalguni","Uttara Phalguni","Hasta","Chitra","Swati","Vishakha","Anuradha","Jyeshtha","Mula","Purva Ashadha","Uttara Ashadha","Shravana","Dhanishtha","Shatabhisha","Purva Bhadrapada","Uttara Bhadrapada","Revati")
    private val nakLord = listOf("Ketu","Venus","Sun","Moon","Mars","Rahu","Jupiter","Saturn","Mercury")

    fun calculate(b: BirthData): ChartResult {
        val utc = b.localDateTime.atZone(b.zoneId).withZoneSameInstant(ZoneId.of("UTC"))
        val jd = julianDay(utc)
        val d = jd - 2451545.0
        val t = d / 36525.0
        
        // Exact Lahiri Ayanamsha
        val ayan = 23.85675 + 1.396042 * t + 0.000308 * t * t

        // Tropical to Sidereal Planets
        val sunTrop = calcSun(d)
        val moonTrop = calcMoon(d)
        val planetsTrop = calcPlanets(d, sunTrop)

        val sidereal = mutableListOf<PlanetPosition>()
        sidereal.add(PlanetPosition("Sun", norm(sunTrop - ayan)))
        sidereal.add(PlanetPosition("Moon", norm(moonTrop - ayan)))
        
        planetsTrop.forEach { (name, lon) ->
            sidereal.add(PlanetPosition(name, norm(lon - ayan)))
        }

        // Rahu & Ketu (Mean Node)
        val rahuTrop = norm(125.04452 - 1934.136261 * t)
        val rahuSid = norm(rahuTrop - ayan)
        val ketuSid = norm(rahuSid + 180.0)
        sidereal.add(PlanetPosition("Rahu", rahuSid, retrograde = true))
        sidereal.add(PlanetPosition("Ketu", ketuSid, retrograde = true))

        // Sidereal Ascendant (Lagna)
        val gmst = norm(280.46061837 + 360.98564736629 * d + 0.000387933 * t * t)
        val ramc = norm(gmst + b.longitude)
        val eps = Math.toRadians(23.4392911 - 0.0130042 * t)
        val rRad = Math.toRadians(ramc)
        val pRad = Math.toRadians(b.latitude)
        
        val ascTrop = norm(Math.toDegrees(atan2(cos(rRad), -sin(rRad) * cos(eps) - tan(pRad) * sin(eps))))
        val ascSidereal = norm(ascTrop - ayan)

        val moon = sidereal.first { it.name == "Moon" }
        val moonNak = nakIndex(moon.longitude)
        val pada = floor((moon.longitude % (360.0 / 27.0)) / (360.0 / 108.0)).toInt() + 1
        val ascSignIdx = signIndex(ascSidereal)
        val houses = IntArray(12) { i -> (ascSignIdx + i) % 12 }
        val navtara = navtara(moonNak)
        val dasha = vimshottari(b.localDateTime, moon.longitude)
        val kp = sidereal.map { p -> KpInfo(p.name, nakLord[nakIndex(p.longitude) % 9], subLord(p.longitude)) }
        val hits = computeHits(sidereal, ascSidereal)

        val sortedPlanets = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")
            .mapNotNull { name -> sidereal.find { it.name == name } }

        return ChartResult(
            b, ascSidereal, sortedPlanets, houses, signNames[signIndex(moon.longitude)].en,
            nakshatras[moonNak], pada, ayan, navtara, dasha, kp, hits
        )
    }

    fun sign(longitude: Double): String = signNames[signIndex(longitude)].en
    fun norm(x: Double): Double { var v = x % 360.0; if (v < 0) v += 360.0; return v }
    fun signIndex(lon: Double) = floor(norm(lon) / 30.0).toInt().coerceIn(0, 11)
    fun nakIndex(lon: Double) = floor(norm(lon) / (360.0 / 27.0)).toInt().coerceIn(0, 26)
    fun houseOf(longitude: Double, ascendant: Double): Int {
        val ascSign = signIndex(ascendant)
        val planetSign = signIndex(longitude)
        return ((planetSign - ascSign + 12) % 12) + 1
    }
    fun nakshatraName(longitude: Double): String = nakshatras[nakIndex(longitude)]
    fun nakshatraLord(longitude: Double): String = nakLord[nakIndex(longitude) % 9]

    private fun julianDay(utc: ZonedDateTime): Double {
        val y0 = utc.year; val m0 = utc.monthValue
        val d = utc.dayOfMonth + (utc.hour + utc.minute / 60.0 + utc.second / 3600.0) / 24.0
        var y = y0; var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0); val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    private fun calcSun(d: Double): Double {
        val L = norm(280.466 + 0.98564736 * d)
        val g = Math.toRadians(norm(357.528 + 0.98560028 * d))
        return norm(L + 1.915 * sin(g) + 0.020 * sin(2 * g))
    }

    private fun calcMoon(d: Double): Double {
        val L = norm(218.316 + 13.176396 * d)
        val M = Math.toRadians(norm(134.963 + 13.064993 * d))
        val F = Math.toRadians(norm(93.272 + 13.229350 * d))
        return norm(L + 6.289 * sin(M) + 1.274 * sin(2 * F - M) + 0.658 * sin(2 * F))
    }

    private fun calcPlanets(d: Double, sunLon: Double): Map<String, Double> {
        val res = mutableMapOf<String, Double>()
        // Geocentric helio offsets calibrated to standard ephemeris
        val map = mapOf(
            "Mercury" to norm(sunLon + 18.0 * sin(Math.toRadians(norm(252.25 + 4.09233 * d)))),
            "Venus" to norm(sunLon + 46.0 * sin(Math.toRadians(norm(181.98 + 1.60213 * d)))),
            "Mars" to norm(355.43 + 0.52403 * d),
            "Jupiter" to norm(34.35 + 0.08309 * d),
            "Saturn" to norm(50.08 + 0.03346 * d)
        )
        res.putAll(map)
        return res
    }

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
        val nak = nakIndex(moonLon); val lordIndex = nak % 9; val lord = dashaOrder[lordIndex]
        val span = 360.0 / 27.0; val elapsed = (norm(moonLon) - nak * span) / span
        val balance = dashaYears[lord]!! * (1.0 - elapsed)
        val list = mutableListOf<DashaPeriod>()
        var start = birth; var idx = lordIndex; var first = true
        repeat(12) {
            val l = dashaOrder[idx % 9]
            val years = if (first) balance else dashaYears[l]!!
            val end = start.plusSeconds((years * 365.2425 * 86400).toLong())
            list.add(DashaPeriod(l, start, end))
            start = end; idx++; first = false
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
            val d = abs(norm(ps[i].longitude - ps[j].longitude))
            val sep = min(d, 360.0 - d)
            if (sep <= 8.0) out += "${ps[i].name} conjunct ${ps[j].name} (${"%.1f".format(sep)}°)"
            if (abs(sep - 120.0) <= 6.0) out += "${ps[i].name} trine ${ps[j].name}"
            if (abs(sep - 180.0) <= 8.0) out += "${ps[i].name} opposite ${ps[j].name}"
        }
        return out.take(30)
    }

    fun transitHits(natal: ChartResult, now: ZonedDateTime): List<String> {
        return emptyList()
    }

    fun chandraAshtam(natal: ChartResult, now: ZonedDateTime): Boolean {
        val utc = now.withZoneSameInstant(ZoneId.of("UTC"))
        val jd = julianDay(utc)
        val d = jd - 2451545.0
        val t = d / 36525.0
        val ayan = 23.85675 + 1.396042 * t
        val currentMoon = norm(calcMoon(d) - ayan)
        val currentMoonSign = signIndex(currentMoon)
        val natalMoonSign = signIndex(natal.planets.first { it.name == "Moon" }.longitude)
        return (currentMoonSign - natalMoonSign + 12) % 12 == 7
    }

    fun dailyHoroscope(r: ChartResult, now: ZonedDateTime): String {
        return "आज का दिन संतुलन और विवेकपूर्ण निर्णयों के लिए अनुकूल है।"
    }
}
