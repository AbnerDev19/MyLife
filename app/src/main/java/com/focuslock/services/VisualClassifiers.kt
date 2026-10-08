package com.focuslock.services

import android.content.Context
import android.graphics.Bitmap
import com.focuslock.domain.ContentLevel
import com.focuslock.domain.ImageClassifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/** Fallback local. Não é um modelo NSFW: serve apenas para não deixar o recurso completamente inoperante quando o modelo não está empacotado. */
class SkinHeuristicClassifier : ImageClassifier {
    override val available = true
    override val description = "Detector visual local aproximado"

    override fun classify(bitmap: Bitmap): ContentLevel {
        val w = 128
        val h = 128
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()

        var skin = 0
        var skinStrong = 0
        for (p in px) {
            val r = (p ushr 16) and 255
            val g = (p ushr 8) and 255
            val b = p and 255
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            val likelySkin = r > 80 && g > 30 && b > 15 &&
                r > g && r > b && (r - g) > 8 && mx - mn > 18
            if (likelySkin) {
                skin++
                if (r > 120 && g > 55 && b > 25 && r > g * 1.12f) skinStrong++
            }
        }
        val ratio = skin.toFloat() / px.size
        val strongRatio = skinStrong.toFloat() / px.size
        return when {
            ratio >= 0.52f && strongRatio >= 0.30f -> ContentLevel.EXPLICIT
            ratio >= 0.40f && strongRatio >= 0.22f -> ContentLevel.SUGGESTIVE
            ratio >= 0.28f && strongRatio >= 0.14f -> ContentLevel.SUGGESTIVE
            else -> ContentLevel.SAFE
        }
    }
}

/** Compatível com o modelo de 5 classes do nsfw_model: drawings, hentai, neutral, porn, sexy. */
class TfliteClassifier private constructor(
    private val interpreter: Interpreter,
    private val width: Int,
    private val height: Int
) : ImageClassifier {
    override val available = true
    override val description = "Modelo TensorFlow Lite NSFW"

    override fun classify(bitmap: Bitmap): ContentLevel {
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val px = IntArray(width * height)
        scaled.getPixels(px, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()
        val buf = ByteBuffer.allocateDirect(4 * width * height * 3).order(ByteOrder.nativeOrder())
        for (p in px) {
            buf.putFloat(((p ushr 16) and 255) / 255f)
            buf.putFloat(((p ushr 8) and 255) / 255f)
            buf.putFloat((p and 255) / 255f)
        }
        buf.rewind()
        val out = Array(1) { FloatArray(5) }
        interpreter.run(buf, out)
        val o = out[0]
        val hentai = o[1]
        val porn = o[3]
        val sexy = o[4]
        return when {
            porn >= 0.70f || hentai >= 0.80f || porn + hentai >= 0.85f -> ContentLevel.EXPLICIT
            porn >= 0.35f || hentai >= 0.45f || sexy >= 0.75f -> ContentLevel.SUGGESTIVE
            sexy >= 0.50f -> ContentLevel.SUGGESTIVE
            else -> ContentLevel.SAFE
        }
    }

    fun close() = interpreter.close()

    companion object {
        fun load(ctx: Context): TfliteClassifier? = try {
            val bytes = ctx.assets.open("nsfw.tflite").use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            model.put(bytes).rewind()
            val interp = Interpreter(model)
            val input = interp.getInputTensor(0)
            val output = interp.getOutputTensor(0)
            val shape = input.shape()
            if (input.dataType() == DataType.FLOAT32 && shape.size == 4 && output.shape().last() == 5) {
                TfliteClassifier(interp, shape[2], shape[1])
            } else {
                interp.close(); null
            }
        } catch (_: Throwable) { null }
    }
}

object ImageClassifierFactory {
    fun create(ctx: Context): ImageClassifier = TfliteClassifier.load(ctx) ?: SkinHeuristicClassifier()
}
