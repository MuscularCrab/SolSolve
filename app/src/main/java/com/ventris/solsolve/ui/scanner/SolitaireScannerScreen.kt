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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

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
	}
}

@Composable
private fun CameraPreviewWithOverlay() {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val previewView = remember { PreviewView(context) }
	var isAnalyzing by remember { mutableStateOf(true) }
	var progressText by remember { mutableStateOf("Scanning for solitaire layout…") }

	Box(modifier = Modifier.fillMaxSize()) {
		AndroidViewCamera(previewView = previewView) { provider ->
			val preview = Preview.Builder()
				.setTargetResolution(Size(1080, 1920))
				.build()
			preview.surfaceProvider = previewView.surfaceProvider

			val analyzer = ImageAnalysis.Builder()
				.setTargetResolution(Size(1080, 1920))
				.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
				.build()

			val analysisExecutor = Executors.newSingleThreadExecutor()
			analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
				// Placeholder analyzer simulating work; replace with ML/vision later
				imageProxy.close()
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

		AnimatedVisibility(visible = isAnalyzing) {
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
				Text(progressText, style = MaterialTheme.typography.bodyMedium)
			}
		}
	}

	LaunchedEffect(Unit) {
		// Simulate scanning progress and completion
		delay(1400)
		progressText = "Detecting piles and face-up cards…"
		delay(1400)
		progressText = "Computing optimal move sequence…"
		delay(1400)
		SolitaireDetectionState.sampleResult()
		isAnalyzing = false
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
		Text(
			text = if (detected) "Scan successful" else "Scanning…",
			color = if (detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
				steps.take(5).forEachIndexed { index, step ->
					Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
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
					.offset { IntOffset(0, (yFactor * 440).dp.roundToPx()) }
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
	val detected: MutableState<Boolean> = mutableStateOf(false)
	val steps: MutableState<List<String>> = mutableStateOf(emptyList())

	fun sampleResult() {
		detected.value = true
		steps.value = listOf(
			"Move 7♣ from Tableau 4 to Foundation",
			"Move 6♦ from Tableau 2 to 7♣",
			"Reveal stock and play A♥ to Foundation",
			"Move 2♥ to Foundation",
			"Move 3♥ to Foundation"
		)
	}

	fun reset() {
		detected.value = false
		steps.value = emptyList()
	}
}


