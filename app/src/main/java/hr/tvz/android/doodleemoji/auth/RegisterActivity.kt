package hr.tvz.android.doodleemoji.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

class RegisterActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = Firebase.database("https://doodleemoji-default-rtdb.europe-west1.firebasedatabase.app/")
        setContent {
            DoodleEmojiTheme {
                RegisterScreen()
            }
        }
    }

    @Composable
    fun RegisterScreen() {
        val username = remember { mutableStateOf("") }
        val email = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val confirmPassword = remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.register_title), fontSize = 24.sp)
            Spacer(modifier = Modifier.height(20.dp))
            TextField(value = username.value, onValueChange = { username.value = it }, label = { Text(stringResource(R.string.username)) })
            Spacer(modifier = Modifier.height(20.dp))
            TextField(value = email.value, onValueChange = { email.value = it }, label = { Text(stringResource(R.string.email)) })
            Spacer(modifier = Modifier.height(20.dp))
            TextField(value = password.value, onValueChange = { password.value = it }, label = { Text(stringResource(R.string.password)) }, visualTransformation = PasswordVisualTransformation())
            Spacer(modifier = Modifier.height(20.dp))
            TextField(value = confirmPassword.value, onValueChange = { confirmPassword.value = it }, label = { Text(stringResource(R.string.confirm_password)) }, visualTransformation = PasswordVisualTransformation())
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = {
                if (password.value == confirmPassword.value) {
                    checkUsernameUnique(username.value) { isUnique ->
                        if (isUnique) {
                            auth.createUserWithEmailAndPassword(email.value, password.value)
                                .addOnCompleteListener(this@RegisterActivity) { task ->
                                    if (task.isSuccessful) {
                                        saveUserData(username.value, email.value)
                                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                                        finish()
                                    } else {
                                        Toast.makeText(this@RegisterActivity, R.string.registration_failed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } else {
                            Toast.makeText(this@RegisterActivity, R.string.username_taken, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@RegisterActivity, R.string.passwords_do_not_match, Toast.LENGTH_SHORT).show()
                }
            }) {
                Text(text = stringResource(R.string.register_button))
            }
        }
    }

    private fun checkUsernameUnique(username: String, callback: (Boolean) -> Unit) {
        val userRef = database.getReference("users")
        userRef.orderByChild("username").equalTo(username).get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                callback(task.result.childrenCount == 0L)
            } else {
                Toast.makeText(this, "Error checking username: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                callback(false)
            }
        }
    }

    private fun saveUserData(username: String, email: String) {
        val user = auth.currentUser ?: return
        val userData = mapOf(
            "uid" to user.uid,
            "username" to username,
            "email" to email,
            "stats" to mapOf(
                "❤️" to EmojiStats(),
                "😃" to EmojiStats(),
                "😍" to EmojiStats(),
                "😵" to EmojiStats(),
                "👍" to EmojiStats()
            )
        )
        database.getReference("users").child(user.uid).setValue(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "User data saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error saving user data: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

data class EmojiStats(var attempts: Int = 0, var successes: Int = 0)
