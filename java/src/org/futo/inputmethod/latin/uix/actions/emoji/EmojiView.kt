// Note: Mainly lifted from androidx.emoji2.emojipicker
/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.futo.inputmethod.latin.uix.actions.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.emojiNeedsCompat


class EmojiView @JvmOverloads constructor(
    context: Context,
    rippleColor: Int,
    val emojiTypeface: Typeface?,
    attrs: AttributeSet? = null,
) :
    View(context, attrs) {

    companion object {
        private const val EMOJI_DRAW_TEXT_SIZE_DP = 48
    }

    init {
        background = AppCompatResources.getDrawable(context, R.drawable.ripple_emoji_view)
        background.setTint(rippleColor)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    internal var willDrawVariantIndicator: Boolean = true
    private var raisedStyle: Boolean = true
    private var textScale: Float = 1.0f

    /** Raised 2.5D surface, with original Unicode emoji font retained. */
    fun setRaisedStyle(enabled: Boolean) {
        raisedStyle = enabled
    }

    private val reliefPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun Canvas.drawRaisedEmojiSurface() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val inset = minOf(w, h) * 0.11f
        val drop = minOf(w, h) * 0.06f
        val radius = minOf(w, h) * 0.33f
        val left = inset
        val top = inset
        val right = w - inset
        val bottom = h - inset - drop

        reliefPaint.shader = null
        reliefPaint.style = Paint.Style.FILL
        reliefPaint.color = Color.argb(95, 0, 0, 0)
        drawRoundRect(
            left + drop * 0.4f, top + drop, right + drop * 0.4f,
            bottom + drop, radius, radius, reliefPaint
        )
        reliefPaint.shader = LinearGradient(
            0f, top, 0f, bottom,
            intArrayOf(
                Color.argb(165, 255, 255, 255),
                Color.argb(75, 255, 255, 255),
                Color.argb(105, 30, 36, 53)
            ),
            floatArrayOf(0.0f, 0.48f, 1.0f),
            Shader.TileMode.CLAMP
        )
        drawRoundRect(left, top, right, bottom, radius, radius, reliefPaint)
        reliefPaint.shader = null
        reliefPaint.color = Color.argb(150, 255, 255, 255)
        reliefPaint.style = Paint.Style.STROKE
        reliefPaint.strokeWidth = minOf(w, h) * 0.018f
        drawRoundRect(
            left + reliefPaint.strokeWidth, top + reliefPaint.strokeWidth,
            right - reliefPaint.strokeWidth, bottom - reliefPaint.strokeWidth,
            radius, radius, reliefPaint
        )
        reliefPaint.style = Paint.Style.FILL
    }
    private var isAbleToScaleHorizontally = false

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            EMOJI_DRAW_TEXT_SIZE_DP.toFloat(),
            context.resources.displayMetrics
        )

        emojiTypeface?.let { setTypeface(it) }
    }

    fun setTextColor(color: Int) {
        textPaint.color = color
    }

    fun setTextSizeMultiplier(v: Float) {
        isAbleToScaleHorizontally = v < 1.0f
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            EMOJI_DRAW_TEXT_SIZE_DP.toFloat() * v,
            context.resources.displayMetrics
        )
    }

    private val offscreenCanvasBitmap: Bitmap = with(textPaint.fontMetricsInt) {
        val size = bottom - top
        createBitmap(size, size)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec) - context.resources.getDimensionPixelSize(R.dimen.emoji_picker_emoji_view_padding),
            MeasureSpec.getSize(heightMeasureSpec) - context.resources.getDimensionPixelSize(R.dimen.emoji_picker_emoji_view_padding)
        )
    }

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.run {
            val bmpW = offscreenCanvasBitmap.width.toFloat()
            val bmpH = offscreenCanvasBitmap.height.toFloat()

            val sX = width.toFloat() / bmpW
            val sY = height.toFloat() / bmpH

            val scale = minOf(sX, sY)

            val scaledW = (bmpW * scale / textScale).coerceAtMost(width.toFloat())
            val scaledH = (bmpH * scale).coerceAtMost(height.toFloat())

            val left = (width  - scaledW) / 2f
            val top  = (height - scaledH) / 2f

            drawBitmap(offscreenCanvasBitmap, null, RectF(left, top, left + scaledW, top + scaledH), bitmapPaint)
        }
    }

    var emoji: EmojiItem? = null
        set(value) {
            field = value

            post {
                if (value != null) {
                    if (value == this.emoji) {
                        offscreenCanvasBitmap.eraseColor(Color.TRANSPARENT)
                        drawEmoji(
                            value.emoji,
                            drawVariantIndicator = willDrawVariantIndicator && value.skinTones
                        )
                        contentDescription = value.emoji
                    }
                    invalidate()
                } else {
                    //offscreenCanvasBitmap.eraseColor(Color.TRANSPARENT)
                }
            }
        }

    private fun drawEmoji(emoji: CharSequence, drawVariantIndicator: Boolean) {
        //offscreenCanvasBitmap.eraseColor(Color.TRANSPARENT)
        offscreenCanvasBitmap.applyCanvas {
            if (raisedStyle) drawRaisedEmojiSurface()
            if (emoji is Spanned) {
                createStaticLayout(emoji, width).draw(this)
            } else {
                textPaint.typeface = if(emojiNeedsCompat(emoji.toString(), emojiTypeface)) {
                    emojiTypeface
                } else {
                    null
                }

                var textWidth = textPaint.measureText(emoji, 0, emoji.length)
                val fm = textPaint.fontMetrics
                var textHeight = fm.descent - fm.ascent

                if(textWidth > width) {
                    val scaleNeeded = width / textWidth
                    if(isAbleToScaleHorizontally || scaleNeeded < 0.4f) {
                        scale(scaleNeeded, 1.0f)
                    } else {
                        scale(scaleNeeded, scaleNeeded)
                        textHeight *= scaleNeeded
                    }
                    textScale = width / textWidth
                    textWidth = width.toFloat()
                } else {
                    scale(1.0f, 1.0f)
                    textScale = 1.0f
                }

                val textCenterY = height / 2f
                val baselineY = textCenterY - textHeight / 2f - fm.ascent

                if (raisedStyle) {
                    textPaint.setShadowLayer(
                        width * 0.025f, width * 0.014f, height * 0.022f,
                        Color.argb(135, 0, 0, 0)
                    )
                }
                drawText(
                    emoji,
                    /* start = */ 0,
                    /* end = */ emoji.length,
                    /* x = */ (width - textWidth) / 2,
                    /* y = */ baselineY,
                    textPaint,
                )
                textPaint.clearShadowLayer()
            }

            if (drawVariantIndicator) {
                AppCompatResources.getDrawable(context, R.drawable.variant_availability_indicator)?.apply {
                    val canvasWidth = this@applyCanvas.width
                    val canvasHeight = this@applyCanvas.height
                    val indicatorWidth =
                        context.resources.getDimensionPixelSize(
                            R.dimen.variant_availability_indicator_width
                        )
                    val indicatorHeight =
                        context.resources.getDimensionPixelSize(
                            R.dimen.variant_availability_indicator_height
                        )
                    bounds = Rect(
                        canvasWidth - indicatorWidth,
                        canvasHeight - indicatorHeight,
                        canvasWidth,
                        canvasHeight
                    )
                }!!.draw(this)
            }

        }
    }

    @RequiresApi(23)
    internal object Api23Impl {
        fun createStaticLayout(emoji: Spanned, textPaint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(
                emoji, 0, emoji.length, textPaint, width
            ).apply {
                setAlignment(Layout.Alignment.ALIGN_CENTER)
                setLineSpacing(/* spacingAdd = */ 0f, /* spacingMult = */ 1f)
                setIncludePad(false)
            }.build()
    }

    private fun createStaticLayout(emoji: Spanned, width: Int): StaticLayout {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Api23Impl.createStaticLayout(emoji, textPaint, width)
        } else {
            @Suppress("DEPRECATION")
            return StaticLayout(
                emoji,
                textPaint,
                width,
                Layout.Alignment.ALIGN_CENTER,
                /* spacingmult = */ 1f,
                /* spacingadd = */ 0f,
                /* includepad = */ false,
            )
        }
    }
}