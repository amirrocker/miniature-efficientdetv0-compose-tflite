package de.amirrocker.material3imageclassification

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import de.amirrocker.material3imageclassification.camera.CameraView
import de.amirrocker.material3imageclassification.component.*
import de.amirrocker.material3imageclassification.ui.theme.Material3ImageClassificationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : ComponentActivity() {

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textToSpeech: TextToSpeech
    private var shouldShowCamera: MutableState<Boolean> = mutableStateOf(false)

    private var currentUtteranceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCameraPermission()
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        val utteranceProgressListener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                println("started talking for id: $utteranceId")
                currentUtteranceId = utteranceId
            }

            override fun onDone(utteranceId: String?) {
                currentUtteranceId = null
                println("Done talking for id: $utteranceId")
            }

            override fun onError(utteranceId: String?) {
                println("error while talking for id: $utteranceId")
            }
        }

        val listener = TextToSpeech.OnInitListener { status ->

            if (status != TextToSpeech.ERROR) {
                // To Choose language of speech
                textToSpeech.setLanguage(Locale.US)
            }
        }

        textToSpeech = TextToSpeech(applicationContext, listener)
        textToSpeech.setOnUtteranceCompletedListener { utteranceId -> println("utteranceId: $utteranceId") }
        textToSpeech.setOnUtteranceProgressListener(utteranceProgressListener)

        setContent {
            Material3ImageClassificationTheme {
                MainContent()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent() {

        val results = remember {
            mutableStateOf(listOf<Classifications>())
        }
        val detectionResults = remember {
            mutableStateOf(listOf<Detection>())
        }

        val bottomSheetScaffoldState =
            rememberBottomSheetScaffoldState(bottomSheetState = BottomSheetState(BottomSheetValue.Collapsed))
        val coroutineScope = rememberCoroutineScope()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "Top Appbar Placeholder", color = Color.Red) },
                    actions = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (bottomSheetScaffoldState.bottomSheetState.isCollapsed) {
                                        bottomSheetScaffoldState.bottomSheetState.expand()
                                    } else {
                                        bottomSheetScaffoldState.bottomSheetState.collapse()
                                    }
                                }
                            }
                        ) {
                            Text(text = "Click Me", color = Color.White)
                        }
                    }
                )
            },
            content = {
                MyContent(bottomSheetScaffoldState, coroutineScope, results, detectionResults, it)
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyContent(
        bottomSheetScaffoldState: BottomSheetScaffoldState,
        coroutineScope: CoroutineScope,
        results: MutableState<List<Classifications>>,
        detectionResults: MutableState<List<Detection>>,
        paddingValues: PaddingValues
    ) {

        suspend fun handleClassificationResults(newResults: List<Classifications>) {
            println("classification results: $results")
            if (bottomSheetScaffoldState.bottomSheetState.isCollapsed) {
                bottomSheetScaffoldState.bottomSheetState.expand()
            }
            results.value = newResults
        }

        suspend fun handleDetectionResults(newResults: List<Detection>) {
            println("detection results: $detectionResults")
//            if (bottomSheetScaffoldState.bottomSheetState.isCollapsed) {
//                bottomSheetScaffoldState.bottomSheetState.expand()
//            }
            detectionResults.value = newResults
        }

        BottomSheetScaffold(
            scaffoldState = bottomSheetScaffoldState,
            sheetContent = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFF333333))
                ) {

                    if (results.value.isNotEmpty()) {
                        LazyColumn {
                            items(results.value.count()) { index: Int ->
                                val item = results.value[index]
                                Text(
                                    "item #$index is at headindex: ${item.headIndex}",
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Text(
                                    "Number of categories are ${item.categories.size} ",
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Text(
                                    "item category is ${item.categories.firstOrNull()?.label ?: "Unknown"}",
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Text(
                                    "item score is ${item.categories.firstOrNull()?.score ?: "Unknown"} ",
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Text(
                                    "item index is ${item.categories.firstOrNull()?.index ?: "Unknown"} ",
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Text(
                                    "------------------------",
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                            }
                        }

                        val maxScoreResult =
                            results.value.maxWith(Comparator.comparingDouble { item ->
                                item.categories.firstOrNull()?.score?.toDouble() ?: 0.0
                            })

                        val label = maxScoreResult.categories.firstOrNull()?.label ?: ""

                        val myHashAlarm = mutableMapOf(
                            TextToSpeech.Engine.KEY_PARAM_STREAM to AudioManager.STREAM_ALARM.toString(),
                            TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to if (label.isNotEmpty()) label else "Void"
                        )

                        if (currentUtteranceId == null) {
                            textToSpeech.speak(
                                label,
                                TextToSpeech.QUEUE_FLUSH,
                                myHashAlarm as java.util.HashMap<String, String>
                            );
                        }

                    } else {
                        Text(text = "No results found .... ", fontSize = 20.sp, color = Color.White)
                    }
                }
            },
            sheetPeekHeight = 0.dp
        ) {

            Column(
                Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center
            ) {

                if (shouldShowCamera.value) {
                    CameraView(
                        outputDirectory = outputDirectory,
                        executor = cameraExecutor,
                        onImageCaptured = ::handleImageCapture,
                        onError = { imageCaptureException ->
                            error("view error: $imageCaptureException")
                        },
                        onClassificationResults = { results ->
                            coroutineScope.launch {
                                handleClassificationResults(newResults = results)
                            }
                        },
                        onDetectionResults = { results ->
                            coroutineScope.launch {
                                handleDetectionResults(newResults = results)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun handleImageCapture(uri: Uri) {
        println("Image captured at $uri")
        shouldShowCamera.value = false
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("MainActivity", "Permission granted")
            shouldShowCamera.value = true
        } else {
            Log.i("MainActivity", "Permission denied")
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i("MainActivity", "Permission previously granted")
                shouldShowCamera.value = true
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> Log.i("MainActivity", "Show camera permissions dialog")

            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Material3ImageClassificationTheme {
        Greeting("Android")
    }
}