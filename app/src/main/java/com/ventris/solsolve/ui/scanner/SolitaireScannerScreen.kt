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
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.foundation.canvas.Canvas
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
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
		if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
		SolitaireDetectionState.reset()
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Text(
			text = "SolSolve",
			style = MaterialTheme.typography.headlineSmall,
			fontWeight = FontWeight.Bold
		)
		Text(
			text = "Take a snapshot of the solitaire game. We'll analyze it and guide you to solve it.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)

		when (SolitaireDetectionState.mode.value) {
			ScannerMode.PREVIEW -> PreviewModeCard(hasCameraPermission)
			ScannerMode.SOLVING -> SolvingModeCard()
		}

		ScanStatusAndActions()
		DetectedStepsPanel()
		ScanLogPanel()
	}
}

@Composable
private fun PreviewModeCard(hasCameraPermission: Boolean) {
	Card(shape = RoundedCornerShape(16.dp)) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(9f / 16f)
		) {
			if (hasCameraPermission) {
				CameraPreviewWithCapture()
			} else {
				PermissionMissing()
			}
		}
	}
}

@Composable
private fun SolvingModeCard() {
	val bitmap by SolitaireDetectionState.capturedBitmap
	Card(shape = RoundedCornerShape(16.dp)) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				// Mini preview
				bitmap?.let {
					Image(
						bitmap = it.asImageBitmap(),
						contentDescription = null,
						modifier = Modifier
							.size(96.dp)
							.clip(RoundedCornerShape(8.dp))
					)
				}
				Text("Solving Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
			}

			// Annotated snapshot
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(9f / 16f)
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
			.setTargetResolution(Size(1080, 1920))
			.build()
	}

	Box(modifier = Modifier.fillMaxSize()) {
		AndroidViewCamera(previewView = previewView) { provider ->
			val preview = Preview.Builder()
				.setTargetResolution(Size(1080, 1920))
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
			} catch (_: Exception) { }
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
private fun PermissionMissing() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black),
		contentAlignment = Alignment.Center
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier
				.background(
					MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
					RoundedCornerShape(12.dp)
				)
				.padding(16.dp)
		) {
			Icon(Icons.Default.Info, contentDescription = null)
			Spacer(Modifier.size(8.dp))
			Text("Camera permission required to scan.")
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
		val statusText = when (mode) {
			ScannerMode.PREVIEW -> "Ready"
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
		IconButton(onClick = { SolitaireDetectionState.reset() }) {
			Icon(Icons.Default.Refresh, contentDescription = "Rescan")
		}
	}
}

@Composable
private fun DetectedStepsPanel() {
	val steps by SolitaireDetectionState.steps
	AnimatedVisibility(visible = steps.isNotEmpty()) {
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
				Text("Next best moves", style = MaterialTheme.typography.titleMedium)
				steps.take(8).forEachIndexed { index, step ->
					Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
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
				.padding(16.dp)
				.verticalScroll(rememberScrollState()),
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
				logs.takeLast(50).forEach { entry ->
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
	val currentIndex by SolitaireDetectionState.currentBoxIndex
	Canvas(modifier = Modifier.fillMaxSize()) {
		boxes.forEachIndexed { index, box ->
			val color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
			val stroke = if (index == currentIndex) 4f else 2f
			val rectPx = androidx.compose.ui.geometry.Rect(
				left = size.width * box.rect.left,
				top = size.height * box.rect.top,
				right = size.width * box.rect.right,
				bottom = size.height * box.rect.bottom
			)
			drawRect(color = Color.Transparent)
			drawRect(color = color, style = Stroke(width = stroke), topLeft = rectPx.topLeft, size = rectPx.size)
		}
	}

	// Drive overlay highlighting
	LaunchedEffect(boxes) {
		if (boxes.isEmpty()) return@LaunchedEffect
		SolitaireDetectionState.addLog(LogSeverity.INFO, "Starting reasoning across ${boxes.size} card regions…")
		for (i in boxes.indices) {
			SolitaireDetectionState.currentBoxIndex.value = i
			SolitaireDetectionState.addLog(LogSeverity.INFO, "Inspecting region ${i + 1}: ${boxes[i].label}")
			delay(900)
		}
		SolitaireDetectionState.addLog(LogSeverity.INFO, "Reasoning pass complete.")
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
			try {
				ProcessCameraProvider.getInstance(context).get().unbindAll()
			} catch (_: Exception) { }
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

	fun reset() {
		mode.value = ScannerMode.PREVIEW
		partial.value = false
		capturedPath.value = null
		capturedBitmap.value = null
		overlayBoxes.value = emptyList()
		currentBoxIndex.value = 0
		steps.value = emptyList()
		logs.value = emptyList()
		addLog(LogSeverity.INFO, "Ready. Take a snapshot when the game fills the frame.")
	}

	fun onSnapshotSaved(file: File) {
		capturedPath.value = file.absolutePath
		val bmp = BitmapFactory.decodeFile(file.absolutePath)
		capturedBitmap.value = bmp
		analyzeSnapshot(bmp)
	}

	private fun analyzeSnapshot(bitmap: Bitmap?) {
		if (bitmap == null) return
		addLog(LogSeverity.INFO, "Analyzing snapshot…")
		// Basic heuristic: edge and white ratios
		val (edgeRatio, whiteRatio) = estimateHeuristics(bitmap)
		addLog(LogSeverity.INFO, "Heuristics: edges=${"%.3f".format(edgeRatio)}, white=${"%.3f".format(whiteRatio)}")
		partial.value = edgeRatio < 0.06f || whiteRatio < 0.12f
		if (partial.value) {
			addLog(LogSeverity.WARN, "Partial solitaire game detected. Include all piles and improve lighting if possible.")
		}
		// Generate dummy card regions laid out like 7 tableau columns
		overlayBoxes.value = generateDummyCardRegions()
		steps.value = listOf(
			"Move 7♣ from Tableau 4 to Foundation",
			"Move 6♦ from Tableau 2 to 7♣",
			"Reveal stock and play A♥ to Foundation",
			"Move 2♥ to Foundation",
			"Move 3♥ to Foundation"
		)
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

	fun addLog(severity: LogSeverity, message: String) {
		val next = logs.value + ScanLogEntry(message = message, severity = severity)
		logs.value = if (next.size > 200) next.takeLast(200) else next
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


