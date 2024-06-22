package hr.tvz.android.doodleemoji

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import hr.tvz.android.doodleemoji.ui.theme.DoodleEmojiTheme


class StatisticsActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance("https://doodleemoji-default-rtdb.europe-west1.firebasedatabase.app/")
        setContent {
            DoodleEmojiTheme {
                StatisticsScreen()
            }
        }
    }

    @Composable
    fun StatisticsScreen() {
        val user = auth.currentUser
        val stats = remember { mutableStateOf<Map<String, EmojiStats>>(emptyMap()) }

        if (user == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.login_to_view_statistics),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val userStatsRef = database.getReference("users").child(user.uid).child("stats")
            LaunchedEffect(user.uid) {
                userStatsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val fetchedStats = mutableMapOf<String, EmojiStats>()
                        for (data in snapshot.children) {
                            val emoji = data.key ?: continue
                            val emojiStats = data.getValue(EmojiStats::class.java) ?: EmojiStats()
                            fetchedStats[emoji] = emojiStats
                            Log.d("StatisticsActivity", "Emoji: $emoji, Stats: $emojiStats")
                        }
                        stats.value = fetchedStats
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("StatisticsActivity", "Error fetching data: ${error.message}")
                    }
                })
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(R.string.statistics), fontSize = 24.sp)
                Spacer(modifier = Modifier.height(20.dp))
                stats.value.forEach { (emoji, stat) ->
                    val successRate = if (stat.attempts > 0) {
                        (stat.successes.toFloat() / stat.attempts * 100).toInt()
                    } else {
                        0
                    }
                    Text(
                        text = "$emoji: ${stat.successes}/${stat.attempts} ($successRate%)",
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}
