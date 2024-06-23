package hr.tvz.android.doodleemoji

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
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
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GameActivity : ComponentActivity() {
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tflite: Interpreter
    private lateinit var objectDetectionTflite: Interpreter
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance("https://doodleemoji-default-rtdb.europe-west1.firebasedatabase.app/")

        // Load the selected model
        val selectedModel = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("selected_model", "doodle_emoji_vgg16_model_86.tflite") ?: "doodle_emoji_vgg16_model_86.tflite"

        // Load TFLite model
        val tfliteModel = loadModelFile(selectedModel)
        tflite = Interpreter(tfliteModel)

        // Load Object Detection Model
        val objectDetectionModel = loadModelFile("doodle_emoji_mobilenetv2_object_recognition_78.tflite")
        objectDetectionTflite = Interpreter(objectDetectionModel)

        setContent {
            DoodleEmojiTheme {
                GameScreen()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val resourceId = resources.getIdentifier(modelName.split(".")[0], "raw", packageName)
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
        objectDetectionTflite.close()
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
        val showModelDialog = remember { mutableStateOf(false) }

        if (showModelDialog.value) {
            ModelSelectionDialog(showDialog = showModelDialog, onModelSelected = { modelName ->
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                    .putString("selected_model", modelName)
                    .apply()
                showModelDialog.value = false
            })
        }

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

            if (!showEmoji.value) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Settings",
                    modifier = Modifier
                        .padding(16.dp)
                        .size(32.dp)
                        .clickable { showModelDialog.value = true }
                        .align(Alignment.TopEnd)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 56.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!showEmoji.value) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFBBDEFB), shape = RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(text = stringResource(R.string.draw_the_emoji), fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showEmoji.value = true
                            startFirstTimer(timerValue, cameraUnlocked)
                        },
                        modifier = Modifier
                            .size(144.dp)
                            .clip(CircleShape)
                    ) {
                        Text(text = stringResource(R.string.start), fontSize = 40.sp)
                    }
                } else {
                    if (!cameraUnlocked.value) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFBBDEFB), shape = RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text(text = timerValue.value.toString(), fontSize = 48.sp)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = selectedEmoji.value,
                            fontSize = 150.sp
                        )
                    } else {
                        if (imageUri.value == null && allowCapture.value) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFBBDEFB), shape = RoundedCornerShape(12.dp))
                                        .padding(16.dp)
                                ) {
                                    Text(text = secondTimerValue.value.toString(), fontSize = 48.sp)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .padding(16.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Text(text = selectedEmoji.value, fontSize = 48.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFBBDEFB), shape = RoundedCornerShape(12.dp))
                                    .padding(8.dp) // Smanjeni padding
                            ) {
                                Text(
                                    text = stringResource(R.string.quick_take_picture),
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(top = 5.dp) // Povećani padding
                                )
                            }
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
                                            capturePhoto(context, lifecycleOwner, imageUri, allowCapture, capturedBitmap, modelOutput, selectedEmoji.value)
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
                                startSecondTimer(secondTimerValue, allowCapture, selectedEmoji.value)
                            }
                        } else if (imageUri.value != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                modelOutput.value?.let { output ->
                                    val results = parseModelOutput(output)
                                    DisplayResultsContainer(
                                        results = results,
                                        message = getMessageWithBackground(results.firstOrNull()?.second ?: 0f, results.firstOrNull()?.first == selectedEmoji.value),
                                        capturedBitmap = capturedBitmap.value,
                                        onPlayAgainClick = {
                                            resetGame(selectedEmoji, showEmoji, timerValue, cameraUnlocked, secondTimerValue, imageUri, allowCapture, capturedBitmap, modelOutput)
                                        }
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.try_quicker_next_time),
                                    fontSize = 24.sp,
                                    modifier = Modifier
                                        .background(Color(0xFFBBDEFB), shape = RoundedCornerShape(12.dp))
                                        .padding(16.dp)
                                )
                                Text(
                                    text = "😵",
                                    fontSize = 150.sp,
                                    modifier = Modifier.padding(top = 20.dp)
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = {
                                    resetGame(selectedEmoji, showEmoji, timerValue, cameraUnlocked, secondTimerValue, imageUri, allowCapture, capturedBitmap, modelOutput)
                                }) {
                                    Text(text = stringResource(R.string.play_again))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resetGame(
        selectedEmoji: MutableState<String>,
        showEmoji: MutableState<Boolean>,
        timerValue: MutableState<Int>,
        cameraUnlocked: MutableState<Boolean>,
        secondTimerValue: MutableState<Int>,
        imageUri: MutableState<String?>,
        allowCapture: MutableState<Boolean>,
        capturedBitmap: MutableState<Bitmap?>,
        modelOutput: MutableState<String?>
    ) {
        val emojiList = listOf("❤️", "😃", "😍", "😵", "👍")
        selectedEmoji.value = emojiList.random()
        showEmoji.value = false
        timerValue.value = 10
        cameraUnlocked.value = false
        secondTimerValue.value = 5
        imageUri.value = null
        allowCapture.value = true
        capturedBitmap.value = null
        modelOutput.value = null
    }

    @Composable
    fun ModelSelectionDialog(showDialog: MutableState<Boolean>, onModelSelected: (String) -> Unit) {
        val context = LocalContext.current
        val rawResources = context.resources.obtainTypedArray(R.array.models)
        val modelFiles = (0 until rawResources.length()).map { index ->
            context.resources.getResourceEntryName(rawResources.getResourceId(index, 0))
        }
        rawResources.recycle()

        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text(text = stringResource(R.string.select_model)) },
            text = {
                Column {
                    modelFiles.forEach { model ->
                        Text(
                            text = model,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable {
                                    onModelSelected(model)
                                    showDialog.value = false
                                }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDialog.value = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
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

    private fun startSecondTimer(secondTimerValue: MutableState<Int>, allowCapture: MutableState<Boolean>, selectedEmoji: String) {
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondTimerValue.value = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                secondTimerValue.value = 0
                allowCapture.value = false

                val user = auth.currentUser
                if (user != null) {
                    val userId = user.uid
                    val emoji = selectedEmoji
                    val userRef = database.getReference("users").child(userId).child("stats").child(emoji)

                    userRef.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val stats = currentData.getValue(EmojiStats::class.java) ?: EmojiStats()
                            stats.attempts += 1
                            currentData.value = stats
                            return Transaction.success(currentData)
                        }

                        override fun onComplete(
                            error: DatabaseError?,
                            committed: Boolean,
                            currentData: DataSnapshot?
                        ) {
                            if (error != null) {
                                Log.e("DatabaseError", "Error updating stats: ${error.message}")
                            }
                        }
                    })
                }
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
        modelOutput: MutableState<String?>,
        selectedEmoji: String
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
                                processImageWithModel(capturedBitmap.value, modelOutput, selectedEmoji, capturedBitmap)
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

    private fun processImageWithModel(bitmap: Bitmap?, modelOutput: MutableState<String?>, selectedEmoji: String, capturedBitmap: MutableState<Bitmap?>) {
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

                // Convert output to List<Pair<String, Float>>
                val emojiList = listOf("❤️", "😃", "😍", "😵", "👍")
                val results = output.mapIndexed { index, probability ->
                    emojiList.getOrElse(index) { "?" } to probability
                }

                // Update stats in the database
                val user = auth.currentUser
                if (user != null) {
                    updateStats(user.uid, selectedEmoji, results)
                }

                // Process object detection on the image
                processObjectDetection(bitmap, results, capturedBitmap)
            } catch (e: Exception) {
                Log.e("ModelInference", "Error during model inference", e)
                modelOutput.value = "Error during model inference"
            }
        }
    }

    private fun processObjectDetection(bitmap: Bitmap, results: List<Pair<String, Float>>, capturedBitmap: MutableState<Bitmap?>) {
        Log.d("ObjectDetection", "Starting object detection")

        // Prepare the bitmap for the object detection model
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
        val tensorImage = TensorImage.fromBitmap(resizedBitmap)

        Log.d("ObjectDetection", "Resized bitmap dimensions: ${resizedBitmap.width}x${resizedBitmap.height}")

        // Normalize the image to [0, 1] range by dividing by 255.0
        val normalizedBuffer = FloatBuffer.allocate(1 * 128 * 128 * 3)
        for (y in 0 until resizedBitmap.height) {
            for (x in 0 until resizedBitmap.width) {
                val pixel = resizedBitmap.getPixel(x, y)
                normalizedBuffer.put((pixel shr 16 and 0xFF) / 255.0f)
                normalizedBuffer.put((pixel shr 8 and 0xFF) / 255.0f)
                normalizedBuffer.put((pixel and 0xFF) / 255.0f)
            }
        }
        normalizedBuffer.rewind()

        Log.d("ObjectDetection", "Input buffer prepared. FloatBuffer capacity: ${normalizedBuffer.capacity()}")

        // Prepare output buffer
        val outputLocations = TensorBuffer.createFixedSize(intArrayOf(1, 4), org.tensorflow.lite.DataType.FLOAT32) // Assuming one output tensor for bounding box coordinates

        // Run object detection model inference
        val outputMap = mapOf(
            0 to outputLocations.buffer.rewind()
        )

        try {
            objectDetectionTflite.runForMultipleInputsOutputs(arrayOf(normalizedBuffer), outputMap)
            Log.d("ObjectDetection", "Model inference completed")

            // Get the detected objects
            val detectedLocations = outputLocations.floatArray
            Log.d("ObjectDetection", "Detected locations: ${detectedLocations.joinToString()}")

            // Use the highest score prediction from the classification model
            val topResult = results.maxByOrNull { it.second }
            if (topResult != null) {
                val topEmoji = topResult.first
                Log.d("ObjectDetection", "Top emoji: $topEmoji")

                overlayEmojiOnBitmap(bitmap, topEmoji, detectedLocations, capturedBitmap)
                Log.d("ObjectDetection", "Emoji overlay completed")
            } else {
                Log.d("ObjectDetection", "No top result found in classification results")
            }
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error during object detection inference", e)
        }
    }


    private fun overlayEmojiOnBitmap(
        bitmap: Bitmap,
        emoji: String,
        location: FloatArray,
        capturedBitmap: MutableState<Bitmap?>,
    ) {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // Skališemo relativne koordinate prema veličini originalne slike
        val left = location[1] * bitmap.width
        val top = location[0] * bitmap.height
        val right = location[3] * bitmap.width
        val bottom = location[2] * bitmap.height

        // Izračunavanje veličine okvira
        val boxWidth = right - left
        val boxHeight = bottom - top

        // Prilagođavanje veličine teksta tako da se uklapa u okvir, ali malo manji od visine okvira
        val paint = Paint().apply {
            textSize = boxHeight * 0.8f // Prilagođavanje veličine teksta na 80% visine okvira
            color = 234
        }

        // Centriramo emotikon unutar prepoznatog okvira
        val centerX = left + (boxWidth / 2)
        val centerY = top + (boxHeight / 2)

        // Prilagođavanje pozicije da emotikon bude centriran
        val textWidth = paint.measureText(emoji)
        val textHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent
        val textX = centerX - (textWidth / 2)
        val textY = centerY + (textHeight / 2) - (boxHeight * 0.15f) // Pomakni gore za 15% visine okvira

        // Postavite početnu alpha vrijednost na 0
        paint.alpha = 0
        canvas.drawText(emoji, textX, textY, paint)

        // Update the bitmap with the overlay
        capturedBitmap.value = mutableBitmap

        // Animirajte alpha vrijednost od 0 do 255 kroz 2.5 sekunde
        val handler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val duration = 2500

        handler.post(object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                paint.alpha = (progress * 255).toInt()
                val updatedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val updatedCanvas = Canvas(updatedBitmap)
                updatedCanvas.drawBitmap(bitmap, 0f, 0f, null)
                updatedCanvas.drawText(emoji, textX, textY, paint)
                capturedBitmap.value = updatedBitmap
                if (progress < 1f) {
                    handler.postDelayed(this, 16) // Ponovno izvršavanje svakih 16ms
                }
            }
        })
    }


    private fun updateStats(userId: String, selectedEmoji: String, results: List<Pair<String, Float>>) {
        val userRef = database.getReference("users").child(userId).child("stats").child(selectedEmoji)

        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val stats = currentData.getValue(EmojiStats::class.java) ?: EmojiStats()
                // stats.attempts += 1 ---> this is already done with second timer

                // Pronalaženje emojija s najvećom vjerojatnošću
                val topResult = results.maxByOrNull { it.second }
                // Ažuriranje broja točnih pokušaja ako je prepoznati emoji jednak ciljanom emojiju
                if (topResult != null && topResult.first == selectedEmoji) {
                    stats.successes += 1
                }

                currentData.value = stats
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e("DatabaseError", "Error updating stats: ${error.message}")
                }
            }
        })
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
    private fun getMessage(confidence: Float, isCorrect: Boolean): String {
        return if (isCorrect) {
            when {
                confidence > 0.85 -> "${stringResource(R.string.amazing)} 🎉"
                confidence > 0.70 -> "${stringResource(R.string.almost_perfect)} 😊"
                confidence > 0.55 -> "${stringResource(R.string.great_job)} 😀"
                confidence > 0.40 -> "${stringResource(R.string.good_attempt)} 🙂"
                confidence > 0.25 -> "${stringResource(R.string.nice_try)} 😅"
                else -> "${stringResource(R.string.not_quite_there)} 😕"
            }
        } else {
            when {
                confidence < 0.15 -> "${stringResource(R.string.come_on_better)} 💩"
                confidence < 0.30 -> "${stringResource(R.string.almost_there)} 😬"
                confidence < 0.50 -> "${stringResource(R.string.unlucky)} 😢"
                else -> "${stringResource(R.string.keep_trying)} 😐"
            }
        }
    }

    @Composable
    private fun getMessageWithBackground(confidence: Float, isCorrect: Boolean): String {
        return if (isCorrect) {
            when {
                confidence > 0.85 -> "${stringResource(R.string.amazing)} 🎉"
                confidence > 0.70 -> "${stringResource(R.string.almost_perfect)} 😊"
                confidence > 0.55 -> "${stringResource(R.string.great_job)} 😀"
                confidence > 0.40 -> "${stringResource(R.string.good_attempt)} 🙂"
                confidence > 0.25 -> "${stringResource(R.string.nice_try)} 😅"
                else -> "${stringResource(R.string.not_quite_there)} 😕"
            }
        } else {
            when {
                confidence < 0.15 -> "${stringResource(R.string.come_on_better)} 💩"
                confidence < 0.30 -> "${stringResource(R.string.almost_there)} 😬"
                confidence < 0.50 -> "${stringResource(R.string.unlucky)} 😢"
                else -> "${stringResource(R.string.keep_trying)} 😐"
            }
        }
    }

    @Composable
    fun DisplayResultsContainer(results: List<Pair<String, Float>>, message: String, capturedBitmap: Bitmap?, onPlayAgainClick: () -> Unit) {
        val animatedVisibilityState = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            animatedVisibilityState.value = true
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFBBDEFB), shape = RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = message,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            capturedBitmap?.let {
                Image(
                    painter = BitmapPainter(it.asImageBitmap()),
                    contentDescription = "Captured image",
                    modifier = Modifier.size(256.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = animatedVisibilityState.value,
                enter = fadeIn(initialAlpha = 0f, animationSpec = tween(durationMillis = 1000))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    results.forEachIndexed { index, result ->
                        val fontSize = when (index) {
                            0 -> 48.sp // Largest for the first prediction
                            1 -> 32.sp // Medium for the second prediction
                            2 -> 24.sp // Smallest for the third prediction
                            else -> 24.sp
                        }
                        val percentageFontSize = fontSize * 2 / 3

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                        ) {
                            Text(text = result.first, fontSize = fontSize, textAlign = TextAlign.Center)
                            Text(text = "${(result.second * 100).toInt()}%", fontSize = percentageFontSize, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onPlayAgainClick,
                modifier = Modifier.size(width = 200.dp, height = 56.dp) // Larger button
            ) {
                Text(text = stringResource(R.string.play_again))
            }
        }
    }



    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
