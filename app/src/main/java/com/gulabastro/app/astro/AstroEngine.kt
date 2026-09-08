package com.gulabastro.app.astro

import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

data class BirthData(val name: String, val localDateTime: LocalDateTime, val latitude: Double, val longitude: Double, val zoneId: ZoneId)
data class PlanetPosition(val name: String, val longitude: Double, val latitude: Double = 0.0, val retrograde: Boolean = false)
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

data class Orbital(val N: Double, val Ndot: Double, val i: Double, val idot: Double, val w: Double, val wdot: Double,
                  val a: Double, val adot: Double, val e: Double, val edot: Double, val M: Double, val Mdot: Double)

object AstroEngine {
    private val signNames = Sign.entries
    private val planets = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")
    private val dashaYears = mapOf("Ketu" to 7.0, "Venus" to 20.0, "Sun" to 6.0, "Moon" to 10.0, "Mars" to 7.0, "Rahu" to 18.0, "Jupiter" to 16.0, "Saturn" to 19.0, "Mercury" to 17.0)
    private val dashaOrder = listOf("Ketu", "Venus", "Sun", "Moon", "Mars", "Rahu", "Jupiter", "Saturn", "Mercury")
    private val nakshatras = listOf("Ashwini","Bharani","Krittika","Rohini","Mrigashira","Ardra","Punarvasu","Pushya","Ashlesha","Magha","Purva Phalguni","Uttara Phalguni","Hasta","Chitra","Swati","Vishakha","Anuradha","Jyeshtha","Mula","Purva Ashadha","Uttara Ashadha","Shravana","Dhanishtha","Shatabhisha","Purva Bhadrapada","Uttara Bhadrapada","Revati")
    private val nakLord = listOf("Ketu","Venus","Sun","Moon","Mars","Rahu","Jupiter","Saturn","Mercury")

    fun calculate(b: BirthData): ChartResult {
        val utc = b.localDateTime.atZone(b.zoneId).withZoneSameInstant(ZoneOffset.UTC)
        val jd = julianDay(utc)
        val ayan = lahiriAyanamsha(jd)
        val tropical = heliocentricToGeocentric(jd)
        val sidereal = tropical.map { p -> p.copy(longitude = norm(p.longitude - ayan)) }
        val moon = sidereal.first { it.name == "Moon" }
        val lst = localSiderealTime(jd, b.longitude)
        val ascTropical = ascendant(lst, b.latitude)
        val ascSidereal = norm(ascTropical - ayan)
        val moonNak = nakIndex(moon.longitude)
        val pada = floor((moon.longitude % (13.3333333333)) / (3.3333333333)).toInt() + 1
        val houses = IntArray(12) { i -> ((signIndex(ascSidereal) + i) % 12) }
        val navtara = navtara(moonNak)
        val dasha = vimshottari(b.localDateTime, moon.longitude)
        val kp = sidereal.map { p -> KpInfo(p.name, nakLord[nakIndex(p.longitude)], subLord(p.longitude)) }
        val hits = computeHits(sidereal, ascSidereal)
        return ChartResult(b, ascSidereal, sidereal, houses, signNames[signIndex(moon.longitude)].en, nakshatras[moonNak], pada, ayan, navtara, dasha, kp, hits)
    }

    fun sign(longitude: Double): String = signNames[signIndex(longitude)].en
    fun signHi(longitude: Double): String = signNames[signIndex(longitude)].hi
    fun norm(x: Double): Double { var v = x % 360.0; if (v < 0) v += 360.0; return v }
    private fun signIndex(lon: Double) = floor(norm(lon) / 30.0).toInt().coerceIn(0, 11)
    private fun nakIndex(lon: Double) = floor(norm(lon) / (360.0 / 27.0)).toInt().coerceIn(0, 26)

    private fun julianDay(utc: ZonedDateTime): Double {
        val y0 = utc.year; val m0 = utc.monthValue; val d = utc.dayOfMonth + (utc.hour + utc.minute / 60.0 + utc.second / 3600.0 + utc.nano / 3.6e12) / 24.0
        var y = y0; var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0); val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    // Meeus/Schlyter-style low-order planetary model. Suitable for an offline astrology engine; Swiss Ephemeris can be swapped in via an adapter.
    private fun heliocentricToGeocentric(jd: Double): List<PlanetPosition> {
        val d = jd - 2451543.5
        val earth = orbital("Earth", d)
        val epos = heliocentric(earth, d)
        val result = mutableListOf<PlanetPosition>()
        for (name in listOf("Mercury","Venus","Mars","Jupiter","Saturn")) {
            val o = orbital(name, d); val xyz = heliocentric(o, d)
            val dx = xyz[0] - epos[0]; val dy = xyz[1] - epos[1]; val dz = xyz[2] - epos[2]
            val lon = norm(Math.toDegrees(atan2(dy, dx)))
            val lat = Math.toDegrees(atan2(dz, sqrt(dx*dx + dy*dy)))
            result += PlanetPosition(name, lon, lat)
        }
        val sunLon = norm(Math.toDegrees(atan2(-epos[1], -epos[0])))
        result += PlanetPosition("Sun", sunLon)
        val moon = moonPosition(d)
        result += PlanetPosition("Moon", moon)
        val rahu = norm(125.04452 - 0.0529538083 * d)
        result += PlanetPosition("Rahu", rahu, retrograde = true)
        result += PlanetPosition("Ketu", norm(rahu + 180.0), retrograde = true)
        return result.sortedBy { planets.indexOf(it.name) }
    }

