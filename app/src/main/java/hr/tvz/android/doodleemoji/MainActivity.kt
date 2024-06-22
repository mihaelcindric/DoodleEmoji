package hr.tvz.android.doodleemoji

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import hr.tvz.android.doodleemoji.auth.LoginActivity
import hr.tvz.android.doodleemoji.ui.theme.DoodleEmojiTheme

data class EmojiStats(var attempts: Int = 0, var successes: Int = 0)

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContent {
            DoodleEmojiTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen() {
        val user = auth.currentUser
        val showDialog = remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(250.dp)
                )
                Spacer(modifier = Modifier.height(50.dp))
                Button(onClick = {
                    startActivity(Intent(this@MainActivity, GameActivity::class.java))
                }) {
                    Text(text = stringResource(R.string.play), fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = {
                    startActivity(Intent(this@MainActivity, StatisticsActivity::class.java))
                }) {
                    Text(text = stringResource(R.string.statistics), fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = {
                    finish()
                }) {
                    Text(text = stringResource(R.string.exit), fontSize = 20.sp)
                }
            }
            Box(modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)) {
                Icon(
                    painter = painterResource(if (user != null) R.drawable.ic_profile else R.drawable.ic_login),
                    contentDescription = stringResource(R.string.profile),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable {
                            if (user != null) {
                                showDialog.value = true
                            } else {
                                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                            }
                        }
                )
            }
            if (showDialog.value) {
                AlertDialog(
                    onDismissRequest = { showDialog.value = false },
                    title = { Text(text = stringResource(R.string.logout_title)) },
                    text = { Text(stringResource(R.string.logout_confirm)) },
                    confirmButton = {
                        Button(onClick = {
                            auth.signOut()
                            showDialog.value = false
                            startActivity(Intent(this@MainActivity, MainActivity::class.java))
                            finish()
                        }) {
                            Text(text = stringResource(R.string.logout))
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showDialog.value = false }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}
