@file:Suppress("unused")

package com.example.cyberpunk.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint as NativePaint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Tokyo Night Palette
private val TokyoBg = Color(0xFF1A1B26)
private val TokyoSurface = Color(0xFF24283B)
private val TokyoBlue = Color(0xFF7AA2F7)
private val TokyoPurple = Color(0xFFBB9AF7)
private val TokyoCyan = Color(0xFF7DCFFF)
private val TokyoClosed = Color(0xFFF7768E)
private val TokyoOpen = Color(0xFF9ECE6A)

private const val TWO_PI = (2.0 * PI).toFloat()

// Group identifiers for pre-allocated 3D geometry
private const val GROUP_TOP_HALF = 0
private const val GROUP_BOTTOM_HALF = 1
private const val GROUP_CORE = 2
private const val GROUP_SCANNER_OUTER = 3
private const val GROUP_SCANNER_INNER = 4

// Edge style identifiers
private const val STYLE_RING_PRIMARY = 0
private const val STYLE_RING_COLLAR = 1
private const val STYLE_RIB_VERTICAL = 2
private const val STYLE_RIB_DIAGONAL = 3
private const val STYLE_CORE_WIRE = 4
private const val STYLE_SCANNER_ARC = 5
private const val STYLE_SCANNER_TICK = 6
private const val STYLE_ENERGY_TETHER = 7

/**
 * Pre-allocated 3D Matrix holder (Rotations around X, Y, Z) to avoid per-frame allocations.
 */
private class Matrix3x3 {
    var m00 = 1f; var m01 = 0f; var m02 = 0f
    var m10 = 0f; var m11 = 1f; var m12 = 0f
    var m20 = 0f; var m21 = 0f; var m22 = 1f

    /**
     * Sets matrix to R_x(rotX) * R_y(rotY) * R_z(rotZ)
     */
    fun setRotationXYZ(rotX: Float, rotY: Float, rotZ: Float = 0f) {
        val cx = cos(rotX)
        val sx = sin(rotX)
        val cy = cos(rotY)
        val sy = sin(rotY)
        val cz = cos(rotZ)
        val sz = sin(rotZ)

        // R_y * R_z
        val ryz00 = cy * cz
        val ryz01 = -cy * sz
        val ryz02 = sy
        val ryz10 = sz
        val ryz11 = cz
        val ryz12 = 0f
        val ryz20 = -sy * cz
        val ryz21 = sy * sz
        val ryz22 = cy

        // R_x * (R_y * R_z)
        m00 = ryz00
        m01 = ryz01
        m02 = ryz02

        m10 = cx * ryz10 - sx * ryz20
        m11 = cx * ryz11 - sx * ryz21
        m12 = cx * ryz12 - sx * ryz22

        m20 = sx * ryz10 + cx * ryz20
        m21 = sx * ryz11 + cx * ryz21
        m22 = sx * ryz12 + cx * ryz22
    }

    fun multiply(other: Matrix3x3, out: Matrix3x3) {
        val a00 = m00 * other.m00 + m01 * other.m10 + m02 * other.m20
        val a01 = m00 * other.m01 + m01 * other.m11 + m02 * other.m21
        val a02 = m00 * other.m02 + m01 * other.m12 + m02 * other.m22

        val a10 = m10 * other.m00 + m11 * other.m10 + m12 * other.m20
        val a11 = m10 * other.m01 + m11 * other.m11 + m12 * other.m21
        val a12 = m10 * other.m02 + m11 * other.m12 + m12 * other.m22

        val a20 = m20 * other.m00 + m21 * other.m10 + m22 * other.m20
        val a21 = m20 * other.m01 + m21 * other.m11 + m22 * other.m21
        val a22 = m20 * other.m02 + m21 * other.m12 + m22 * other.m22

        out.m00 = a00; out.m01 = a01; out.m02 = a02
        out.m10 = a10; out.m11 = a11; out.m12 = a12
        out.m20 = a20; out.m21 = a21; out.m22 = a22
    }
}

/**
 * Pre-allocated 3D Geometry and Projection buffers for the Cyberpunk Time Capsule.
 * Zero objects are allocated during the draw phase.
 */
private class Capsule3DMesh {
    private val maxVertices = 320
    private val maxEdges = 640

    var vertexCount = 0
        private set
    var edgeCount = 0
        private set

    // Base local-space coordinates
    val baseX = FloatArray(maxVertices)
    val baseY = FloatArray(maxVertices)
    val baseZ = FloatArray(maxVertices)
    val vertexGroup = IntArray(maxVertices)
    val isKeyNode = BooleanArray(maxVertices)

    // Transformed 3D & Projected 2D coordinates
    val viewX = FloatArray(maxVertices)
    val viewY = FloatArray(maxVertices)
    val viewZ = FloatArray(maxVertices)
    val screenX = FloatArray(maxVertices)
    val screenY = FloatArray(maxVertices)
    val perspectiveScale = FloatArray(maxVertices)

