package hr.tvz.android.doodleemoji

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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

        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = painterResource(id = R.drawable.ic_exit),
                contentDescription = stringResource(R.string.exit),
                modifier = Modifier
                    .padding(16.dp)
                    .size(32.dp)
                    .clickable { finish() }
                    .align(Alignment.TopStart)
            )

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
                        fontSize = 36.sp,
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
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFBBDEFB), shape = RoundedCornerShape(12.dp)) // Light blue background with rounded corners
                            .padding(16.dp)
                    ) {
                        Text(text = stringResource(R.string.statistics), fontSize = 48.sp)
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                    stats.value.forEach { (emoji, stat) ->
                        val successRate = if (stat.attempts > 0) {
                            (stat.successes.toFloat() / stat.attempts * 100).toInt()
                        } else {
                            0
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF90CAF9), shape = RoundedCornerShape(12.dp)) // Darker blue background with rounded corners
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "$emoji: ${stat.successes}/${stat.attempts} ($successRate%)",
                                fontSize = 36.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}
