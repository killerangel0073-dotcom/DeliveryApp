package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun PantallaCamaraGenerica(
    navController: NavController,
    tituloGuia: String,
    onPhotoCaptured: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedFile by remember { mutableStateOf<File?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (capturedFile == null) {
            // --- VISTA DE CÁMARA ---
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                    .build()
                                imageCapture = capture
                                
                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner, 
                                    cameraSelector, 
                                    preview, 
                                    capture
                                )
                            } catch (exc: Exception) {
                                Log.e("CamaraGenerica", "Fallo al iniciar cámara", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        
                        previewView
                    }
                )

                // 2. GUÍA VISUAL
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    val rectWidth = canvasWidth * 0.90f
                    val rectHeight = rectWidth * 0.63f
                    val left = (canvasWidth - rectWidth) / 2
                    val top = (canvasHeight - rectHeight) / 2.5f
                    
                    drawRect(
                        color = Color.Black.copy(alpha = 0.7f),
                        size = Size(canvasWidth, canvasHeight)
                    )

                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(left, top),
                        size = Size(rectWidth, rectHeight),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )

                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = Size(rectWidth, rectHeight),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // 3. TEXTOS
                Column(
                    modifier = Modifier.fillMaxSize().padding(bottom = 140.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = Color.Black.copy(0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            tituloGuia.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                // 4. BOTONES
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .padding(24.dp)
                        .align(Alignment.TopStart)
                        .zIndex(10f)
                        .background(Color.Black.copy(0.4f), CircleShape)
                        .size(48.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 50.dp)
                        .zIndex(10f)
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(60.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(85.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable {
                                    if (imageCapture != null) {
                                        isCapturing = true
                                        takePhotoAndCropGeneric(
                                            context,
                                            imageCapture,
                                            cameraExecutor,
                                            onPhotoCaptured = {
                                                isCapturing = false
                                                capturedFile = it
                                            },
                                            onError = {
                                                isCapturing = false
                                                Log.e("CamaraGenerica", "Error: $it")
                                            }
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(70.dp)
                                    .border(3.dp, Color.Red, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Camera, 
                                    null, 
                                    tint = Color.Red, 
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // --- VISTA DE PREVIA ---
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = capturedFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "¿La fotografía es legible?", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = { 
                                capturedFile?.delete()
                                capturedFile = null 
                            },
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("REPETIR", color = Color.White, fontWeight = FontWeight.Black)
                        }
                        
                        Button(
                            onClick = { 
                                capturedFile?.let { onPhotoCaptured(it) } 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ACEPTAR", color = Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

private fun takePhotoAndCropGeneric(
    context: Context,
    imageCapture: ImageCapture?,
    executor: ExecutorService,
    onPhotoCaptured: (File) -> Unit,
    onError: (String) -> Unit
) {
    val capture = imageCapture ?: return
    val photoFile = File(context.cacheDir, "temp_doc.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    capture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val exifInterface = ExifInterface(photoFile.absolutePath)
                    val orientation = exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, 
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    val rotationDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }

                    val originalBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val matrix = Matrix()
                    if (rotationDegrees != 0) matrix.postRotate(rotationDegrees.toFloat())
                    
                    val rotatedBitmap = Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )

                    val w = rotatedBitmap.width
                    val h = rotatedBitmap.height
                    
                    val cropW = (w * 0.90).toInt()
                    val cropH = (cropW * 0.63).toInt()
                    val x = (w - cropW) / 2
                    val y = (h - cropH) / 2.5f 
                    
                    val cropped = Bitmap.createBitmap(
                        rotatedBitmap, 
                        x.coerceAtLeast(0), 
                        y.toInt().coerceAtLeast(0), 
                        cropW.coerceAtMost(w - x), 
                        cropH.coerceAtMost(h - y.toInt())
                    )
                    
                    val resultFile = File(context.cacheDir, "doc_final_${System.currentTimeMillis()}.jpg")
                    val out = FileOutputStream(resultFile)
                    cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    out.close()
                    
                    photoFile.delete()
                    onPhotoCaptured(resultFile)
                } catch (e: Exception) {
                    onError(e.message ?: "Error processing")
                }
            }

            override fun onError(exc: ImageCaptureException) {
                onError(exc.message ?: "Capture error")
            }
        }
    )
}
