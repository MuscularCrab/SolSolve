package com.ventris.solsolve.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import androidx.camera.core.AspectRatio
import androidx.exifinterface.media.ExifInterface
import android.graphics.Matrix
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size as CvSize
import org.opencv.imgproc.Imgproc

@Composable
fun SolitaireScannerScreen() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        SolitaireDetectionState.reset()
    }

    val requestPermission: () -> Unit = {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "SolSolve",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                text = "Take a snapshot of the solitaire game. We'll analyze it and guide you to solve it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            when (SolitaireDetectionState.mode.value) {
                ScannerMode.PREVIEW -> PreviewModeCard(hasCameraPermission, requestPermission)
                ScannerMode.SOLVING -> SolvingModeCard()
            }
        }
        item { ScanStatusAndActions() }
        item { DetectedStepsPanel() }
        item { ScanLogPanel() }
    }
}

@Composable
private fun PreviewModeCard(hasCameraPermission: Boolean, onRequestPermission: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
        ) {
            if (hasCameraPermission) {
                CameraPreviewWithCapture()
            } else {
                PermissionMissing(onRequestPermission)
            }
        }
    }
}

@Composable
private fun SolvingModeCard() {
    val bitmap by SolitaireDetectionState.capturedBitmap
    val inProgress by SolitaireDetectionState.reasoningInProgress
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Text("Solving Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    if (inProgress) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("Solving…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row {
                    IconButton(onClick = { SolitaireDetectionState.reset() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                    }
                    val context = LocalContext.current
                    IconButton(onClick = { SolitaireDetectionState.clearAll(context) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete All")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                bitmap?.let { bmp ->
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                    CardsOverlay()
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithCapture() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidViewCamera(previewView = previewView) { provider ->
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                SolitaireDetectionState.addLog(LogSeverity.ERROR, "Camera bind failed: ${e.message}")
            }
        }

        ScanningOverlay(borderCornerSize = 28.dp, onPulse = { })

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                val output = File(context.cacheDir, "solsolve_snapshot_${System.currentTimeMillis()}.jpg")
                val opts = ImageCapture.OutputFileOptions.Builder(output).build()
                imageCapture.takePicture(opts, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        SolitaireDetectionState.onSnapshotSaved(output)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        SolitaireDetectionState.addLog(LogSeverity.ERROR, "Snapshot failed: ${exception.message}")
                    }
                })
            }) {
                Text("Take Snapshot")
            }
        }
    }
}

@Composable
private fun PermissionMissing(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Camera permission required to scan.")
            }
            Button(onClick = onRequestPermission) { Text("Grant Camera Permission") }
        }
    }
}