    // Edge topology
    val edgeStart = IntArray(maxEdges)
    val edgeEnd = IntArray(maxEdges)
    val edgeStyle = IntArray(maxEdges)

    // Reusable matrices
    val worldMatrix = Matrix3x3()
    val localMatrix = Matrix3x3()
    val combinedMatrix = Matrix3x3()

    init {
        buildGeometry()
    }

    private fun addVertex(
        x: Float,
        y: Float,
        z: Float,
        group: Int,
        keyNode: Boolean = false
    ): Int {
        val idx = vertexCount++
        baseX[idx] = x
        baseY[idx] = y
        baseZ[idx] = z
        vertexGroup[idx] = group
        isKeyNode[idx] = keyNode
        return idx
    }

    private fun addEdge(start: Int, end: Int, style: Int) {
        val idx = edgeCount++
        edgeStart[idx] = start
        edgeEnd[idx] = end
        edgeStyle[idx] = style
    }

    private fun buildGeometry() {
        val ringSegments = 16
        // Y levels and radii for one half of the capsule (mirrored for bottom half)
        val ringY = floatArrayOf(0.03f, 0.16f, 0.56f, 0.84f, 1.00f)
        val ringR = floatArrayOf(0.42f, 0.56f, 0.56f, 0.43f, 0.18f)

        val topRingIndices = Array(ringY.size) { IntArray(ringSegments) }
        val bottomRingIndices = Array(ringY.size) { IntArray(ringSegments) }

        // 1. Build Top Half
        for (r in ringY.indices) {
            val y = ringY[r]
            val radius = ringR[r]
            for (s in 0 until ringSegments) {
                val angle = (s.toFloat() / ringSegments) * TWO_PI
                val x = cos(angle) * radius
                val z = sin(angle) * radius
                val isNode = (r == 1 || r == 2) && (s % 2 == 0)
                topRingIndices[r][s] = addVertex(x, y, z, GROUP_TOP_HALF, isNode)
            }
            val style = if (r == 0 || r == 4) STYLE_RING_COLLAR else STYLE_RING_PRIMARY
            for (s in 0 until ringSegments) {
                val next = (s + 1) % ringSegments
                addEdge(topRingIndices[r][s], topRingIndices[r][next], style)
            }
        }

        // Top Half Vertical Ribs & Cross-Bracing
        for (s in 0 until ringSegments) {
            // Barrel vertical ribs
            addEdge(topRingIndices[1][s], topRingIndices[2][s], STYLE_RIB_VERTICAL)
            if (s % 2 == 0) {
                addEdge(topRingIndices[0][s], topRingIndices[1][s], STYLE_RIB_VERTICAL)
                addEdge(topRingIndices[2][s], topRingIndices[3][s], STYLE_RIB_VERTICAL)
                addEdge(topRingIndices[3][s], topRingIndices[4][s], STYLE_RIB_VERTICAL)
                // Diagonal cyber-truss on main barrel
                val nextEven = (s + 2) % ringSegments
                addEdge(topRingIndices[1][s], topRingIndices[2][nextEven], STYLE_RIB_DIAGONAL)
            }
        }

        // 2. Build Bottom Half
        for (r in ringY.indices) {
            val y = -ringY[r]
            val radius = ringR[r]
            for (s in 0 until ringSegments) {
                val angle = (s.toFloat() / ringSegments) * TWO_PI
                val x = cos(angle) * radius
                val z = sin(angle) * radius
                val isNode = (r == 1 || r == 2) && (s % 2 == 0)
                bottomRingIndices[r][s] = addVertex(x, y, z, GROUP_BOTTOM_HALF, isNode)
            }
            val style = if (r == 0 || r == 4) STYLE_RING_COLLAR else STYLE_RING_PRIMARY
            for (s in 0 until ringSegments) {
                val next = (s + 1) % ringSegments
                addEdge(bottomRingIndices[r][s], bottomRingIndices[r][next], style)
            }
        }

        // Bottom Half Vertical Ribs & Cross-Bracing
        for (s in 0 until ringSegments) {
            addEdge(bottomRingIndices[1][s], bottomRingIndices[2][s], STYLE_RIB_VERTICAL)
            if (s % 2 == 0) {
                addEdge(bottomRingIndices[0][s], bottomRingIndices[1][s], STYLE_RIB_VERTICAL)
                addEdge(bottomRingIndices[2][s], bottomRingIndices[3][s], STYLE_RIB_VERTICAL)
                addEdge(bottomRingIndices[3][s], bottomRingIndices[4][s], STYLE_RIB_VERTICAL)
                val nextEven = (s + 2) % ringSegments
                addEdge(bottomRingIndices[1][s], bottomRingIndices[2][nextEven], STYLE_RIB_DIAGONAL)
            }
        }

        // Energy Tethers connecting Top and Bottom inner lock collars
        for (s in 0 until ringSegments step 2) {
            addEdge(topRingIndices[0][s], bottomRingIndices[0][s], STYLE_ENERGY_TETHER)
        }

        // 3. Build Inner Energy Core (Octahedral Crystal + Equatorial Ring)
        val coreTop = addVertex(0f, 0.28f, 0f, GROUP_CORE, keyNode = true)
        val coreBottom = addVertex(0f, -0.28f, 0f, GROUP_CORE, keyNode = true)
        val coreSegs = 6
        val coreRing = IntArray(coreSegs)
        for (s in 0 until coreSegs) {
            val angle = (s.toFloat() / coreSegs) * TWO_PI
            val r = 0.22f
            coreRing[s] = addVertex(cos(angle) * r, 0f, sin(angle) * r, GROUP_CORE, keyNode = true)
        }
        for (s in 0 until coreSegs) {
            val next = (s + 1) % coreSegs
            addEdge(coreRing[s], coreRing[next], STYLE_CORE_WIRE)
            addEdge(coreTop, coreRing[s], STYLE_CORE_WIRE)
            addEdge(coreBottom, coreRing[s], STYLE_CORE_WIRE)
        }

        // 4. Build Outer Scanner Ring (Dual band with tick markers)
        val outerSegs = 32
        val outerRadius = 0.92f
        val outerBandHalfHeight = 0.045f
        val outerTop = IntArray(outerSegs)
        val outerBot = IntArray(outerSegs)
        for (s in 0 until outerSegs) {
            val angle = (s.toFloat() / outerSegs) * TWO_PI
            val x = cos(angle) * outerRadius
            val z = sin(angle) * outerRadius
            outerTop[s] = addVertex(x, outerBandHalfHeight, z, GROUP_SCANNER_OUTER, s % 4 == 0)
            outerBot[s] = addVertex(x, -outerBandHalfHeight, z, GROUP_SCANNER_OUTER, false)
        }
        for (s in 0 until outerSegs) {
            val next = (s + 1) % outerSegs
            // Create segmented holographic arcs (leave small gaps every 8 segments)
            if (s % 8 != 7) {
                addEdge(outerTop[s], outerTop[next], STYLE_SCANNER_ARC)
                addEdge(outerBot[s], outerBot[next], STYLE_SCANNER_ARC)
            }
            if (s % 2 == 0) {
                addEdge(outerTop[s], outerBot[s], STYLE_SCANNER_TICK)
            }
        }

        // 5. Build Inner Scanner Ring (Tilted counter-rotating telemetry ring)
        val innerSegs = 24
        val innerRadius = 0.76f
        val innerRing = IntArray(innerSegs)
        val innerTicks = IntArray(innerSegs)
        for (s in 0 until innerSegs) {
            val angle = (s.toFloat() / innerSegs) * TWO_PI
            val c = cos(angle)
            val sn = sin(angle)
            innerRing[s] = addVertex(c * innerRadius, 0f, sn * innerRadius, GROUP_SCANNER_INNER, s % 6 == 0)
            innerTicks[s] = addVertex(c * (innerRadius + 0.06f), 0f, sn * (innerRadius + 0.06f), GROUP_SCANNER_INNER, false)
        }
        for (s in 0 until innerSegs) {
            val next = (s + 1) % innerSegs
            if (s % 6 != 5) {
                addEdge(innerRing[s], innerRing[next], STYLE_SCANNER_ARC)
            }
            if (s % 3 == 0) {
                addEdge(innerRing[s], innerTicks[s], STYLE_SCANNER_TICK)
            }
        }
    }

