package com.example.cardnumberdetector


import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@ExperimentalGetImage
class MainActivity : AppCompatActivity() {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraSelector: CameraSelector
    private lateinit var graphicOverlay: GraphicOverlay
    private var found = false
    private var _width = 0
    private var _height = 0
    private var newText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.CAMERA
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    report.let {
                        if (report.areAllPermissionsGranted()) {
                            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest>,
                    token: PermissionToken
                ) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            }).check()

        cameraExecutor = Executors.newSingleThreadExecutor()
        graphicOverlay = findViewById(R.id.graphicOverlay)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.cameraPreview).surfaceProvider)

                }


            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, YourImageAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
//                flashFlow
//                    .onEach { camera.cameraControl.enableTorch(it) }
//                    .launchIn(lifecycleScope)
            } catch (exc: Exception) {
                Log.e("TTT", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    @ExperimentalGetImage
    private inner class YourImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            Log.d("PassportScannerScreen", "mediaImage 1")
            if (mediaImage != null) {
                _width = mediaImage.width
                _height = mediaImage.height
                Log.d("PassportScannerScreen", "mediaImage 2")

                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener {
                        it.textBlocks.forEach {
                            it.lines.forEach {
                                if (it.text.startsWith("8600") && !found && it.text.length == 19) {
//                                    it.elements.forEach {
//                                        graphicOverlay.clear()
//                                        val textGraphic = TextGraphic(graphicOverlay, it)
//                                        graphicOverlay.add(textGraphic)
//                                    }
//                                    Toast.makeText(this@MainActivity, "Reading...", Toast.LENGTH_SHORT).show()
                                    found = true
                                    openDialog(it.text)
                                }
                            }
                        }
                    }
                    .addOnCanceledListener {
                        Log.d("PassportScannerScreen", "addOnCanceledListener")
                    }
                    .addOnCompleteListener {
                        Log.d("PassportScannerScreen", "addOnCompleteListener=$it")
                        imageProxy.close()
                    }
                    .addOnFailureListener {
                        Log.d("PassportScannerScreen", "addOnFailureListener=$it")
                    }
            }
        }
    }

    fun openDialog(cardNumber: String) {
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Your card number")
        builder.setMessage(cardNumber)
        builder.setCancelable(false)
        builder.setPositiveButton("Copy") { dialog, which ->
            found = false
            val clipboard: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("Copied", cardNumber)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Card number copied", Toast.LENGTH_SHORT).show()
        }
        builder.show()
    }

}