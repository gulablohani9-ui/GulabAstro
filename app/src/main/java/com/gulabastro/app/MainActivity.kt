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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

data class City(val name: String, val lat: Double, val lon: Double, val zone: String = "Asia/Kolkata")

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
    var selectedCity by remember { mutableStateOf<City?>(City("Jodhpur, Rajasthan, India", 26.2389, 73.0243)) }

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
                    selectedCity = selectedCity,
                    onCitySelected = { city ->
                        selectedCity = city
                        place = city.name
                    },
                    isLoading = isLoading,
                    error = error,
                    onGenerate = {
                        scope.launch {
                            isLoading = true
                            error = ""
                            try {
                                val dt = LocalDateTime.parse("$date $time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                val finalCity = selectedCity ?: searchCitySuggestions(ctx, place).firstOrNull() ?: City(place, 26.2389, 73.0243)
                                
                                val res = AstroEngine.calculate(BirthData(name.ifBlank { "User" }, dt, finalCity.lat, finalCity.lon, ZoneId.of(finalCity.zone)))
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
    selectedCity: City?,
    onCitySelected: (City) -> Unit,
    isLoading: Boolean,
    error: String,
    onGenerate: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<City>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var isSearchingCity by remember { mutableStateOf(false) }

    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("जातक विवरण (Birth Profile)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(name, onNameChange, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(date, onDateChange, label = { Text("Birth Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(time, onTimeChange, label = { Text("Birth Time (HH:MM)") }, modifier = Modifier.fillMaxWidth()) }

        item {
            Column {
                OutlinedTextField(
                    value = place,
                    onValueChange = { query ->
                        onPlaceChange(query)
                        searchJob?.cancel()
                        if (query.trim().length >= 2) {
                            searchJob = scope.launch {
                                isSearchingCity = true
                                delay(300)
                                suggestions = searchCitySuggestions(ctx, query)
                                isSearchingCity = false
                            }
                        } else {
                            suggestions = emptyList()
                        }
                    },
                    label = { Text("Birth Place (Type to auto-suggest)") },
                    trailingIcon = {
                        if (isSearchingCity) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column {
                            suggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onCitySelected(suggestion)
                                            suggestions = emptyList()
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(suggestion.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Lat: ${fmt(suggestion.lat)}°, Lon: ${fmt(suggestion.lon)}°", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        item {
            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Calculating..." else "Generate Vedic Kundli")
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

private suspend fun searchCitySuggestions(ctx: Context, query: String): List<City> = withContext(Dispatchers.IO) {
    val results = mutableListOf<City>()
    try {
        val geocoder = Geocoder(ctx, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val list = mutableListOf<Address>()
            geocoder.getFromLocationName(query, 5)?.let { list.addAll(it) }
            list.forEach { addr ->
                val name = listOfNotNull(addr.locality ?: addr.subAdminArea, addr.adminArea, addr.countryName).joinToString(", ")
                results.add(City(name.ifBlank { query }, addr.latitude, addr.longitude))
            }
        } else {
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocationName(query, 5)
            list?.forEach { addr ->
                val name = listOfNotNull(addr.locality ?: addr.subAdminArea, addr.adminArea, addr.countryName).joinToString(", ")
                results.add(City(name.ifBlank { query }, addr.latitude, addr.longitude))
            }
        }
    } catch (_: Exception) {}

    if (results.isEmpty()) {
        val q = query.trim().lowercase()
        if ("jodhpur".contains(q)) results.add(City("Jodhpur, Rajasthan", 26.2389, 73.0243))
        if ("jaipur".contains(q)) results.add(City("Jaipur, Rajasthan", 26.9124, 75.7873))
        if ("delhi".contains(q)) results.add(City("Delhi, India", 28.6139, 77.2090))
        if ("mumbai".contains(q)) results.add(City("Mumbai, Maharashtra", 19.0760, 72.8777))
    }
    results
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
