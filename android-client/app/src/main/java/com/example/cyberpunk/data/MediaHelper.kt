package com.example.cyberpunk.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

object MediaHelper {

    private const val MAX_IMAGE_DIMENSION = 1280
    private const val JPEG_QUALITY = 75

    /**
     * Создает временный файл в cacheDir/capsule-media и возвращает пару (File, Content Uri через FileProvider)
     * для использования в TakePicture ActivityResultContract.
     */
    fun createCameraTempFile(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "capsule-media").apply { mkdirs() }
        val file = File(dir, "photo_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Pair(file, uri)
    }

    /**
     * Загружает изображение по Uri, корректирует ориентацию EXIF,
     * масштабирует до максимального размера 1280 px по длинной стороне
     * и сжимает в JPEG с качеством 75%.
     */
    fun processAndCompressImage(context: Context, imageUri: Uri): Pair<ByteArray, String>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val rawBytes = inputStream.use { it.readBytes() }

            // Читаем ориентацию EXIF
            val orientation = try {
                val exifStream = context.contentResolver.openInputStream(imageUri)
                exifStream?.use {
                    val exif = ExifInterface(it)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // Получаем размеры
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // Вычисляем inSampleSize
            var sampleSize = 1
            var maxDim = maxOf(origWidth, origHeight)
            while (maxDim / 2 >= MAX_IMAGE_DIMENSION) {
                sampleSize *= 2
                maxDim /= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions) ?: return null

            // Корректируем поворот
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            val rotatedBitmap = if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(
                    sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true
                )
                if (rotated != sampledBitmap) sampledBitmap.recycle()
                rotated
            } else {
                sampledBitmap
            }

            // Масштабируем по длинной стороне до 1280 px
            val curMax = maxOf(rotatedBitmap.width, rotatedBitmap.height)
            val finalBitmap = if (curMax > MAX_IMAGE_DIMENSION) {
                val scale = MAX_IMAGE_DIMENSION.toFloat() / curMax
                val targetW = (rotatedBitmap.width * scale).toInt()
                val targetH = (rotatedBitmap.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true)
                if (scaled != rotatedBitmap) rotatedBitmap.recycle()
                scaled
            } else {
                rotatedBitmap
            }

            val outputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            finalBitmap.recycle()

            val compressedBytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            Pair(compressedBytes, base64)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Очищает кэшированные медиа-файлы приложения при выходе из аккаунта.
     */
    fun clearMediaCache(context: Context) {
        try {
            val mediaDir = File(context.cacheDir, "capsule-media")
            if (mediaDir.exists()) {
                mediaDir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }
}

/**
 * Хелпер управления записью голоса через MediaRecorder:
 * MPEG_4 + AAC, 64 kbps, 44.1 kHz, автостоп 60 сек.
 */
class VoiceRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var outputFile: File? = null
        private set
    var isRecording: Boolean = false
        private set
    private var startTimeMillis: Long = 0L

    fun startRecording(onMaxDurationReached: () -> Unit = {}): File? {
        stopRecording()
        val dir = File(context.cacheDir, "capsule-media").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.mp4")
        outputFile = file

        return try {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                setMaxDuration(60_000) // 60 секунд автостоп
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopRecording()
                        onMaxDurationReached()
                    }
                }
                prepare()
                start()
            }
            recorder = mr
            isRecording = true
            startTimeMillis = System.currentTimeMillis()
            file
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            isRecording = false
            outputFile = null
            null
        }
    }

    fun stopRecording(): Long {
        if (!isRecording) return 0L
        val duration = System.currentTimeMillis() - startTimeMillis
        try {
            recorder?.stop()
        } catch (_: Exception) {
        } finally {
            recorder?.release()
            recorder = null
            isRecording = false
        }
        return duration
    }

    fun release() {
        if (isRecording) {
            stopRecording()
        }
        recorder?.release()
        recorder = null
        isRecording = false
    }
}

/**
 * Хелпер воспроизведения аудиофайлов через MediaPlayer.
 */
class VoicePlayerHelper {
    private var player: MediaPlayer? = null
    var isPlaying: Boolean = false
        private set

    fun play(file: File, onCompletion: () -> Unit = {}) {
        stop()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    this@VoicePlayerHelper.isPlaying = false
                    onCompletion()
                }
                prepare()
                start()
            }
            isPlaying = true
        } catch (e: Exception) {
            stop()
        }
    }

    fun pause() {
        if (isPlaying) {
            player?.pause()
            isPlaying = false
        }
    }

    fun resume() {
        player?.let {
            if (!isPlaying) {
                it.start()
                isPlaying = true
            }
        }
    }

    fun stop() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        } finally {
            player = null
            isPlaying = false
        }
    }

    fun release() {
        stop()
    }

    fun currentPosition(): Int = player?.currentPosition ?: 0
    fun duration(): Int = player?.duration ?: 0
}