    /**
     * Transforms all vertices into 3D camera space and projects them onto the 2D Canvas.
     */
    fun transformAndProject(
        centerX: Float,
        centerY: Float,
        fov: Float,
        cameraDistance: Float,
        rotX: Float,
        rotY: Float,
        splitOffset: Float,
        coreScale: Float,
        coreSpin: Float,
        scannerAngle: Float
    ) {
        worldMatrix.setRotationXYZ(rotX, rotY, 0f)

        for (i in 0 until vertexCount) {
            var lx = baseX[i]
            var ly = baseY[i]
            var lz = baseZ[i]

            when (vertexGroup[i]) {
                GROUP_TOP_HALF -> {
                    ly += splitOffset
                    applyMatrix(worldMatrix, lx, ly, lz, i)
                }
                GROUP_BOTTOM_HALF -> {
                    ly -= splitOffset
                    applyMatrix(worldMatrix, lx, ly, lz, i)
                }
                GROUP_CORE -> {
                    lx *= coreScale
                    ly *= coreScale
                    lz *= coreScale
                    localMatrix.setRotationXYZ(coreSpin * 0.7f, -coreSpin * 1.6f, coreSpin * 0.4f)
                    worldMatrix.multiply(localMatrix, combinedMatrix)
                    applyMatrix(combinedMatrix, lx, ly, lz, i)
                }
                GROUP_SCANNER_OUTER -> {
                    localMatrix.setRotationXYZ(0.38f, scannerAngle, -0.22f)
                    worldMatrix.multiply(localMatrix, combinedMatrix)
                    applyMatrix(combinedMatrix, lx, ly, lz, i)
                }
                GROUP_SCANNER_INNER -> {
                    localMatrix.setRotationXYZ(-0.52f, -scannerAngle * 1.35f, 0.35f)
                    worldMatrix.multiply(localMatrix, combinedMatrix)
                    applyMatrix(combinedMatrix, lx, ly, lz, i)
                }
            }

            // Perspective projection (+Z is towards the viewer)
            val depth = (cameraDistance - viewZ[i]).coerceAtLeast(0.5f)
            val scale = fov / depth
            perspectiveScale[i] = scale
            screenX[i] = centerX + viewX[i] * scale
            screenY[i] = centerY - viewY[i] * scale
        }
    }

