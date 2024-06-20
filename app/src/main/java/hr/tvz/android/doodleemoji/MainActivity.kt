// MainActivity.kt
package hr.tvz.android.doodleemoji

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.tvz.android.doodleemoji.ui.theme.DoodleEmojiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DoodleEmojiTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Logo",
                modifier = Modifier.size(150.dp)
            )
            Spacer(modifier = Modifier.height(50.dp))
            Button(onClick = {
                startActivity(Intent(this@MainActivity, GameActivity::class.java))
            }) {
                Text(text = "Play", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = {
                startActivity(Intent(this@MainActivity, StatisticsActivity::class.java))
            }) {
                Text(text = "Statistics", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = {
                finish()
            }) {
                Text(text = "Exit", fontSize = 20.sp)
            }
        }
    }
}
