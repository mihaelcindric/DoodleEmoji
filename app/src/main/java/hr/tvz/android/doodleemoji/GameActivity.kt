package hr.tvz.android.doodleemoji

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.camera.view.PreviewView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import hr.tvz.android.doodleemoji.ui.theme.DoodleEmojiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GameActivity : ComponentActivity() {
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setContent {
            DoodleEmojiTheme {
                GameScreen()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @Composable
    fun GameScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val emojiList = listOf("❤️", "😃", "😍", "😵", "👍")
        val selectedEmoji = remember { mutableStateOf(emojiList.random()) }
        val timerValue = remember { mutableStateOf(10) }
        val cameraUnlocked = remember { mutableStateOf(false) }
        val secondTimerValue = remember { mutableStateOf(5) }
        val imageUri = remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Time remaining: ${timerValue.value}", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = selectedEmoji.value,
                fontSize = 50.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (!cameraUnlocked.value) {
                Button(onClick = {
                    startFirstTimer(timerValue, cameraUnlocked)
                }) {
                    Text(text = "Start", fontSize = 20.sp)
                }
            } else {
                if (imageUri.value == null) {
                    Text(text = "Capture in: ${secondTimerValue.value}", fontSize = 24.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { context ->
                                PreviewView(context).apply {
                                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                                    this.layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    this.id = R.id.previewView
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        FloatingActionButton(
                            onClick = { capturePhoto(context, lifecycleOwner, imageUri) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp),
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_launcher_foreground), contentDescription = "Capture")
                        }
                    }
                    LaunchedEffect(Unit) {
                        startCamera(context)
                        startSecondTimer(secondTimerValue)
                    }
                } else {
                    Image(
                        bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri.value!!.toUri()).asImageBitmap(),
                        contentDescription = "Captured image",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private fun startFirstTimer(timerValue: MutableState<Int>, cameraUnlocked: MutableState<Boolean>) {
        object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerValue.value = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                timerValue.value = 0
                cameraUnlocked.value = true
            }
        }.start()
    }

    private fun startSecondTimer(secondTimerValue: MutableState<Int>) {
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondTimerValue.value = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                secondTimerValue.value = 0
                // capturePhoto() is now triggered by the button click
            }
        }.start()
    }

    private fun startCamera(context: android.content.Context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.previewView).surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                // Handle exception
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun capturePhoto(context: android.content.Context, lifecycleOwner: LifecycleOwner, imageUri: MutableState<String?>) {
        val photoFile = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    lifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            // Save image to gallery
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, photoFile.name)
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/DoodleEmoji")
                            }
                            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            uri?.let {
                                val outputStream = context.contentResolver.openOutputStream(it)
                                FileInputStream(photoFile).use { input ->
                                    outputStream?.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                imageUri.value = it.toString()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    // Handle the error
                }
            }
        )
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