    private fun applyMatrix(m: Matrix3x3, x: Float, y: Float, z: Float, idx: Int) {
        viewX[idx] = m.m00 * x + m.m01 * y + m.m02 * z
        viewY[idx] = m.m10 * x + m.m11 * y + m.m12 * z
        viewZ[idx] = m.m20 * x + m.m21 * y + m.m22 * z
    }
}

/**
 * Cached native Android Paint objects with BlurMaskFilter for neon glow rendering.
 * Instantiated once per composition via remember.
 */
private class NeonGlowPaints {
    private val lineBlurFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    private val scannerBlurFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
    private val coreOuterBlurFilter = BlurMaskFilter(56f, BlurMaskFilter.Blur.NORMAL)
    private val coreInnerBlurFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)

    val wireGlowPaint = NativePaint().apply {
        isAntiAlias = true
        style = NativePaint.Style.STROKE
        strokeCap = NativePaint.Cap.ROUND
        maskFilter = lineBlurFilter
    }

    val scannerGlowPaint = NativePaint().apply {
        isAntiAlias = true
        style = NativePaint.Style.STROKE
        strokeCap = NativePaint.Cap.ROUND
        maskFilter = scannerBlurFilter
    }

    val coreOuterGlowPaint = NativePaint().apply {
        isAntiAlias = true
        style = NativePaint.Style.FILL
        maskFilter = coreOuterBlurFilter
    }

    val coreInnerGlowPaint = NativePaint().apply {
        isAntiAlias = true
        style = NativePaint.Style.FILL
        maskFilter = coreInnerBlurFilter
    }
}

