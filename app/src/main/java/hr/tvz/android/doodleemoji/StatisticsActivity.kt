package hr.tvz.android.doodleemoji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.tvz.android.doodleemoji.ui.theme.DoodleEmojiTheme

class StatisticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DoodleEmojiTheme {
                StatisticsScreen()
            }
        }
    }
}

@Composable
fun StatisticsScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Statistics", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(20.dp))
        // Display statistics here
        // Example: Text(text = "Emoji 1: 10 successful attempts", fontSize = 18.sp)
    }
}
