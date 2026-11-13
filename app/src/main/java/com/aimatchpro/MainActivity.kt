package com.aimatchpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RealScoreScreen()
            }
        }
    }
}

@Composable
fun RealScoreScreen() {
    var matches by remember { mutableStateOf<List<FixtureData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<FixtureData?>(null) }
    var filter by remember { mutableStateOf("LIVE") }

    suspend fun load() {
        try {
            loading = true
            error = ""
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val res = if (filter == "LIVE") ApiClient.api.getLiveMatches() else ApiClient.api.getTodayMatches(date = today)
            matches = res.response
            loading = false
            if (matches.isEmpty()) error = "No live matches now. Try TODAY."
        } catch (e: Exception) {
            loading = false
            error = "Error: ${e.message} - Check your API KEY"
        }
    }

    LaunchedEffect(filter) { load() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val res = if (filter == "LIVE") ApiClient.api.getLiveMatches() else ApiClient.api.getTodayMatches(date = today)
                matches = res.response
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F1A)).padding(16.dp)) {
        Text(text = "AI Match Pro - Live", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(text = "Real Scores like Score Exact", color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { filter = "LIVE" }, colors = ButtonDefaults.buttonColors(containerColor = if (filter == "LIVE") Color(0xFF00E676) else Color.Gray)) { Text(text = "LIVE", color = Color.Black) }
            Button(onClick = { filter = "TODAY" }, colors = ButtonDefaults.buttonColors(containerColor = if (filter == "TODAY") Color.White else Color.Gray)) { Text(text = "TODAY", color = Color.Black) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (loading) {
            Text(text = "Loading live scores...", color = Color.White)
        } else if (error.isNotEmpty()) {
            Text(text = error, color = Color.Red)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Check API KEY in ApiService.kt", color = Color.Yellow, fontSize = 13.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(matches) { m ->
                    val homeGoals = m.goals.home ?: 0
                    val awayGoals = m.goals.away ?: 0
                    val status = m.fixture.status.long
                    val elapsed = m.fixture.status.elapsed?.toString() ?: ""
                    Card(modifier = Modifier.fillMaxWidth().clickable { selected = m }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = status + if (elapsed.isNotEmpty()) " - $elapsed'" else "", color = Color.Yellow, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = m.teams.home.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(text = "$homeGoals - $awayGoals", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(text = m.teams.away.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "AI Prediction: ${getPrediction(m.teams.home.name, m.teams.away.name)}", color = Color.Cyan, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (selected != null) {
        val m = selected!!
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(text = "${m.teams.home.name} vs ${m.teams.away.name}") },
            text = { Text(text = "Score: ${m.goals.home ?: 0} - ${m.goals.away ?: 0}\nStatus: ${m.fixture.status.long}\n\nAI Prediction:\n${getPrediction(m.teams.home.name, m.teams.away.name)}") },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(text = "Close") } }
        )
    }
}