    private fun orbital(name: String, d: Double): Orbital = when(name) {
        "Mercury" -> Orbital(48.3313,3.24587E-5,7.0047,5.00E-8,29.1241,1.01444E-5,0.387098,0,0.205635,5.59E-10,168.6562,4.0923344368)
        "Venus" -> Orbital(76.6799,2.46590E-5,3.3946,2.75E-8,54.8910,1.38374E-5,0.72333,0,0.006773,-1.302E-9,48.0052,1.6021302244)
        "Earth" -> Orbital(0,0,0,0,282.9404,4.70935E-5,1.0,0,0.016709,-1.151E-9,356.0470,0.9856002585)
        "Mars" -> Orbital(49.5574,2.11081E-5,1.8497,-1.78E-8,286.5016,2.92961E-5,1.523688,0,0.093405,2.516E-9,18.6021,0.5240207766)
        "Jupiter" -> Orbital(100.4542,2.76854E-5,1.3030,-1.557E-7,273.8777,1.64505E-5,5.20256,0,0.048498,4.469E-9,19.8950,0.0830853001)
        else -> Orbital(113.6634,2.38980E-5,2.4886,-1.081E-7,339.3939,2.97661E-5,9.55475,0,0.055546,-9.499E-9,316.9670,0.0334442282)
    }

    private fun heliocentric(o: Orbital, d: Double): DoubleArray {
        val N = Math.toRadians(o.N + o.Ndot*d); val i = Math.toRadians(o.i + o.idot*d); val w = Math.toRadians(o.w + o.wdot*d)
        val a = o.a + o.adot*d; val e = o.e + o.edot*d; val M = Math.toRadians(o.M + o.Mdot*d)
        var E = M
        repeat(8) { E -= (E - e*sin(E) - M) / (1 - e*cos(E)) }
        val xv = a*(cos(E)-e); val yv = a*(sqrt(1-e*e)*sin(E)); val v = atan2(yv,xv); val r = hypot(xv,yv)
        val xh = r*(cos(N)*cos(v+w)-sin(N)*sin(v+w)*cos(i)); val yh = r*(sin(N)*cos(v+w)+cos(N)*sin(v+w)*cos(i)); val zh = r*(sin(v+w)*sin(i))
        return doubleArrayOf(xh,yh,zh)
    }

    private fun moonPosition(d: Double): Double {
        val N = Math.toRadians(norm(125.1228 - 0.0529538083*d)); val i = Math.toRadians(5.1454); val w = Math.toRadians(norm(318.0634 + 0.1643573223*d)); val a=60.2666; val e=0.0549; val M=Math.toRadians(norm(115.3654 + 13.0649929509*d))
        var E=M; repeat(8){E -= (E-e*sin(E)-M)/(1-e*cos(E))}; val xv=a*(cos(E)-e); val yv=a*sqrt(1-e*e)*sin(E); val v=atan2(yv,xv); val r=hypot(xv,yv)
        val xh=r*(cos(N)*cos(v+w)-sin(N)*sin(v+w)*cos(i)); val yh=r*(sin(N)*cos(v+w)+cos(N)*sin(v+w)*cos(i)); return norm(Math.toDegrees(atan2(yh,xh)))
    }

    private fun lahiriAyanamsha(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        return 23.85675 + 1.396042 * t + 0.000308 * t*t
    }

    private fun localSiderealTime(jd: Double, longitude: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val gst = norm(280.46061837 + 360.98564736629*(jd-2451545.0) + 0.000387933*t*t - t*t*t/38710000.0)
        return norm(gst + longitude)
    }

    private fun ascendant(lstDeg: Double, latDeg: Double): Double {
        val e = Math.toRadians(23.4393); val theta=Math.toRadians(lstDeg); val phi=Math.toRadians(latDeg)
        return norm(Math.toDegrees(atan2(-cos(theta), sin(theta)*cos(e)+tan(phi)*sin(e))))
    }

    private fun navtara(nak: Int): List<String> {
        val names = listOf("Janma Tara","Sampat Tara","Vipat Tara","Kshema Tara","Pratyari Tara","Sadhaka Tara","Naidhana Tara","Mitra Tara","Param Mitra Tara")
        return names.mapIndexed { i, n -> "$n: ${nakshatras[(nak+i)%27]}" }
    }