@Composable
fun Capsule3DView(
    isUnlocked: Boolean,
    isSyncing: Boolean,
    onCapsuleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnClick by rememberUpdatedState(onCapsuleClick)

    // User swipe rotation state
    var dragRotX by remember { mutableFloatStateOf(-0.22f) }
    var dragRotY by remember { mutableFloatStateOf(0.45f) }

    // Smooth auto-rotation & ambient pulsing via rememberInfiniteTransition
    val infiniteTransition = rememberInfiniteTransition(label = "CapsuleInfiniteTransition")

    val autoRotY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AutoRotationY"
    )

    val baseTickPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScannerBaseTick"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CorePulse"
    )

    // Animated scanner rotation speed multiplier when isSyncing changes
    val scannerSpeed by animateFloatAsState(
        targetValue = if (isSyncing) 4.5f else 1.0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "ScannerSpeed"
    )

    // Accumulate scanner phase smoothly without phase jumps when scannerSpeed changes
    var accumulatedScannerAngle by remember { mutableFloatStateOf(0f) }
    var lastTickPhase by remember { mutableFloatStateOf(baseTickPhase) }

    SideEffect {
        var delta = baseTickPhase - lastTickPhase
        if (delta < 0f) delta += TWO_PI
        accumulatedScannerAngle = (accumulatedScannerAngle + delta * scannerSpeed) % (TWO_PI * 20f)
        lastTickPhase = baseTickPhase
    }

    // Status glow color transition (#F7768E closed -> #9ECE6A open)
    val statusGlowColor by animateColorAsState(
        targetValue = if (isUnlocked) TokyoOpen else TokyoClosed,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "StatusGlowColor"
    )

    // Vertical separation of top and bottom wireframe halves when unlocked
    val splitOffset by animateFloatAsState(
        targetValue = if (isUnlocked) 0.36f else 0.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CapsuleSplitOffset"
    )

    // Core expansion when unlocked
    val coreExpansion by animateFloatAsState(
        targetValue = if (isUnlocked) 1.52f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CoreExpansion"
    )

    // Intense flash burst when capsule unlocks
    val unlockFlash = remember { Animatable(0f) }
    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            unlockFlash.snapTo(1f)
            unlockFlash.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
        } else {
            unlockFlash.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 250)
            )
        }
    }

    // Pre-allocated 3D mesh and native BlurMaskFilter paints
    val mesh = remember { Capsule3DMesh() }
    val neonPaints = remember { NeonGlowPaints() }

    // Адаптивное качество для устройств со слабым GPU: если кадр длится > 24 мс, снижаем число проходов свечения
    var avgFrameDurationMs by remember { mutableFloatStateOf(16f) }
    val isWeakGpu = avgFrameDurationMs > 24f

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnClick() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val sensitivity = 0.0075f
                    dragRotY = (dragRotY + dragAmount.x * sensitivity) % TWO_PI
                    dragRotX = (dragRotX + dragAmount.y * sensitivity).coerceIn(-1.35f, 1.35f)
                }
            }
    ) {
        val frameStartTime = System.currentTimeMillis()
        val canvasWidth = size.width
        val canvasHeight = size.height
        if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

        val minDim = min(canvasWidth, canvasHeight)
        if (minDim <= 0f) return@Canvas

        val centerX = canvasWidth * 0.5f
        val centerY = canvasHeight * 0.5f
        val centerOffset = Offset(centerX, centerY)

        // 1. Draw Tokyo Night Cyberpunk Background & Subtle HUD Grid
        drawCyberpunkBackdrop(
            centerOffset = centerOffset,
            minDim = minDim,
            statusColor = statusGlowColor,
            isSyncing = isSyncing
        )

        // 2. Transform and Project 3D Geometry
        val totalRotY = dragRotY + autoRotY
        val totalRotX = dragRotX
        val effectiveCoreScale = coreExpansion * corePulse + unlockFlash.value * 0.35f
        val fov = minDim * 1.05f
        val cameraDistance = 3.6f

        mesh.transformAndProject(
            centerX = centerX,
            centerY = centerY,
            fov = fov,
            cameraDistance = cameraDistance,
            rotX = totalRotX,
            rotY = totalRotY,
            splitOffset = splitOffset,
            coreScale = effectiveCoreScale,
            coreSpin = autoRotY * 2.0f,
            scannerAngle = accumulatedScannerAngle
        )

        // 3. Pass 1: Back-facing 3D edges (z < 0) for accurate depth layering behind the core
        drawMeshEdgesPass(
            mesh = mesh,
            neonPaints = neonPaints,
            frontPass = false,
            statusColor = statusGlowColor,
            splitOffset = splitOffset,
            isSyncing = isSyncing,
            flashIntensity = unlockFlash.value,
            minDim = minDim
        )

        // 4. Pass 2: Central Glowing Core & Inner Crystal Wireframe
        drawGlowingCore(
            mesh = mesh,
            neonPaints = neonPaints,
            centerOffset = centerOffset,
            minDim = minDim,
            statusColor = statusGlowColor,
            coreScale = effectiveCoreScale,
            flashIntensity = unlockFlash.value,
            isWeakGpu = isWeakGpu
        )

        // 5. Pass 3: Front-facing 3D edges (z >= 0) & Glowing Vertex Nodes
        drawMeshEdgesPass(
            mesh = mesh,
            neonPaints = neonPaints,
            frontPass = true,
            statusColor = statusGlowColor,
            splitOffset = splitOffset,
            isSyncing = isSyncing,
            flashIntensity = unlockFlash.value,
            minDim = minDim
        )

        // Замер длительности кадра для адаптивного сглаживания
        val frameDuration = System.currentTimeMillis() - frameStartTime
        avgFrameDurationMs = avgFrameDurationMs * 0.92f + frameDuration * 0.08f
    }
}

private val ShockwaveStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)

private fun DrawScope.drawCyberpunkBackdrop(
    centerOffset: Offset,
    minDim: Float,
    statusColor: Color,
    isSyncing: Boolean
) {
    // Deep Tokyo Night surface vignette (zero allocations per frame)
    drawRect(color = TokyoBg)
    drawCircle(
        color = TokyoSurface,
        radius = minDim * 0.62f,
        center = centerOffset
    )
    drawCircle(
        color = TokyoBg.copy(alpha = 0.45f),
        radius = minDim * 0.56f,
        center = centerOffset
    )

    // Subtle ambient status aura in the background
    drawCircle(
        color = statusColor.copy(alpha = 0.09f),
        radius = minDim * 0.44f,
        center = centerOffset
    )
    drawCircle(
        color = TokyoPurple.copy(alpha = 0.05f),
        radius = minDim * 0.30f,
        center = centerOffset
    )

    // Cyberpunk HUD Corner Reticles
    val pad = minDim * 0.06f
    val bracketLen = minDim * 0.075f
    val hudColor = if (isSyncing) TokyoCyan.copy(alpha = 0.55f) else TokyoBlue.copy(alpha = 0.32f)
    val strokeW = max(1.5f, minDim * 0.0035f)

    // Top-Left
    drawLine(hudColor, Offset(pad, pad), Offset(pad + bracketLen, pad), strokeW)
    drawLine(hudColor, Offset(pad, pad), Offset(pad, pad + bracketLen), strokeW)

    // Top-Right
    drawLine(hudColor, Offset(size.width - pad, pad), Offset(size.width - pad - bracketLen, pad), strokeW)
    drawLine(hudColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + bracketLen), strokeW)

    // Bottom-Left
    drawLine(hudColor, Offset(pad, size.height - pad), Offset(pad + bracketLen, size.height - pad), strokeW)
    drawLine(hudColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - bracketLen), strokeW)

    // Bottom-Right
    drawLine(hudColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - bracketLen, size.height - pad), strokeW)
    drawLine(hudColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - bracketLen), strokeW)
}

