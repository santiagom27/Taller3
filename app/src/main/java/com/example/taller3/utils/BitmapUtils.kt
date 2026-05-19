package com.example.taller3.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import com.example.taller3.R

object BitmapUtils {

    /**
     * Crea un marcador circular personalizado a partir de un Bitmap.
     * Dibuja un círculo con borde de color y una flecha abajo (como pin).
     */
    fun createCustomMarkerFromBitmap(context: Context, bitmap: Bitmap, isCurrentUser: Boolean): Bitmap {
        val size = 120
        val output = Bitmap.createBitmap(size + 20, size + 30, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val borderColor = if (isCurrentUser) Color.parseColor("#4CAF50") else Color.parseColor("#2196F3")
        val borderWidth = 8f

        // Sombra
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44000000")
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle((size / 2 + 10).toFloat(), (size / 2 + 10).toFloat(), (size / 2).toFloat(), shadowPaint)

        // Borde
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle((size / 2 + 10).toFloat(), (size / 2 + 10).toFloat(), (size / 2 + borderWidth / 2).toFloat(), borderPaint)

        // Recorte circular del bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, false)
        val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        clipPaint.shader = shader
        canvas.drawCircle((size / 2 + 10).toFloat(), (size / 2 + 10).toFloat(), (size / 2 - borderWidth).toFloat(), clipPaint)

        // Triángulo (pin)
        val path = Path()
        val cx = (size / 2 + 10).toFloat()
        val top = (size + 10).toFloat()
        val bottom = (size + 30).toFloat()
        path.moveTo(cx - 10f, top)
        path.lineTo(cx + 10f, top)
        path.lineTo(cx, bottom)
        path.close()
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor }
        canvas.drawPath(path, pinPaint)

        return output
    }

    /**
     * Crea un marcador genérico con inicial del nombre cuando no hay foto.
     */
    fun createInitialMarker(context: Context, initial: String, isCurrentUser: Boolean): Bitmap {
        val size = 120
        val output = Bitmap.createBitmap(size + 20, size + 30, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val bgColor = if (isCurrentUser) Color.parseColor("#4CAF50") else Color.parseColor("#2196F3")

        // Fondo del círculo
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawCircle((size / 2 + 10).toFloat(), (size / 2 + 10).toFloat(), (size / 2).toFloat(), bgPaint)

        // Texto con la inicial
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 52f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val cx = (size / 2 + 10).toFloat()
        val cy = (size / 2 + 10 - (textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(initial.uppercase(), cx, cy, textPaint)

        // Pin
        val path = Path()
        val top = (size + 10).toFloat()
        val bottom = (size + 30).toFloat()
        path.moveTo(cx - 10f, top)
        path.lineTo(cx + 10f, top)
        path.lineTo(cx, bottom)
        path.close()
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawPath(path, pinPaint)

        return output
    }
}