    private fun vimshottari(birth: LocalDateTime, moonLon: Double): List<DashaPeriod> {
        val nak = nakIndex(moonLon); val lordIndex=nak%9; val lord=dashaOrder[lordIndex]; val span=13.3333333333; val elapsed=(moonLon-nak*span)/span
        val balance=dashaYears[lord]!!*(1.0-elapsed); val list=mutableListOf<DashaPeriod>(); var start=birth
        var idx=lordIndex; var first=true
        repeat(18) {
            val l=dashaOrder[idx%9]; val years=if(first) balance else dashaYears[l]!!; val end=start.plusSeconds((years*365.2425*86400).toLong()); list += DashaPeriod(l,start,end); start=end; idx++; first=false
        }
        return list
    }

    private fun subLord(lon: Double): String {
        val frac=(norm(lon) % (360.0/27.0))/(360.0/27.0); val total=dashaYears.values.sum(); var x=frac*total
        for(l in dashaOrder){x-=dashaYears[l]!!; if(x<=0)return l}; return dashaOrder.last()
    }

    private fun computeHits(ps: List<PlanetPosition>, asc: Double): List<String> {
        val out=mutableListOf<String>()
        for(i in ps.indices) for(j in i+1 until ps.size){
            val d=abs(norm(ps[i].longitude-ps[j].longitude)); val sep=min(d,360-d)
            if(sep<=8) out += "${ps[i].name} conjunct ${ps[j].name} (${"%.1f".format(sep)}°)"
            val aspect=abs(sep-120); if(aspect<=6) out += "${ps[i].name} trine ${ps[j].name}"
            val opp=abs(sep-180); if(opp<=8) out += "${ps[i].name} opposite ${ps[j].name}"
        }
        ps.forEach { p -> val house=(floor(norm(p.longitude-asc)/30.0).toInt()+1).coerceIn(1,12); out += "${p.name} → House $house / ${sign(p.longitude)}" }
        return out.take(40)
    }
    fun houseOf(longitude: Double, ascendant: Double): Int = (floor(norm(longitude - ascendant) / 30.0).toInt() + 1).coerceIn(1, 12)
    fun nakshatraName(longitude: Double): String = nakshatras[nakIndex(longitude)]
    fun nakshatraLord(longitude: Double): String = nakLord[nakIndex(longitude)]
    fun currentTransit(now: ZonedDateTime): List<PlanetPosition> {
        val utc = now.withZoneSameInstant(ZoneOffset.UTC)
        val jd = julianDay(utc)
        val ayan = lahiriAyanamsha(jd)
        return heliocentricToGeocentric(jd).map { it.copy(longitude = norm(it.longitude - ayan)) }
    }
    fun transitHits(natal: ChartResult, now: ZonedDateTime): List<String> {
        val transit = currentTransit(now)
        val out = mutableListOf<String>()
        for (t in transit) for (n in natal.planets) {
            val sep0 = abs(norm(t.longitude - n.longitude)); val sep = min(sep0, 360.0-sep0)
            if (sep <= 3.0) out += "${t.name} transit conjunct natal ${n.name} (${String.format("%.1f",sep)}°)"
            if (abs(sep-120.0) <= 4.0) out += "${t.name} transit trine natal ${n.name}"
            if (abs(sep-180.0) <= 4.0) out += "${t.name} transit opposite natal ${n.name}"
        }
        return out.take(50)
    }
    fun chandraAshtam(natal: ChartResult, now: ZonedDateTime): Boolean {
        val moon = currentTransit(now).first { it.name == "Moon" }
        val natalMoonSign = signIndex(natal.planets.first { it.name == "Moon" }.longitude)
        val transitSign = signIndex(moon.longitude)
        return (transitSign - natalMoonSign + 12) % 12 == 7
    }
    fun dailyHoroscope(r: ChartResult, now: ZonedDateTime): String {
        val moon = currentTransit(now).first { it.name == "Moon" }
        val house = houseOf(moon.longitude, r.ascendant)
        return when(house) { 1 -> "आज self-confidence और नए decisions के लिए अच्छा दिन है।"; 2 -> "धन और परिवार से जुड़े विषयों में practical रहें।"; 3 -> "Communication, travel और skill-building पर focus करें।"; 4 -> "घर-परिवार और emotional balance को priority दें।"; 5 -> "Creativity, romance और learning में initiative लें।"; 6 -> "Routine, health habits और pending work पूरा करें।"; 7 -> "Partnership और relationships में खुलकर बात करें।"; 8 -> "Risky decisions से बचें और documents ध्यान से देखें।"; 9 -> "Learning, travel और spiritual activities लाभ दे सकती हैं।"; 10 -> "Career में visibility और responsibility बढ़ सकती है।"; 11 -> "Network और gains से जुड़े opportunities पर ध्यान दें।"; else -> "Rest, reflection और planning के लिए समय निकालें।" }
    }

}
