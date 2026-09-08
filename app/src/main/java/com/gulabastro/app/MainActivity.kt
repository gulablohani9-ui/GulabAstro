package com.gulabastro.app

import android.app.*
import android.content.*
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.gulabastro.app.astro.*
import java.io.File
import java.io.FileOutputStream
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() { override fun onCreate(s: Bundle?) { super.onCreate(s); setContent { GulabAstroApp() } } }

data class City(val lat: Double, val lon: Double, val zone: String = "Asia/Kolkata")

@Composable fun GulabAstroApp() {
    var hi by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }; var date by remember { mutableStateOf("1990-01-01") }
    var time by remember { mutableStateOf("12:00") }; var place by remember { mutableStateOf("Jaipur") }
    var chart by remember { mutableStateOf<ChartResult?>(null) }; var error by remember { mutableStateOf("") }
    val ctx=LocalContext.current
    MaterialTheme {
        Scaffold(topBar={TopAppBar(title={Text("Gulab Astro")},actions={TextButton({hi=!hi}){Text(if(hi)"EN" else "हिं")}})}) { pad ->
            LazyColumn(Modifier.padding(pad).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item { Text(if(hi)"संपूर्ण वैदिक ज्योतिष" else "Complete Vedic Astrology",style=MaterialTheme.typography.headlineSmall) }
                item { Field(name,{name=it},if(hi)"नाम" else "Name") }
                item { Field(date,{date=it},if(hi)"जन्म तारीख (YYYY-MM-DD)" else "Birth date (YYYY-MM-DD)") }
                item { Field(time,{time=it},if(hi)"जन्म समय (HH:MM)" else "Birth time (HH:MM)") }
                item { Field(place,{place=it},if(hi)"जन्म स्थान" else "Birth place") }
                item { Text(if(hi)"Timezone: India (Asia/Kolkata). Accurate coordinates are used for supported cities." else "Timezone: India (Asia/Kolkata). Accurate coordinates are used for supported cities.",style=MaterialTheme.typography.bodySmall) }
                item { Button({ try { val dt=LocalDateTime.parse("$date $time",DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); val c=city(place); chart=AstroEngine.calculate(BirthData(name.ifBlank{"User"},dt,c.lat,c.lon,ZoneId.of(c.zone))); error="" } catch(e:Exception){error=e.message?:"Invalid input"} },Modifier.fillMaxWidth()){Text(if(hi)"कुंडली बनाएं" else "Generate Kundli")} }
                if(error.isNotBlank()) item { Text(error,color=MaterialTheme.colorScheme.error) }
                chart?.let { r ->
                    item { CardBox(if(hi)"Kundli / Birth Chart" else "Kundli / Birth Chart") { Text("Lagna: ${AstroEngine.sign(r.ascendant)} ${fmt(r.ascendant)}°"); Text("Moon: ${r.moonSign} • ${r.moonNakshatra} • Pada ${r.nakshatraPada}"); Text("Ayanamsha: ${fmt(r.ayanamsha)}°") } }
                    item { CardBox(if(hi)"Navgraha" else "Navgraha") { r.planets.forEach { Text("${it.name}: ${fmt(it.longitude)}° • ${AstroEngine.sign(it.longitude)} • House ${AstroEngine.houseOf(it.longitude,r.ascendant)}${if(it.retrograde)" ℞" else ""}") } } }
                    item { CardBox("Navtara Chakra") { r.navtara.forEach{Text(it)} } }
                    item { CardBox("Dasha / Mahadasha") { r.dasha.take(12).forEach{Text("${it.lord}: ${it.start.toLocalDate()} → ${it.end.toLocalDate()}")} } }
                    item { CardBox("KP Astrology") { r.kp.forEach{Text("${it.planet}: Star Lord ${it.starLord} • Sub Lord ${it.subLord}")} } }
                    item { CardBox(if(hi)"Planet-to-Planet / House Hits" else "Planet-to-Planet / House Hits") { r.hits.forEach{Text("• $it")} } }
                    item { CardBox(if(hi)"Transit" else "Transit") { val hits=AstroEngine.transitHits(r,ZonedDateTime.now()); if(hits.isEmpty()) Text(if(hi)"अभी major transit hit नहीं मिला।" else "No major transit hit found now.") else hits.forEach{Text("• $it")} } }
                    item { CardBox(if(hi)"Daily Horoscope" else "Daily Horoscope") { Text(AstroEngine.dailyHoroscope(r,ZonedDateTime.now())) } }
                    item { CardBox(if(hi)"Love / Marriage" else "Love / Marriage") { Text(if(hi)"7th-house, Venus और relationship significators को साथ में देखें।" else "Review the 7th house, Venus and relationship significators together.") } }
                    item { CardBox(if(hi)"Career & Finance" else "Career & Finance") { Text(if(hi)"10th/2nd/11th houses, their lords and current transits को साथ देखें।" else "Review 10th/2nd/11th houses, their lords and current transits together.") } }
                    item { CardBox(if(hi)"Vastu / Remedies" else "Vastu / Remedies") { Text(if(hi)"North-East को साफ रखें, entrance clutter-free रखें; remedies को chart-specific रखें।" else "Keep the North-East clean and entrance clutter-free; keep remedies chart-specific.") } }
                    item { CardBox(if(hi)"Chandrama Astam" else "Chandrama Astam") { val a=AstroEngine.chandraAshtam(r,ZonedDateTime.now()); Text(if(a) (if(hi)"⚠️ अभी चंद्रमा अष्टम स्थिति में है।" else "⚠️ Moon is currently in the 8th sign from natal Moon.") else (if(hi)"अभी चंद्रमा अष्टम नहीं है।" else "Moon is not currently in the 8th sign from natal Moon.")) } }
                    item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({exportPdf(ctx,r)},Modifier.weight(1f)){Text("PDF")};Button({share(ctx,r)},Modifier.weight(1f)){Text(if(hi)"Share" else "Share")}} }
                }
                item { Text(if(hi)"Modules: Kundli • Rashi/Nakshatra • Navgraha • Daily Horoscope • Love/Marriage • Career/Finance • Vastu/Remedies • Dasha/Mahadasha • Navtara • KP • Planet/House Hits • Transit • Chandrama Astam • PDF" else "Modules: Kundli • Rashi/Nakshatra • Navgraha • Daily Horoscope • Love/Marriage • Career/Finance • Vastu/Remedies • Dasha/Mahadasha • Navtara • KP • Planet/House Hits • Transit • Chandrama Astam • PDF",style=MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable private fun Field(v:String,set:(String)->Unit,label:String){OutlinedTextField(v,set,label={Text(label)},modifier=Modifier.fillMaxWidth())}
@Composable private fun CardBox(t:String,content:@Composable ColumnScope.()->Unit){ElevatedCard{Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(t,style=MaterialTheme.typography.titleMedium);content()}}}
private fun fmt(v:Double)="%.2f".format(v)
private fun city(s:String)=when(s.trim().lowercase()){"jaipur"->City(26.9124,75.7873);"delhi"->City(28.6139,77.2090);"mumbai"->City(19.0760,72.8777);"kolkata"->City(22.5726,88.3639);"bengaluru","bangalore"->City(12.9716,77.5946);"chennai"->City(13.0827,80.2707);"ahmedabad"->City(23.0225,72.5714);"lucknow"->City(26.8467,80.9462);"udaipur"->City(24.5854,73.7125);else->City(26.9124,75.7873)}

private fun exportPdf(ctx:Context,r:ChartResult){
    val doc=PdfDocument(); var pageNo=1; var page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create()); var y=40f; val p=Paint().apply{textSize=14f}
    fun line(s:String){ if(y>800f){doc.finishPage(page);pageNo++;page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create());y=40f};page.canvas.drawText(s,28f,y,p);y+=20f }
    line("Gulab Astro - Kundli Report");line("Name: ${r.birth.name}");line("Birth: ${r.birth.localDateTime} (${r.birth.zoneId})");line("Lagna: ${AstroEngine.sign(r.ascendant)} ${fmt(r.ascendant)}°");line("Moon: ${r.moonSign} / ${r.moonNakshatra} / Pada ${r.nakshatraPada}");line("Ayanamsha: ${fmt(r.ayanamsha)}°");line("--- Navgraha ---");r.planets.forEach{line("${it.name}: ${fmt(it.longitude)}° ${AstroEngine.sign(it.longitude)} House ${AstroEngine.houseOf(it.longitude,r.ascendant)}")};line("--- Navtara ---");r.navtara.forEach(::line);line("--- Dasha ---");r.dasha.take(12).forEach{line("${it.lord}: ${it.start.toLocalDate()} -> ${it.end.toLocalDate()}")};line("--- KP ---");r.kp.forEach{line("${it.planet}: ${it.starLord} / ${it.subLord}")};line("--- Hits ---");r.hits.forEach(::line);line("--- Transit ---");AstroEngine.transitHits(r,ZonedDateTime.now()).forEach(::line);line("--- Horoscope ---");line(AstroEngine.dailyHoroscope(r,ZonedDateTime.now()));doc.finishPage(page)
    val f=File(ctx.getExternalFilesDir(null),"GulabAstro-Kundli-${System.currentTimeMillis()}.pdf");FileOutputStream(f).use{doc.writeTo(it)};doc.close();val uri=FileProvider.getUriForFile(ctx,"com.gulabastro.app.fileprovider",f);val i=Intent(Intent.ACTION_SEND).apply{type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};ctx.startActivity(Intent.createChooser(i,"Save / Share Kundli PDF"))
}
private fun share(ctx:Context,r:ChartResult){val text="Gulab Astro\nLagna: ${AstroEngine.sign(r.ascendant)}\nMoon: ${r.moonSign}\nNakshatra: ${r.moonNakshatra}\nAyanamsha: ${fmt(r.ayanamsha)}°";ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Share Kundli"))}
