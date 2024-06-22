package hr.tvz.android.doodleemoji.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import hr.tvz.android.doodleemoji.MainActivity
import hr.tvz.android.doodleemoji.R
import hr.tvz.android.doodleemoji.ui.theme.DoodleEmojiTheme

class LoginActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = Firebase.database("https://doodleemoji-default-rtdb.europe-west1.firebasedatabase.app/")
        setContent {
            DoodleEmojiTheme {
                LoginScreen()
            }
        }
    }

    @Composable
    fun LoginScreen() {
        var usernameOrEmail by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.login_title), fontSize = 24.sp)
            Spacer(modifier = Modifier.height(20.dp))
            TextField(value = usernameOrEmail, onValueChange = { usernameOrEmail = it }, label = { Text(stringResource(R.string.username_or_email)) })
            Spacer(modifier = Modifier.height(20.dp))
            TextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.password)) }, visualTransformation = PasswordVisualTransformation())
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = {
                if (usernameOrEmail.contains("@")) {
                    // Login with email
                    auth.signInWithEmailAndPassword(usernameOrEmail, password)
                        .addOnCompleteListener(this@LoginActivity) { task ->
                            if (task.isSuccessful) {
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this@LoginActivity, R.string.login_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    // Login with username
                    val userRef = database.getReference("users")
                    userRef.orderByChild("username").equalTo(usernameOrEmail).get().addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result.childrenCount > 0) {
                            val email = task.result.children.first().child("email").getValue(String::class.java)
                            if (email != null) {
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener(this@LoginActivity) { authTask ->
                                        if (authTask.isSuccessful) {
                                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                            finish()
                                        } else {
                                            Toast.makeText(this@LoginActivity, R.string.login_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            } else {
                                Toast.makeText(this@LoginActivity, R.string.login_failed, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@LoginActivity, R.string.login_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }) {
                Text(text = stringResource(R.string.login_button))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.register_here),
                modifier = Modifier.clickable {
                    startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
                },
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
