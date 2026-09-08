@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gulabastro.app

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.gulabastro.app.astro.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFB300), surface = Color(0xFF1E1E2E))) {
                GulabAstroApp()
            }
        }
    }
}

data class City(val lat: Double, val lon: Double, val zone: String = "Asia/Kolkata")

enum class ScreenTab(val title: String, val icon: ImageVector) {
    INPUT("Form", Icons.Default.Edit),
    CHART("Lagna", Icons.Default.AccountBox),
    PLANETS("Grahas", Icons.Default.Star),
    TRANSIT("Transit", Icons.Default.Refresh),
    DASHA("Dasha/KP", Icons.Default.List),
    INSIGHTS("Horoscope", Icons.Default.Info)
}

@Composable
fun GulabAstroApp() {
    var selectedTab by remember { mutableStateOf(ScreenTab.INPUT) }
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("1990-01-01") }
    var time by remember { mutableStateOf("12:00") }
    var place by remember { mutableStateOf("Jodhpur") }
    var chart by remember { mutableStateOf<ChartResult?>(null) }
    var error by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gulab Astro Digital Studio", fontWeight = FontWeight.Bold) },
                actions = {
                    if (chart != null) {
                        IconButton(onClick = { exportPdf(ctx, chart!!) }) {
                            Icon(Icons.Default.Share, contentDescription = "PDF")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                ScreenTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (selectedTab) {
                ScreenTab.INPUT -> InputView(
                    name = name, onNameChange = { name = it },
                    date = date, onDateChange = { date = it },
                    time = time, onTimeChange = { time = it },
                    place = place, onPlaceChange = { place = it },
                    isLoading = isLoading,
                    error = error,
                    onGenerate = {
                        scope.launch {
                            isLoading = true
                            error = ""
                            try {
                                val dt = LocalDateTime.parse("$date $time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                val c = fetchCityCoordinates(ctx, place)
                                val res = AstroEngine.calculate(BirthData(name.ifBlank { "User" }, dt, c.lat, c.lon, ZoneId.of(c.zone)))
                                chart = res
                                selectedTab = ScreenTab.CHART
                            } catch (e: Exception) {
                                error = e.message ?: "Calculation error"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )

                ScreenTab.CHART -> ChartWindow(chart)
                ScreenTab.PLANETS -> PlanetsWindow(chart, searchQuery, onSearchChange = { searchQuery = it })
                ScreenTab.TRANSIT -> TransitWindow(chart)
                ScreenTab.DASHA -> DashaKpWindow(chart)
                ScreenTab.INSIGHTS -> InsightsWindow(chart)
            }
        }
    }
}

@Composable
fun InputView(
    name: String, onNameChange: (String) -> Unit,
    date: String, onDateChange: (String) -> Unit,
    time: String, onTimeChange: (String) -> Unit,
    place: String, onPlaceChange: (String) -> Unit,
    isLoading: Boolean,
    error: String,
    onGenerate: () -> Unit
) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("जातक विवरण (Birth Profile)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(name, onNameChange, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(date, onDateChange, label = { Text("Birth Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(time, onTimeChange, label = { Text("Birth Time (HH:MM)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(place, onPlaceChange, label = { Text("City (Automatic Coordinates)") }, modifier = Modifier.fillMaxWidth()) }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        item {
            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Creating Chart..." else "Generate Vedic Kundli")
            }
        }
    }
}

@Composable
fun ChartWindow(chart: ChartResult?) {
    if (chart == null) { EmptyState(); return }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ascendant (लग्न): ${AstroEngine.sign(chart.ascendant)} (${fmt(chart.ascendant)}°)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Moon Sign (राशि): ${chart.moonSign}", fontSize = 16.sp)
                    Text("Nakshatra: ${chart.moonNakshatra} (Pada ${chart.nakshatraPada})", fontSize = 16.sp)
                    Text("Ayanamsha (Lahiri): ${fmt(chart.ayanamsha)}°", fontSize = 14.sp)
                }
            }
        }
        item {
            Text("Digital 12 Houses Matrix", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        items(12) { idx ->
            val hNo = idx + 1
            val signName = Sign.entries[chart.houses[idx]].en
            val occupantPlanets = chart.planets.filter { AstroEngine.houseOf(it.longitude, chart.ascendant) == hNo }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("House $hNo ($signName)", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (occupantPlanets.isEmpty()) "No Graha"
                        else occupantPlanets.joinToString { "${it.name}${if (it.retrograde) " (R)" else ""}" },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun PlanetsWindow(chart: ChartResult?, query: String, onSearchChange: (String) -> Unit) {
    if (chart == null) { EmptyState(); return }
    val filtered = chart.planets.filter { it.name.contains(query, ignoreCase = true) || AstroEngine.sign(it.longitude).contains(query, ignoreCase = true) }

    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search planet or rashi...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
        items(filtered) { p ->
            val house = AstroEngine.houseOf(p.longitude, chart.ascendant)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name + if (p.retrograde) " ℞" else "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("House $house", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Longitude: ${fmt(p.longitude)}° in ${AstroEngine.sign(p.longitude)}")
                    Text("Nakshatra: ${AstroEngine.nakshatraName(p.longitude)} (Lord: ${AstroEngine.nakshatraLord(p.longitude)})", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun TransitWindow(chart: ChartResult?) {
    if (chart == null) { EmptyState(); return }
    val now = ZonedDateTime.now()
    val hits = AstroEngine.transitHits(chart, now)
    val ashtam = AstroEngine.chandraAshtam(chart, now)

    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (ashtam) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("चंद्र अष्टम स्थिति", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (ashtam) "⚠️ आज चंद्रमा अष्टम गोचर में है! सावधानी बरतें।"
                        else "✅ आज कोई चंद्र अष्टम नहीं है। गोचर अनुकूल है।"
                    )
                }
            }
        }
        item { Text("Active Live Transit Hits", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        if (hits.isEmpty()) {
            item { Text("वर्तमान में कोई major aspect hit नहीं है।") }
        } else {
            items(hits) { hit ->
                Card(Modifier.fillMaxWidth()) {
                    Text("• $hit", Modifier.padding(12.dp), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun DashaKpWindow(chart: ChartResult?) {
    if (chart == null) { EmptyState(); return }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Vimshottari Dasha Sequence", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        items(chart.dasha) { d ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(d.lord, fontWeight = FontWeight.Bold)
                    Text("${d.start.toLocalDate()}  ⟶  ${d.end.toLocalDate()}", fontSize = 13.sp)
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item { Text("KP Sub-Lord Matrix", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        items(chart.kp) { k ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k.planet, fontWeight = FontWeight.SemiBold)
                    Text("Star: ${k.starLord} | Sub: ${k.subLord}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun InsightsWindow(chart: ChartResult?) {
    if (chart == null) { EmptyState(); return }
    val now = ZonedDateTime.now()
    val horoscope = AstroEngine.dailyHoroscope(chart, now)

    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("दैनिक राशिफल (Transit Based)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(horoscope)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("वास्तु एवं उपाय (Remedies)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("• ईशान कोण (North-East) को सदा स्वच्छ और भारमुक्त रखें।\n• घर का मुख्य द्वार साफ रखें।\n• चंद्र शांति हेतु सोमवार को जल का अपव्यय रोकें।")
                }
            }
        }
    }
}

@Composable
fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("कृपया पहले 'Form' टैब में जाकर कुंडली बनाएं।", color = Color.Gray)
    }
}

private fun fmt(v: Double) = "%.2f".format(v)

private suspend fun fetchCityCoordinates(ctx: Context, cityName: String): City = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(ctx, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val list = mutableListOf<Address>()
            geocoder.getFromLocationName(cityName, 1)?.let { list.addAll(it) }
            if (list.isNotEmpty()) {
                val addr = list[0]
                return@withContext City(addr.latitude, addr.longitude)
            }
        } else {
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(cityName, 1)
            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                return@withContext City(addr.latitude, addr.longitude)
            }
        }
    } catch (_: Exception) {}

    City(26.9124, 75.7873)
}

private fun exportPdf(ctx: Context, r: ChartResult) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val p = Paint().apply { textSize = 14f }
    var y = 40f
    fun write(s: String) { page.canvas.drawText(s, 30f, y, p); y += 22f }

    write("Gulab Astro Digital Studio Report")
    write("Name: ${r.birth.name} (${r.birth.localDateTime})")
    write("Lagna: ${AstroEngine.sign(r.ascendant)} ${fmt(r.ascendant)}°")
    write("Moon: ${r.moonSign} / ${r.moonNakshatra}")
    write("--- Planets ---")
    r.planets.forEach { write("${it.name}: ${fmt(it.longitude)}° ${AstroEngine.sign(it.longitude)}") }

    doc.finishPage(page)
    val f = File(ctx.getExternalFilesDir(null), "GulabAstro-${System.currentTimeMillis()}.pdf")
    FileOutputStream(f).use { doc.writeTo(it) }
    doc.close()

    val uri = FileProvider.getUriForFile(ctx, "com.gulabastro.app.fileprovider", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Share Kundli PDF"))
}