private fun DrawScope.drawGlowingCore(
    mesh: Capsule3DMesh,
    neonPaints: NeonGlowPaints,
    centerOffset: Offset,
    minDim: Float,
    statusColor: Color,
    coreScale: Float,
    flashIntensity: Float,
    isWeakGpu: Boolean = false
) {
    val baseRadius = minDim * 0.075f * coreScale
    val outerGlowRadius = baseRadius * (2.3f + flashIntensity * 1.4f)
    val innerGlowRadius = baseRadius * (1.25f + flashIntensity * 0.6f)

    val outerGlowColor = lerp(statusColor, TokyoPurple, 0.25f)
    val innerGlowColor = lerp(statusColor, Color.White, 0.35f + flashIntensity * 0.45f)

    // Native Canvas BlurMaskFilter pass for intense neon core bloom
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas

        // На слабых GPU пропускаем широкий размытый проход (outer bloom) для сохранения плавности 60 FPS
        if (!isWeakGpu) {
            neonPaints.coreOuterGlowPaint.color = outerGlowColor
                .copy(alpha = (0.58f + flashIntensity * 0.35f).coerceAtMost(1f))
                .toArgb()
            native.drawCircle(
                centerOffset.x,
                centerOffset.y,
                outerGlowRadius,
                neonPaints.coreOuterGlowPaint
            )
        }

        neonPaints.coreInnerGlowPaint.color = innerGlowColor
            .copy(alpha = (0.85f + flashIntensity * 0.15f).coerceAtMost(1f))
            .toArgb()
        native.drawCircle(
            centerOffset.x,
            centerOffset.y,
            innerGlowRadius,
            neonPaints.coreInnerGlowPaint
        )
    }

    // Crisp hot energy nucleus
    drawCircle(
        color = statusColor.copy(alpha = 0.92f),
        radius = baseRadius * 0.68f,
        center = centerOffset
    )
    drawCircle(
        color = Color.White.copy(alpha = (0.85f + flashIntensity * 0.15f).coerceAtMost(1f)),
        radius = baseRadius * 0.34f,
        center = centerOffset
    )

    // Expanding shockwave ring during unlock flash
    if (flashIntensity > 0.01f) {
        val waveProgress = 1f - flashIntensity
        val waveRadius = minDim * (0.12f + waveProgress * 0.34f)
        drawCircle(
            color = statusColor.copy(alpha = flashIntensity * 0.75f),
            radius = waveRadius,
            center = centerOffset,
            style = ShockwaveStroke
        )
    }

    // Draw 3D inner crystal wireframe around the core
    val coreWireStroke = max(1.5f, minDim * 0.0042f)
    for (i in 0 until mesh.edgeCount) {
        if (mesh.edgeStyle[i] != STYLE_CORE_WIRE) continue
        val s = mesh.edgeStart[i]
        val e = mesh.edgeEnd[i]
        val avgZ = (mesh.viewZ[s] + mesh.viewZ[e]) * 0.5f
        val alpha = if (avgZ >= 0f) 0.90f else 0.40f
        drawLine(
            color = lerp(statusColor, Color.White, 0.45f).copy(alpha = alpha),
            start = Offset(mesh.screenX[s], mesh.screenY[s]),
            end = Offset(mesh.screenX[e], mesh.screenY[e]),
            strokeWidth = coreWireStroke,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawMeshEdgesPass(
    mesh: Capsule3DMesh,
    neonPaints: NeonGlowPaints,
    frontPass: Boolean,
    statusColor: Color,
    splitOffset: Float,
    isSyncing: Boolean,
    flashIntensity: Float,
    minDim: Float
) {
    val unitStroke = max(1f, minDim * 0.0032f)
    val scannerAccent = if (isSyncing) lerp(TokyoCyan, statusColor, 0.35f) else TokyoCyan

    // 1. Native Canvas Glow Pass for Neon Edges
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas

        for (i in 0 until mesh.edgeCount) {
            val style = mesh.edgeStyle[i]
            if (style == STYLE_CORE_WIRE) continue

            val s = mesh.edgeStart[i]
            val e = mesh.edgeEnd[i]
            val avgZ = (mesh.viewZ[s] + mesh.viewZ[e]) * 0.5f
            val isFront = avgZ >= 0f
            if (isFront != frontPass) continue

            // Energy tethers are only visible when the capsule halves separate
            if (style == STYLE_ENERGY_TETHER && splitOffset <= 0.01f) continue

            val depthFactor = ((avgZ + 1.2f) / 2.4f).coerceIn(0.18f, 1f)
            val x1 = mesh.screenX[s]
            val y1 = mesh.screenY[s]
            val x2 = mesh.screenX[e]
            val y2 = mesh.screenY[e]

            when (style) {
                STYLE_RING_PRIMARY -> {
                    val glowAlpha = (if (frontPass) 0.58f else 0.22f) * depthFactor
                    neonPaints.wireGlowPaint.color = statusColor.copy(alpha = glowAlpha).toArgb()
                    neonPaints.wireGlowPaint.strokeWidth = unitStroke * 3.2f
                    native.drawLine(x1, y1, x2, y2, neonPaints.wireGlowPaint)
                }
                STYLE_RING_COLLAR -> {
                    val collarColor = lerp(statusColor, TokyoPurple, 0.3f)
                    val glowAlpha = (if (frontPass) 0.65f else 0.25f) * depthFactor
                    neonPaints.wireGlowPaint.color = collarColor.copy(alpha = glowAlpha).toArgb()
                    neonPaints.wireGlowPaint.strokeWidth = unitStroke * 3.4f
                    native.drawLine(x1, y1, x2, y2, neonPaints.wireGlowPaint)
                }
                STYLE_RIB_VERTICAL -> {
                    if (frontPass) {
                        val ribGlow = lerp(TokyoBlue, statusColor, 0.4f)
                        neonPaints.wireGlowPaint.color = ribGlow.copy(alpha = 0.38f * depthFactor).toArgb()
                        neonPaints.wireGlowPaint.strokeWidth = unitStroke * 2.4f
                        native.drawLine(x1, y1, x2, y2, neonPaints.wireGlowPaint)
                    }
                }
                STYLE_ENERGY_TETHER -> {
                    val tetherProgress = (splitOffset / 0.36f).coerceIn(0f, 1f)
                    val tetherAlpha = (0.65f * tetherProgress + flashIntensity * 0.3f).coerceAtMost(1f) * depthFactor
                    neonPaints.wireGlowPaint.color = statusColor.copy(alpha = tetherAlpha).toArgb()
                    neonPaints.wireGlowPaint.strokeWidth = unitStroke * 3.0f
                    native.drawLine(x1, y1, x2, y2, neonPaints.wireGlowPaint)
                }
                STYLE_SCANNER_ARC, STYLE_SCANNER_TICK -> {
                    val syncBoost = if (isSyncing) 1.3f else 1.0f
                    val glowAlpha = ((if (frontPass) 0.55f else 0.20f) * depthFactor * syncBoost).coerceAtMost(1f)
                    neonPaints.scannerGlowPaint.color = scannerAccent.copy(alpha = glowAlpha).toArgb()
                    neonPaints.scannerGlowPaint.strokeWidth = unitStroke * (if (style == STYLE_SCANNER_ARC) 3.4f else 2.2f)
                    native.drawLine(x1, y1, x2, y2, neonPaints.scannerGlowPaint)
                }
            }
        }
    }

    // 2. Crisp Core Line Pass on Compose DrawScope
    for (i in 0 until mesh.edgeCount) {
        val style = mesh.edgeStyle[i]
        if (style == STYLE_CORE_WIRE) continue

        val s = mesh.edgeStart[i]
        val e = mesh.edgeEnd[i]
        val avgZ = (mesh.viewZ[s] + mesh.viewZ[e]) * 0.5f
        val isFront = avgZ >= 0f
        if (isFront != frontPass) continue

        if (style == STYLE_ENERGY_TETHER && splitOffset <= 0.01f) continue

        val depthFactor = ((avgZ + 1.2f) / 2.4f).coerceIn(0.20f, 1f)
        val start = Offset(mesh.screenX[s], mesh.screenY[s])
        val end = Offset(mesh.screenX[e], mesh.screenY[e])

        when (style) {
            STYLE_RING_PRIMARY -> {
                val baseColor = lerp(TokyoBlue, statusColor, 0.72f)
                val lineColor = if (frontPass) lerp(baseColor, Color.White, 0.28f) else baseColor
                val alpha = (if (frontPass) 0.94f else 0.36f) * depthFactor
                drawLine(
                    color = lineColor.copy(alpha = alpha),
                    start = start,
                    end = end,
                    strokeWidth = unitStroke * (if (frontPass) 1.65f else 1.15f),
                    cap = StrokeCap.Round
                )
            }
            STYLE_RING_COLLAR -> {
                val collarColor = lerp(statusColor, TokyoPurple, 0.25f)
                val alpha = (if (frontPass) 0.96f else 0.40f) * depthFactor
                drawLine(
                    color = lerp(collarColor, Color.White, if (frontPass) 0.35f else 0f).copy(alpha = alpha),
                    start = start,
                    end = end,
                    strokeWidth = unitStroke * (if (frontPass) 1.85f else 1.2f),
                    cap = StrokeCap.Round
                )
            }
            STYLE_RIB_VERTICAL -> {
                val ribColor = lerp(TokyoBlue, TokyoPurple, 0.35f)
                val alpha = (if (frontPass) 0.80f else 0.28f) * depthFactor
                drawLine(
                    color = ribColor.copy(alpha = alpha),
                    start = start,
                    end = end,
                    strokeWidth = unitStroke * (if (frontPass) 1.25f else 0.9f),
                    cap = StrokeCap.Round
                )
            }
            STYLE_RIB_DIAGONAL -> {
                val alpha = (if (frontPass) 0.45f else 0.16f) * depthFactor
                drawLine(
                    color = TokyoPurple.copy(alpha = alpha),
                    start = start,
                    end = end,
                    strokeWidth = unitStroke * 0.85f,
                    cap = StrokeCap.Round
                )
            }
            STYLE_ENERGY_TETHER -> {
                val tetherProgress = (splitOffset / 0.36f).coerceIn(0f, 1f)
                val alpha = (if (frontPass) 0.85f else 0.40f) * tetherProgress * depthFactor
                drawLine(
                    color = lerp(statusColor, TokyoCyan, 0.4f).copy(alpha = alpha),
                    start = start,
                    end = end,
                    strokeWidth = unitStroke * 1.15f,
                    cap = StrokeCap.Round
                )
            }
            STYLE_SCANNER_ARC -> {
                val alpha = (if (frontPass) 0.95f else 0.38f) * depthFactor
                val color = if (frontPass) lerp(scannerAccent, Color.White, 0.35f) else scannerAccent
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = start,
                    end = end,
                    strokeWidth = unitStroke * (if (frontPass) 1.75f else 1.1f),
                    cap = StrokeCap.Round
                )
            }
            STYLE_SCANNER_TICK -> {
                val alpha = (if (frontPass) 0.85f else 0.30f) * depthFactor
                drawLine(
                    color = statusColor.copy(alpha = alpha),
                    start = start,
                    end = end,
                    strokeWidth = unitStroke * 1.25f,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    // 3. Glowing Junction Nodes on Front-Facing Key Vertices
    if (frontPass) {
        val nodeBaseRadius = unitStroke * 1.7f
        for (v in 0 until mesh.vertexCount) {
            if (!mesh.isKeyNode[v]) continue
            val vz = mesh.viewZ[v]
            if (vz < 0f) continue

            val depthAlpha = ((vz + 0.5f) / 1.5f).coerceIn(0.3f, 1f)
            val group = mesh.vertexGroup[v]
            val nodeColor = when (group) {
                GROUP_SCANNER_OUTER, GROUP_SCANNER_INNER -> scannerAccent
                else -> statusColor
            }
            val pos = Offset(mesh.screenX[v], mesh.screenY[v])
            drawCircle(
                color = nodeColor.copy(alpha = 0.45f * depthAlpha),
                radius = nodeBaseRadius * 2.1f,
                center = pos
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.90f * depthAlpha),
                radius = nodeBaseRadius * 0.85f,
                center = pos
            )
        }
    }
}

@Preview(
    name = "Capsule 3D - Locked & Idle",
    showBackground = true,
    backgroundColor = 0xFF1A1B26,
    widthDp = 380,
    heightDp = 460
)
@Composable
private fun Capsule3DViewLockedPreview() {
    var unlocked by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TokyoBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TokyoBg)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (unlocked) "STATUS: UNLOCKED // CORE ACTIVE" else "STATUS: LOCKED // SEALED",
                    color = if (unlocked) TokyoOpen else TokyoClosed,
                    style = MaterialTheme.typography.labelLarge
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Capsule3DView(
                        isUnlocked = unlocked,
                        isSyncing = syncing,
                        onCapsuleClick = { unlocked = !unlocked },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { unlocked = !unlocked },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TokyoSurface,
                            contentColor = if (unlocked) TokyoOpen else TokyoClosed
                        )
                    ) {
                        Text(if (unlocked) "Lock Capsule" else "Unlock Capsule")
                    }
                    Button(
                        onClick = { syncing = !syncing },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TokyoSurface,
                            contentColor = if (syncing) TokyoCyan else TokyoBlue
                        )
                    ) {
                        Text(if (syncing) "Sync: OVERDRIVE" else "Sync: NORMAL")
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Capsule 3D - Unlocked & Syncing",
    showBackground = true,
    backgroundColor = 0xFF1A1B26,
    widthDp = 380,
    heightDp = 460
)
@Composable
private fun Capsule3DViewUnlockedPreview() {
    var unlocked by remember { mutableStateOf(true) }
    var syncing by remember { mutableStateOf(true) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TokyoBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TokyoBg)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (unlocked) "STATUS: UNLOCKED // SYNC OVERDRIVE" else "STATUS: LOCKED // SEALED",
                    color = if (unlocked) TokyoOpen else TokyoClosed,
                    style = MaterialTheme.typography.labelLarge
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Capsule3DView(
                        isUnlocked = unlocked,
                        isSyncing = syncing,
                        onCapsuleClick = { unlocked = !unlocked },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { unlocked = !unlocked },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TokyoSurface,
                            contentColor = if (unlocked) TokyoOpen else TokyoClosed
                        )
                    ) {
                        Text(if (unlocked) "Lock Capsule" else "Unlock Capsule")
                    }
                    Button(
                        onClick = { syncing = !syncing },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TokyoSurface,
                            contentColor = if (syncing) TokyoCyan else TokyoBlue
                        )
                    ) {
                        Text(if (syncing) "Sync: OVERDRIVE" else "Sync: NORMAL")
                    }
                }
            }
        }
    }
}