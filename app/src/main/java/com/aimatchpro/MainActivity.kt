
package com.aimatchpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkBg = Color(0xFF0B1220)
private val CardBg = Color(0xFF19263A)
private val Green = Color(0xFF35D69A)

data class Match(
    val id: String,
    val league: String,
    val home: String,
    val away: String,
    val score: String,
    val status: String,
    val clock: String
) {
    val live: Boolean
        get() = status.lowercase() in listOf(
            "first half", "second half", "half time",
            "extra time", "break time", "penalties",
            "in progress", "interrupted", "suspended"
        )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("aimatch", MODE_PRIVATE)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Green,
                    background = DarkBg,
                    surface = CardBg
                )
            ) {
                FootballApp(
                    savedKey = prefs.getString("api_key", "") ?: "",
                    saveKey = {
                        prefs.edit().putString("api_key", it).apply()
                    }
                )
            }
        }
    }
}

private fun readText(obj: JSONObject?, key: String): String {
    return obj?.optString(key)?.takeIf {
        it.isNotBlank() && it != "null"
    } ?: ""
}

private suspend fun getMatches(
    apiKey: String,
    date: String
): List<Match> = withContext(Dispatchers.IO) {

    require(apiKey.isNotBlank()) {
        "أدخل API Key أولاً"
    }

    val url = URL(
        "https://soccer.highlightly.net/matches" +
        "?date=$date&timezone=Europe%2FParis&limit=100"
    )

    val connection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("x-rapidapi-key", apiKey)
            connectTimeout = 15000
            readTimeout = 15000
        }

    try {
        val responseCode = connection.responseCode

        val stream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val response = stream.bufferedReader().use {
            it.readText()
        }

        if (responseCode !in 200..299) {
            throw Exception("خطأ API: $responseCode")
        }

        val root = JSONObject(response)
        val data = root.optJSONArray("data")
            ?: throw Exception("لم يتم العثور على بيانات المباريات")

        buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue

                val home = item.optJSONObject("homeTeam")
                val away = item.optJSONObject("awayTeam")
                val league = item.optJSONObject("league")
                val state = item.optJSONObject("state")
                val score = state?.optJSONObject("score")

                val currentScore = readText(score, "current")
                    .ifBlank {
                        val homeScore = readText(score, "home")
                        val awayScore = readText(score, "away")
                        if (homeScore.isNotBlank() &&
                            awayScore.isNotBlank()
                        ) "$homeScore - $awayScore" else "—"
                    }

                add(
                    Match(
                        id = item.optString("id", "$i"),
                        league = readText(league, "name")
                            .ifBlank { "دوري كرة القدم" },
                        home = readText(home, "name")
                            .ifBlank { "الفريق الأول" },
                        away = readText(away, "name")
                            .ifBlank { "الفريق الثاني" },
                        score = currentScore,
                        status = readText(state, "description")
                            .ifBlank { "غير معروف" },
                        clock = readText(state, "clock")
                    )
                )
            }
        }
    } finally {
        connection.disconnect()
    }
}

@Composable
fun FootballApp(
    savedKey: String,
    saveKey: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf(savedKey) }
    var keyInput by remember { mutableStateOf(savedKey) }

    var matches by remember {
        mutableStateOf<List<Match>>(emptyList())
    }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var liveOnly by remember { mutableStateOf(false) }

    var date by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Date())
        )
    }

    suspend fun refresh() {
        if (apiKey.isBlank()) {
            error = "أدخل مفتاح Highlightly أولاً"
            return
        }

        loading = true
        error = ""

        try {
            matches = getMatches(apiKey, date)
        } catch (e: Exception) {
            error = e.message ?: "تعذر تحميل المباريات"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(apiKey, date) {
        if (apiKey.isNotBlank()) {
            refresh()
            while (true) {
                delay(60_000)
                refresh()
            }
        }
    }

    val displayedMatches = if (liveOnly) {
        matches.filter { it.live }
    } else {
        matches
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Text(
                "AI MATCH PRO",
                color = Green,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                "كرة القدم • نتائج مباشرة وتحليلات",
                color = Color.LightGray
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Highlightly API Key") },
                singleLine = true,
                visualTransformation =
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                )
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    apiKey = keyInput.trim()
                    saveKey(apiKey)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ المفتاح وربط المباريات")
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "مباريات $date",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !liveOnly,
                    onClick = { liveOnly = false },
                    label = { Text("كل المباريات") }
                )

                FilterChip(
                    selected = liveOnly,
                    onClick = { liveOnly = true },
                    label = { Text("مباشر") }
                )

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = {
                        date = SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                        ).format(Date())
                    }
                ) {
                    Text("اليوم")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    val format = SimpleDateFormat(
                        "yyyy-MM-dd", Locale.US
                    )
                    val calendar = java.util.Calendar.getInstance()
                    calendar.time = format.parse(date) ?: Date()
                    calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
                    date = format.format(calendar.time)
                }) {
                    Text("السابق")
                }

                Text(
                    date,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                TextButton(onClick = {
                    val format = SimpleDateFormat(
                        "yyyy-MM-dd", Locale.US
                    )
                    val calendar = java.util.Calendar.getInstance()
                    calendar.time = format.parse(date) ?: Date()
                    calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                    date = format.format(calendar.time)
                }) {
                    Text("التالي")
                }

                TextButton(
                    onClick = {
                        // Automatic refresh runs every minute.
                    }
                ) {
                    Text("تحديث تلقائي")
                }
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Green
                )
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    color = Color(0xFFFF8A80),
                    modifier = Modifier.padding(8.dp)
                )
            }

            if (!loading && displayedMatches.isEmpty()
                && error.isEmpty()
            ) {
                Text(
                    "لا توجد مباريات متاحة حالياً",
                    color = Color.LightGray,
                    modifier = Modifier.padding(12.dp)
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    displayedMatches,
                    key = { it.id }
                ) { match ->

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = CardBg
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    match.league,
                                    color = Green,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    if (match.live) "● مباشر"
                                    else match.status,
                                    color = if (match.live)
                                        Green else Color.LightGray
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    match.home,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    match.score,
                                    color = Green,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Text(
                                    match.away,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            if (match.clock.isNotBlank()) {
                                Text(
                                    "الدقيقة: ${match.clock}",
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "البيانات من Highlightly • تحديث كل 60 ثانية",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
