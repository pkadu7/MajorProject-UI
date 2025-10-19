package com.example.uim

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.uim.ui.theme.UimTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UimTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoiceAssistantUI()
                }
            }
        }
    }
}

@Composable
fun VoiceAssistantUI() {
    var state by remember { mutableStateOf("Ready") }
    var message by remember { mutableStateOf("Say 'Hey Assistant'") }
    var showCamera by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // --- TTS Setup ---
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

    LaunchedEffect(Unit) {
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = java.util.Locale.US
            }
        }
    }

    fun speak(text: String) {
        ttsInstance?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
        }
    }

    // --- Permissions ---
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) message = "Microphone permission is required!"
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) message = "Camera permission is required!"
    }

    LaunchedEffect(Unit) {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // --- Speech Recognizer ---
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    fun startListening() {
        recognizer.cancel()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.forEach { result ->
                    val text = result.trim().lowercase()
                    if ("hey assistant" in text && state == "Ready") {
                        state = "Listening"
                        message = "Listening..."
                        speak("How can I help you?")
                    }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Handler(context.mainLooper).postDelayed({
                    recognizer.startListening(intent)
                }, 200)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.forEach { result ->
                    val text = result.trim().lowercase()
                    when {
                        "hey assistant" in text && state == "Ready" -> {
                            state = "Listening"
                            message = "Listening..."
                            speak("How can I help you?")
                        }
                        "open the camera" in text -> {
                            showCamera = true
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                != PackageManager.PERMISSION_GRANTED
                            ) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                message = "Camera Opened"
                                speak("Camera is open now")
                            }
                        }
                    }
                }
                recognizer.startListening(intent)
            }
        })
        recognizer.startListening(intent)
    }

    // --- State Transitions ---
    LaunchedEffect(state) {
        when (state) {
            "Listening" -> {
                delay(3000)
                state = "Thinking"
                message = "Thinking..."
            }
            "Thinking" -> {
                delay(1500)
                state = "Ready"
                message = "Say 'Hey Assistant'"
            }
        }
    }

    // --- UI ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 40.dp)
        )

        ModernMicButton(state = state) {
            if (state == "Ready") {
                state = "Listening"
                message = "Listening..."
                speak("How can I help you?")
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startListening()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (showCamera) {
            CameraPreview()
        }
    }
}

@Composable
fun CameraPreview() {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize()) { view ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                // TODO: Send imageProxy to your ML model later
                imageProxy.close()
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(context as ComponentActivity, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

@Composable
fun ModernMicButton(state: String, onMicClick: () -> Unit) {
    val pulse = rememberInfiniteTransition()
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse)
    )

    Box(
        modifier = Modifier
            .size(180.dp)
            .drawBehind {
                if (state == "Listening" || state == "Thinking") {
                    drawCircle(
                        color = when (state) {
                            "Listening" -> Color(0xFF00C853)
                            "Thinking" -> Color(0xFFFFD700)
                            else -> Color.Gray
                        }.copy(alpha = pulseAlpha),
                        radius = size.minDimension / 2 + 20f,
                        center = Offset(size.width / 2, size.height / 2)
                    )
                }
            }
            .shadow(elevation = 12.dp, shape = CircleShape)
            .background(
                color = when (state) {
                    "Ready" -> Color.Gray
                    "Listening" -> Color(0xFF00C853)
                    "Thinking" -> Color(0xFFFFD700)
                    else -> Color.Gray
                },
                shape = CircleShape
            )
            .clickable { onMicClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("🎤", fontSize = 60.sp)
    }
}
