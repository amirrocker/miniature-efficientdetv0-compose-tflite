package de.amirrocker.material3imageclassification.camera

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Lens
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.amirrocker.material3imageclassification.classification.ImageClassifierHelper
import de.amirrocker.material3imageclassification.detection.ObjectDetectionHelper
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun CameraView(
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    onClassificationResults: (results: List<Classifications>) -> Unit,
    onDetectionResults: (results: List<Detection>) -> Unit
) {

    val detectedObjects = remember {
        mutableStateOf<List<Detection>?>(emptyList())
    }

    val listener = object : ImageClassifierHelper.ImageClassifierListener {
        override fun onError(error: String) {
            error("classification helper failed with error: $error")
        }

        override fun onResults(results: List<Classifications>?, inferenceTime: Long) {
            println("classification results: $results")
            onClassificationResults(results ?: emptyList())

        }
    }

    val objectDetectionListener = object : ObjectDetectionHelper.DetectionListener {
        override fun onError(error: String) {
            println("on error: $error")
        }

        override fun onResults(
            results: MutableList<Detection>?,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int
        ) {
            println("onResults: $results")

            onDetectionResults(results ?: emptyList())

            detectedObjects.value = results

        }
    }

    // for now only back cam is supported.
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // classifier
    val imageClassifierHelper = ImageClassifierHelper(
        context = context,
        imageClassifierListener = listener
    )
    // object detector
    val objectDetectionHelper = ObjectDetectionHelper(
        context = context,
        objectDetectionListener = objectDetectionListener
    )

    lateinit var bitmapBuffer: Bitmap

    // setup the needed view components
    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

    // classification
    val imageAnalyzer = ImageAnalysis.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
        .setTargetRotation((context as Activity).display?.rotation ?: 0)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        // assign the analyzer to the instance
        .also {
            it.setAnalyzer(executor) { image ->
                bitmapBuffer = Bitmap.createBitmap(
                    image.width,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                // copy out the rgb bits to shared bitmap buffer!
                image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

                // TODO find a better solution to switch between classification and detection
                //      should be inside the settings together with choice of model
                // pass the bitmap and rotation to classifier helper for classification
                // imageClassifierHelper.classify(bitmapBuffer, getScreenOrientation(context))
                //
                val imageRotation = image.imageInfo.rotationDegrees
                objectDetectionHelper.detect(bitmapBuffer, imageRotation)
            }
        }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
            imageAnalyzer
        )
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    // now create the UI
    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        detectedObjects.value?.let {
            if (it.isNotEmpty()) {
                it.forEachIndexed { index: Int, detection: Detection ->
                    Spacer(
                        modifier = Modifier
                            .drawWithCache {
                                val width = detection.boundingBox.width()
                                val height = detection.boundingBox.height()
                                val xPos = detection.boundingBox.left
                                val yPos = detection.boundingBox.top

                                val path = Path()
                                path.moveTo(xPos, yPos)
                                path.lineTo(xPos + width, yPos)
                                path.lineTo(xPos + width, yPos + height)
                                path.lineTo(xPos, yPos + height)
                                path.lineTo(xPos, yPos)
                                path.close()

                                onDrawBehind {
                                    drawPath(
                                        path = path,
                                        color = Color.Yellow,
                                        style = Stroke(width = 4f)
                                    )
                                }
                            }
                            .fillMaxSize()
                    )
                }
            }
        }

        IconButton(
            modifier = Modifier.padding(bottom = 20.dp),
            onClick = {
                println("click")
                takePhoto(
                    filenameFormat = "yyyy-MM-dd-HH-mm-ss-SSS",
                    imageCapture = imageCapture,
                    outputDirectory = outputDirectory,
                    executor = executor,
                    onImageCaptured = onImageCaptured,
                    onError = onError
                )
            },
            content = {
                Icon(
                    imageVector = Icons.Sharp.Lens,
                    contentDescription = "Make a photo",
                    tint = Color.White,
                    modifier = Modifier
                        .size(100.dp)
                        .padding(1.dp)
                        .border(1.dp, Color.White, CircleShape)
                )
            }
        )
    }
}


fun takePhoto(
    filenameFormat: String,
    imageCapture: ImageCapture,
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {

    val photoFile = createFotoFile(
        outputDirectory = outputDirectory,
        filenameFormat = filenameFormat
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                onImageCaptured(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
                error(exception)
            }
        })
}

private const val FILE_EXTENSION_JPG = ".jpg"
private const val FILE_EXTENSION_PNG = ".png"

private fun createFotoFile(outputDirectory: File, filenameFormat: String) =
    File(
        outputDirectory,
        SimpleDateFormat(
            filenameFormat, Locale.getDefault()
        )
            .format(
                System.currentTimeMillis()
            ) + FILE_EXTENSION_JPG
    )

/**
 * a small utility function that makes it easier to access the ProcessCameraProvider.
 * The ProcessCameraProvider is an asynchronous process that needs to be suspended.
 */
private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }

private fun getScreenOrientation(context: Context): Int {
    val outMetrics = DisplayMetrics()
    val display: Display?


    if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.R) {
        display = context.display
        display?.getRealMetrics(outMetrics)
    } else {
        display = (context as Activity).windowManager.defaultDisplay
        display.getMetrics(outMetrics)
    }
    return display?.rotation ?: 0
}


/*

    trash bin

    it.forEachIndexed { index: Int, detection: Detection ->
//                        val width = detection.boundingBox.width()
//                        val height = detection.boundingBox.height()
//                        val xPos = detection.boundingBox.left
//                        val yPos = detection.boundingBox.top
//
//                        val path = Path()
//                        path.moveTo(xPos, yPos)
//                        path.lineTo(xPos+width, yPos)
//                        path.lineTo(xPos+width, yPos+height)
//                        path.lineTo(xPos, yPos+height)
//                        path.lineTo(xPos, yPos)
//                        onDrawBehind {
//
//                        }
//                        drawPath(
//                            path = path,
//                            brush =  // SolidColor(Color.Yellow)
//                        )
//
////                        drawRect(
////                            color = Color.Red,
////                            alpha = .3f,
////                            size = Size(width, height),
////                            topLeft = Offset(xPos, yPos)
////                        )
//                    }
                }

 */