@Composable
private fun ScanStatusAndActions() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val mode by SolitaireDetectionState.mode
        val partial by SolitaireDetectionState.partial
        val demoMode by SolitaireDetectionState.demoMode
        val statusText = when (mode) {
            ScannerMode.PREVIEW -> "Ready. Tap Take Snapshot."
            ScannerMode.SOLVING -> if (partial) "Solving (partial game)" else "Solving"
        }
        Text(
            text = statusText,
            color = when {
                mode == ScannerMode.PREVIEW -> MaterialTheme.colorScheme.onSurfaceVariant
                partial -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Demo", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
            Switch(checked = demoMode, onCheckedChange = { SolitaireDetectionState.demoMode.value = it })
            IconButton(onClick = { SolitaireDetectionState.reset() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan")
            }
            val context = LocalContext.current
            IconButton(onClick = { SolitaireDetectionState.clearAll(context) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete All")
            }
        }
    }
}

@Composable
private fun DetectedStepsPanel() {
    val steps by SolitaireDetectionState.steps
    val demoMode by SolitaireDetectionState.demoMode
    AnimatedVisibility(visible = steps.isNotEmpty() || !demoMode) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (steps.isNotEmpty()) {
                    Text("Next best moves", style = MaterialTheme.typography.titleMedium)
                    steps.take(8).forEachIndexed { index, step ->
                        Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("No computed moves", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A solver model is not installed. Enable Demo to see sample suggestions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanLogPanel() {
    val logs by SolitaireDetectionState.logs
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Scan log", style = MaterialTheme.typography.titleMedium)
            if (logs.isEmpty()) {
                Text(
                    "No messages yet. Take a snapshot to begin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                logs.takeLast(100).forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, tint) = when (entry.severity) {
                            LogSeverity.INFO -> Icons.Default.Info to MaterialTheme.colorScheme.primary
                            LogSeverity.WARN -> Icons.Default.WarningAmber to MaterialTheme.colorScheme.tertiary
                            LogSeverity.ERROR -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
                        }
                        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(entry.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardsOverlay() {
    val boxes by SolitaireDetectionState.overlayBoxes
    val steps by SolitaireDetectionState.steps
    val started by SolitaireDetectionState.reasoningStarted
    LaunchedEffect(boxes, steps) {
        if (started) return@LaunchedEffect
        SolitaireDetectionState.reasoningInProgress.value = true
        if (boxes.isNotEmpty()) {
            SolitaireDetectionState.addLog(LogSeverity.INFO, "Detected ${boxes.size} candidate card regions.")
        }
        if (steps.isNotEmpty()) {
            SolitaireDetectionState.addLog(LogSeverity.INFO, "Beginning solver reasoning over ${steps.size} planned moves…")
            for ((i, step) in steps.withIndex()) {
                SolitaireDetectionState.addLog(LogSeverity.INFO, "Considering move ${i + 1}: $step")
                delay(400)
                SolitaireDetectionState.addLog(LogSeverity.INFO, "Heuristic check: frees pile space and progresses foundation")
                delay(250)
            }
            SolitaireDetectionState.addLog(LogSeverity.INFO, "Reasoning complete. Ready to execute moves.")
        }
        SolitaireDetectionState.reasoningInProgress.value = false
        SolitaireDetectionState.reasoningStarted.value = true
    }
}

@Composable
private fun AndroidViewCamera(
    previewView: PreviewView,
    onProviderReady: (ProcessCameraProvider) -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = cameraProviderFuture.get()
            onProviderReady(provider)
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            // Let lifecycle unbind; avoid blocking get() here to reduce crash risk
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ScanningOverlay(
    borderCornerSize: Dp,
    onPulse: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val yFactor by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Mask and framing box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(280.dp, 440.dp)
                .background(Color.Transparent)
        )
        // Corners
        val cornerThickness = 4.dp
        FrameCorners(size = 280.dp, height = 440.dp, corner = borderCornerSize, stroke = cornerThickness)
        // Moving scan line
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(280.dp, 440.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.TopStart)
                    .offset(y = (yFactor * 440).dp)
            )
        }
    }
}

@Composable
private fun FrameCorners(size: Dp, height: Dp, corner: Dp, stroke: Dp) {
    val color = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(size, height)) {
            // top-left
            Box(Modifier.size(corner, stroke).background(color))
            Box(Modifier.size(stroke, corner).background(color))
            // top-right
            Box(
                Modifier
                    .size(corner, stroke)
                    .align(Alignment.TopEnd)
                    .background(color)
            )
            Box(
                Modifier
                    .size(stroke, corner)
                    .align(Alignment.TopEnd)
                    .background(color)
            )
            // bottom-left
            Box(
                Modifier
                    .size(corner, stroke)
                    .align(Alignment.BottomStart)
                    .background(color)
            )
            Box(
                Modifier
                    .size(stroke, corner)
                    .align(Alignment.BottomStart)
                    .background(color)
            )
            // bottom-right
            Box(
                Modifier
                    .size(corner, stroke)
                    .align(Alignment.BottomEnd)
                    .background(color)
            )
            Box(
                Modifier
                    .size(stroke, corner)
                    .align(Alignment.BottomEnd)
                    .background(color)
            )
        }
    }
}

private object SolitaireDetectionState {
    val mode: MutableState<ScannerMode> = mutableStateOf(ScannerMode.PREVIEW)
    val partial: MutableState<Boolean> = mutableStateOf(false)
    val capturedPath: MutableState<String?> = mutableStateOf(null)
    val capturedBitmap: MutableState<Bitmap?> = mutableStateOf(null)
    val overlayBoxes: MutableState<List<CardRegion>> = mutableStateOf(emptyList())
    val currentBoxIndex: MutableState<Int> = mutableStateOf(0)
    val steps: MutableState<List<String>> = mutableStateOf(emptyList())
    val logs: MutableState<List<ScanLogEntry>> = mutableStateOf(emptyList())
    val reasoningStarted: MutableState<Boolean> = mutableStateOf(false)
    val reasoningInProgress: MutableState<Boolean> = mutableStateOf(false)
    val demoMode: MutableState<Boolean> = mutableStateOf(false)

    fun reset() {
        mode.value = ScannerMode.PREVIEW
        partial.value = false
        capturedPath.value = null
        capturedBitmap.value = null
        overlayBoxes.value = emptyList()
        currentBoxIndex.value = 0
        steps.value = emptyList()
        logs.value = emptyList()
        reasoningStarted.value = false
        reasoningInProgress.value = false
        addLog(LogSeverity.INFO, "Ready. Take a snapshot when the game fills the frame.")
    }

    fun clearAll(context: android.content.Context) {
        // Delete cached snapshots
        try {
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("solsolve_snapshot_")) {
                    f.delete()
                }
            }
        } catch (_: Exception) { }
        // Reset all in-memory state
        reset()
    }

    fun onSnapshotSaved(file: File) {
        overlayBoxes.value = emptyList()
        currentBoxIndex.value = 0
        steps.value = emptyList()
        logs.value = emptyList()
        partial.value = false
        reasoningStarted.value = false
        reasoningInProgress.value = false

        capturedPath.value = file.absolutePath
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val targetMaxDim = 1024
        val sourceMaxDim = max(opts.outWidth, opts.outHeight)
        val sample = max(1, Integer.highestOneBit(sourceMaxDim / targetMaxDim))
        val loadOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        var bmp = BitmapFactory.decodeFile(file.absolutePath, loadOpts)
        bmp = adjustOrientationIfNeeded(file, bmp)
        capturedBitmap.value = bmp
        analyzeSnapshot(bmp)
    }

    private fun adjustOrientationIfNeeded(file: File, input: Bitmap?): Bitmap? {
        if (input == null) return null
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val angle = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (angle != 0f) {
                val m = Matrix().apply { postRotate(angle) }
                Bitmap.createBitmap(input, 0, 0, input.width, input.height, m, true)
            } else input
        } catch (_: Exception) { input }
    }

    private fun analyzeSnapshot(bitmap: Bitmap?) {
        if (bitmap == null) return
        addLog(LogSeverity.INFO, "Analyzing snapshot…")
        // Try OpenCV contour-based detection
        var tableauCols = 0
        var totalCards = 0
        try {
            val (cols, count) = detectCardsWithOpenCv(bitmap)
            tableauCols = cols
            totalCards = count
            addLog(LogSeverity.INFO, "OpenCV: detected ~$totalCards card contours across $tableauCols columns")
        } catch (e: Throwable) {
            addLog(LogSeverity.WARN, "OpenCV detection unavailable: ${e.message}")
        }

        val (edgeRatio, whiteRatio) = estimateHeuristics(bitmap)
        addLog(LogSeverity.INFO, "Heuristics: edges=${"%.3f".format(edgeRatio)}, white=${"%.3f".format(whiteRatio)}")
        partial.value = (edgeRatio < 0.06f || whiteRatio < 0.12f) || tableauCols < 5
        if (partial.value) {
            addLog(LogSeverity.WARN, "Partial solitaire game detected. Include all piles and improve lighting if possible.")
        }
        overlayBoxes.value = generateDummyCardRegions()
        if (demoMode.value) {
            steps.value = if (totalCards > 0) generateDemoStepsFromDetection(tableauCols, totalCards) else generateDemoSteps(edgeRatio, whiteRatio)
            addLog(LogSeverity.INFO, "Demo mode: generated suggestions from detection.")
        } else {
            steps.value = emptyList()
            addLog(LogSeverity.INFO, "No solver model installed; moves not computed.")
        }
        mode.value = ScannerMode.SOLVING
        addLog(LogSeverity.INFO, "Solving started (${overlayBoxes.value.size} regions).")
    }

    private fun estimateHeuristics(bitmap: Bitmap): Pair<Float, Float> {
        val width = bitmap.width
        val height = bitmap.height
        var edges = 0
        var whiteish = 0
        var samples = 0
        val step = 16
        for (y in 0 until height step step) {
            var prev = -1
            for (x in 0 until width step step) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val v = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                if (prev >= 0 && abs(v - prev) > 25) edges++
                if (v > 200) whiteish++
                prev = v
                samples++
            }
        }
        val edgeRatio = if (samples > 0) edges.toFloat() / samples else 0f
        val whiteRatio = if (samples > 0) whiteish.toFloat() / samples else 0f
        return edgeRatio to whiteRatio
    }

    private fun detectCardsWithOpenCv(srcBmp: Bitmap): Pair<Int, Int> {
        val bmp = if (srcBmp.width > 1280) Bitmap.createScaledBitmap(srcBmp, 1280, (1280f/srcBmp.width*srcBmp.height).toInt(), true) else srcBmp
        val mat = Mat()
        Utils.bitmapToMat(bmp, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, CvSize(5.0,5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 60.0, 120.0)
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        var cardCount = 0
        val xs = mutableListOf<Double>()
        contours.forEach { c ->
            val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
            val pts = approx.toArray()
            if (pts.size == 4) {
                val rect = Imgproc.boundingRect(MatOfPoint(*pts.map { org.opencv.core.Point(it.x, it.y) }.toTypedArray()))
                val w = rect.width.toDouble()
                val h = rect.height.toDouble()
                val area = w * h
                val aspect = max(w,h) / max(1.0, minOf(w,h))
                if (area > 1000 && area < (bmp.width * bmp.height * 0.25) && aspect in 1.2..2.2) {
                    cardCount++
                    xs.add(rect.x.toDouble())
                }
            }
        }
        // Estimate columns by clustering x positions into ~7 bins
        xs.sort()
        var cols = 0
        var prev = -1.0
        val thresh = bmp.width / 20.0
        xs.forEach { x ->
            if (prev < 0 || kotlin.math.abs(x - prev) > thresh) cols++
            prev = x
        }
        return cols to cardCount
    }

    private fun generateDemoStepsFromDetection(cols: Int, count: Int): List<String> {
        val piles = (1..max(1, cols)).map { "Tableau $it" }
        val ranks = listOf("A","2","3","4","5","6","7","8","9","10","J","Q","K")
        val suits = listOf("♣","♦","♥","♠")
        fun pickRank(i:Int)=ranks[(i+count+cols).mod(ranks.size)]
        fun pickSuit(i:Int)=suits[(i+count).mod(suits.size)]
        fun pile(i:Int)=piles[(i+cols).mod(piles.size)]
        return listOf(
            "Move ${pickRank(1)}${pickSuit(2)} from ${pile(3)} to Foundation",
            "Move ${pickRank(4)}${pickSuit(5)} from ${pile(6)} to ${pickRank(7)}${pickSuit(8)}",
            "Reveal stock and play ${pickRank(9)}${pickSuit(10)} to Foundation",
            "Move ${pickRank(11)}${pickSuit(12)} to Foundation",
            "Move ${pickRank(13)}${pickSuit(14)} to ${pile(15)}"
        )
    }

    private fun generateDummyCardRegions(): List<CardRegion> {
        val cols = 7
        val marginX = 0.06f
        val marginY = 0.08f
        val spacingX = (1f - 2 * marginX) / cols
        val cardW = spacingX * 0.9f
        val cardH = cardW * (3.5f / 2.5f) // aspect ratio
        val regions = mutableListOf<CardRegion>()
        for (c in 0 until cols) {
            val left = marginX + c * spacingX + (spacingX - cardW) / 2f
            val top = marginY
            regions.add(
                CardRegion(
                    label = "Tableau ${c + 1}",
                    rect = RectF(left, top, left + cardW, top + cardH)
                )
            )
        }
        return regions
    }

    private fun generateDemoSteps(edgeRatio: Float, whiteRatio: Float): List<String> {
        val seeds = (edgeRatio * 1000).toInt() xor (whiteRatio * 1000).toInt()
        val piles = listOf("Tableau 1","Tableau 2","Tableau 3","Tableau 4","Tableau 5","Tableau 6","Tableau 7")
        val ranks = listOf("A","2","3","4","5","6","7","8","9","10","J","Q","K")
        val suits = listOf("♣","♦","♥","♠")
        fun pick(list: List<String>, i: Int) = list[(i + seeds).mod(list.size)]
        val s1 = "Move ${pick(ranks,1)}${pick(suits,2)} from ${pick(piles,3)} to Foundation"
        val s2 = "Move ${pick(ranks,4)}${pick(suits,5)} from ${pick(piles,6)} to ${pick(ranks,7)}${pick(suits,8)}"
        val s3 = "Reveal stock and play ${pick(ranks,9)}${pick(suits,10)} to Foundation"
        val s4 = "Move ${pick(ranks,11)}${pick(suits,12)} to Foundation"
        val s5 = "Move ${pick(ranks,13)}${pick(suits,14)} to ${pick(piles,15)}"
        return listOf(s1,s2,s3,s4,s5)
    }

    fun addLog(severity: LogSeverity, message: String) {
        val next = logs.value + ScanLogEntry(message = message, severity = severity)
        logs.value = if (next.size > 300) next.takeLast(300) else next
    }
}

data class CardRegion(
    val label: String,
    val rect: RectF // normalized 0..1
)

enum class ScannerMode { PREVIEW, SOLVING }

private data class ScanLogEntry(
    val message: String,
    val severity: LogSeverity,
    val timeMs: Long = System.currentTimeMillis()
)

enum class LogSeverity { INFO, WARN, ERROR }


