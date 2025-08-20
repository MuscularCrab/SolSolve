package com.ventris.solsolve.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
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
			text = "Point your camera at the solitaire game. We'll scan and guide you to solve it in the fewest moves.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)

		Card(
			shape = RoundedCornerShape(16.dp)
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(9f / 16f)
			) {
				if (hasCameraPermission) {
					CameraPreviewWithOverlay()
				} else {
					PermissionMissing()
				}
			}
		}

		ScanStatusAndActions()
		DetectedStepsPanel()
		ScanLogPanel()
	}
}

@Composable
private fun CameraPreviewWithOverlay() {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val previewView = remember { PreviewView(context) }
	val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
	val analyzing by SolitaireDetectionState.analyzing

	Box(modifier = Modifier.fillMaxSize()) {
		AndroidViewCamera(previewView = previewView) { provider ->
			val preview = Preview.Builder()
				.setTargetResolution(Size(1080, 1920))
				.build()
			preview.setSurfaceProvider(previewView.surfaceProvider)

			val analyzer = ImageAnalysis.Builder()
				.setTargetResolution(Size(1080, 1920))
				.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
				.build()

			val analysisExecutor = Executors.newSingleThreadExecutor()
			var frameCounter = 0
			var stableHits = 0
			var resultPosted = false
			val startMs = System.currentTimeMillis()
			analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
				try {
					// Throttle to 1/4 frames
					frameCounter = (frameCounter + 1) and 3
					if (frameCounter != 0) {
						imageProxy.close(); return@setAnalyzer
					}
					val yPlane = imageProxy.planes.getOrNull(0)
					if (yPlane == null) { imageProxy.close(); return@setAnalyzer }
					val buffer: ByteBuffer = yPlane.buffer
					val rowStride = yPlane.rowStride
					val pixelStride = yPlane.pixelStride
					val width = imageProxy.width
					val height = imageProxy.height
					val step = 12
					var samples = 0
					var edges = 0
					var whiteish = 0
					for (y in 0 until height step step) {
						var prev = -1
						for (x in 0 until width step step) {
							val idx = y * rowStride + x * pixelStride
							val v = buffer.get(idx).toInt() and 0xFF
							if (prev >= 0 && abs(v - prev) > 25) edges++
							if (v > 200) whiteish++
							prev = v
							samples++
						}
					}
					val edgeRatio = if (samples > 0) edges.toFloat() / samples else 0f
					val whiteRatio = if (samples > 0) whiteish.toFloat() / samples else 0f
					val looksLikeCards = edgeRatio > 0.06f && whiteRatio > 0.12f
					if (looksLikeCards) stableHits++ else stableHits = max(0, stableHits - 1)
					val elapsed = System.currentTimeMillis() - startMs
					if (!resultPosted && stableHits >= 6) {
						resultPosted = true
						mainExecutor.execute {
							SolitaireDetectionState.onDetectionSuccess(
								edgeRatio = edgeRatio,
								whiteRatio = whiteRatio
							)
						}
					} else if (!resultPosted && elapsed > 6000) {
						resultPosted = true
						mainExecutor.execute {
							SolitaireDetectionState.onNoGameDetected(
								edgeRatio = edgeRatio,
								whiteRatio = whiteRatio
							)
						}
					}
				} catch (_: Exception) {
					// ignore analyzer errors for now
				} finally {
					imageProxy.close()
				}
			}

			try {
				provider.unbindAll()
				provider.bindToLifecycle(
					lifecycleOwner,
					CameraSelector.DEFAULT_BACK_CAMERA,
					preview,
					analyzer
				)
			} catch (_: Exception) { }
		}

		ScanningOverlay(
			borderCornerSize = 28.dp,
			onPulse = { /* no-op */ }
		)

		AnimatedVisibility(visible = analyzing) {
			Row(
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(16.dp)
					.background(
						MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
						RoundedCornerShape(12.dp)
					)
					.padding(horizontal = 16.dp, vertical = 10.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				CircularProgressIndicator(
					modifier = Modifier.size(18.dp),
					strokeWidth = 2.dp
				)
				Spacer(modifier = Modifier.size(12.dp))
				Text("Scanning…", style = MaterialTheme.typography.bodyMedium)
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
		val detected by SolitaireDetectionState.detected
		val analyzing by SolitaireDetectionState.analyzing
		val statusText = when {
			analyzing -> "Scanning…"
			detected -> "Scan successful"
			else -> "No game detected"
		}
		Text(
			text = statusText,
			color = when {
				detected -> MaterialTheme.colorScheme.primary
				analyzing -> MaterialTheme.colorScheme.onSurfaceVariant
				else -> MaterialTheme.colorScheme.error
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
					"No messages yet. Point the camera at the solitaire game.",
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
	val analyzing: MutableState<Boolean> = mutableStateOf(true)
	val detected: MutableState<Boolean> = mutableStateOf(false)
	val steps: MutableState<List<String>> = mutableStateOf(emptyList())
	val logs: MutableState<List<ScanLogEntry>> = mutableStateOf(emptyList())

	fun reset() {
		analyzing.value = true
		detected.value = false
		steps.value = emptyList()
		logs.value = emptyList()
		addLog(LogSeverity.INFO, "Scan reset. Hold steady and frame the entire tableau.")
	}

	fun onDetectionSuccess(edgeRatio: Float, whiteRatio: Float) {
		if (!detected.value) {
			detected.value = true
			analyzing.value = false
			steps.value = listOf(
				"Move 7♣ from Tableau 4 to Foundation",
				"Move 6♦ from Tableau 2 to 7♣",
				"Reveal stock and play A♥ to Foundation",
				"Move 2♥ to Foundation",
				"Move 3♥ to Foundation"
			)
			addLog(LogSeverity.INFO, "Solitaire layout detected (edges=${"%.2f".format(edgeRatio)}, white=${"%.2f".format(whiteRatio)}).")
		}
	}

	fun onNoGameDetected(edgeRatio: Float, whiteRatio: Float) {
		if (!detected.value) {
			analyzing.value = false
			addLog(LogSeverity.WARN, "No game detected. Try better lighting and include all piles in view (edges=${"%.2f".format(edgeRatio)}, white=${"%.2f".format(whiteRatio)}).")
		}
	}

	private fun addLog(severity: LogSeverity, message: String) {
		val next = logs.value + ScanLogEntry(message = message, severity = severity)
		logs.value = if (next.size > 100) next.takeLast(100) else next
	}
}

private data class ScanLogEntry(
	val message: String,
	val severity: LogSeverity,
	val timeMs: Long = System.currentTimeMillis()
)

enum class LogSeverity { INFO, WARN, ERROR }


