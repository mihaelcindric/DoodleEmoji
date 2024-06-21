package hr.tvz.android.doodleemoji

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import hr.tvz.android.doodleemoji.ui.theme.DoodleEmojiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GameActivity : ComponentActivity() {
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tflite: Interpreter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Učitaj TFLite model
        val tfliteModel = loadModelFile(R.raw.doodle_emoji_vgg16_model_80)
        tflite = Interpreter(tfliteModel)

        setContent {
            DoodleEmojiTheme {
                GameScreen()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun loadModelFile(resourceId: Int): ByteBuffer {
        val fileDescriptor = resources.openRawResourceFd(resourceId)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength).order(ByteOrder.nativeOrder())
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tflite.close()
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
        val showEmoji = remember { mutableStateOf(false) }
        val allowCapture = remember { mutableStateOf(true) }
        val capturedBitmap = remember { mutableStateOf<Bitmap?>(null) }
        val modelOutput = remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!showEmoji.value) {
                Text(text = "Draw the emoji!", fontSize = 24.sp)
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = {
                    showEmoji.value = true
                    startFirstTimer(timerValue, cameraUnlocked)
                }) {
                    Text(text = "Start", fontSize = 20.sp)
                }
            } else {
                if (!cameraUnlocked.value) {
                    Text(text = timerValue.value.toString(), fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = selectedEmoji.value,
                        fontSize = 150.sp
                    )
                } else {
                    if (imageUri.value == null && allowCapture.value) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = secondTimerValue.value.toString(), fontSize = 48.sp, modifier = Modifier.padding(start = 16.dp))
                            Text(text = selectedEmoji.value, fontSize = 48.sp, modifier = Modifier.padding(end = 16.dp))
                        }
                        Text(text = "Quick! Take a picture of your drawing.", fontSize = 16.sp, modifier = Modifier.padding(16.dp))
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(1f)) { // Enforces a square aspect ratio
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
                                onClick = {
                                    if (allowCapture.value) {
                                        capturePhoto(context, lifecycleOwner, imageUri, allowCapture, capturedBitmap, modelOutput)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp)
                                    .size(72.dp)
                                    .clip(CircleShape),
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ) {}
                        }
                        LaunchedEffect(Unit) {
                            startCamera(context)
                            startSecondTimer(secondTimerValue, allowCapture)
                        }
                    } else if (imageUri.value != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            capturedBitmap.value?.let {
                                Image(
                                    painter = BitmapPainter(it.asImageBitmap()),
                                    contentDescription = "Captured image",
                                    modifier = Modifier.size(256.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            modelOutput.value?.let { output ->
                                val results = parseModelOutput(output)
                                displayResults(results, selectedEmoji.value)
                            }
                        }
                    } else {
                        Text(
                            text = "Try to be quicker next time!",
                            fontSize = 24.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 200.dp)
                        )
                    }
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

    private fun startSecondTimer(secondTimerValue: MutableState<Int>, allowCapture: MutableState<Boolean>) {
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondTimerValue.value = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                secondTimerValue.value = 0
                allowCapture.value = false
            }
        }.start()
    }

    private fun startCamera(context: android.content.Context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setTargetResolution(android.util.Size(1280, 1280))  // Set square resolution
                .build().also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.previewView).surfaceProvider)
                }
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(android.util.Size(1280, 1280))  // Set square resolution
                .build()
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

    private fun capturePhoto(
        context: android.content.Context,
        lifecycleOwner: LifecycleOwner,
        imageUri: MutableState<String?>,
        allowCapture: MutableState<Boolean>,
        capturedBitmap: MutableState<Bitmap?>,
        modelOutput: MutableState<String?>
    ) {
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
                                capturedBitmap.value = rotateBitmapIfNeeded(BitmapFactory.decodeFile(photoFile.absolutePath))
                                processImageWithModel(capturedBitmap.value, modelOutput)
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

    private fun rotateBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(90f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun processImageWithModel(bitmap: Bitmap?, modelOutput: MutableState<String?>) {
        bitmap?.let {
            // Resize and normalize the image to 128x128 for the model
            val resizedBitmap = Bitmap.createScaledBitmap(it, 128, 128, true)
            val tensorImage = TensorImage.fromBitmap(resizedBitmap)
            val normalizeOp = NormalizeOp(0f, 255f)
            val normalizedTensorImage = normalizeOp.apply(tensorImage.tensorBuffer)

            Log.d("ImageProcessing", "Image size: ${resizedBitmap.width}x${resizedBitmap.height}")

            // Convert tensorImage to ByteBuffer
            val inputBuffer = ByteBuffer.allocateDirect(1 * 128 * 128 * 3 * 4).order(ByteOrder.nativeOrder())
            inputBuffer.rewind()
            normalizedTensorImage.buffer.rewind()
            inputBuffer.put(normalizedTensorImage.buffer)

            // Prepare output buffer
            val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 5), org.tensorflow.lite.DataType.FLOAT32)

            // Run model inference
            try {
                tflite.run(inputBuffer, outputBuffer.buffer.rewind())
                // Get the output from the model
                val output = outputBuffer.floatArray
                modelOutput.value = output.joinToString(separator = "; ") { "%.2f".format(it) }

                // Log output for debugging
                Log.d("ModelOutput", modelOutput.value ?: "No output")
            } catch (e: Exception) {
                Log.e("ModelInference", "Error during model inference", e)
                modelOutput.value = "Error during model inference"
            }
        }
    }

    private fun parseModelOutput(output: String): List<Pair<String, Float>> {
        val probabilities = output.replace(",", ".").split("; ").mapNotNull {
            try {
                it.toFloat()
            } catch (e: NumberFormatException) {
                null
            }
        }
        val emojiList = listOf("❤️", "😃", "😍", "😵", "👍")
        return probabilities.mapIndexed { index, probability ->
            emojiList.getOrElse(index) { "?" } to probability
        }.sortedByDescending { it.second }.take(3)
    }

    @Composable
    fun displayResults(results: List<Pair<String, Float>>, selectedEmoji: String) {
        val primaryResult = results.firstOrNull()
        val confidence = primaryResult?.second ?: 0f
        val message = when {
            primaryResult?.first == selectedEmoji -> when {
                confidence > 0.85 -> "Amazing!!! This was too easy for you!"
                confidence > 0.70 -> "Almost perfect! What a talent!"
                confidence > 0.55 -> "Great job! Keep it up!"
                confidence > 0.40 -> "Good attempt, but you can do better!"
                confidence > 0.25 -> "Nice try, but let's aim for higher!"
                else -> "Not quite there. Keep practicing!"
            }
            else -> when {
                confidence < 0.15 -> "Come on, you can do better than that. We believe in you!"
                confidence < 0.30 -> "Almost there, better luck next time!"
                confidence < 0.50 -> "That was just unlucky, you'll do it next time for sure!"
                else -> "Keep trying, you'll get it right!"
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, fontSize = 24.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                results.forEachIndexed { index, result ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(text = "${result.first}", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "${(result.second * 100).toInt()}%", fontSize = 20.sp)
                    }
                }
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
