// Scenic on-device vector artwork for MG5 Dashboard.
// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.sin

/**
 * Artwork is bundled in the APK and rendered with Canvas: no network images,
 * external assets, or potentially distracting animation required.
 */
class Mg5SceneDrawable(private val mode: Mode) : Drawable() {
    enum class Mode { RIDE, HOME, CLIMATE }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sky = Color.rgb(8, 29, 58)
    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val saved = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        val scaleX = w / 1000f
        val scaleY = h / 400f
        canvas.scale(scaleX, scaleY)
        val rounded = Path().apply { addRoundRect(RectF(0f, 0f, 1000f, 400f), 35f, 35f, Path.Direction.CW) }
        canvas.clipPath(rounded)
        p.shader = LinearGradient(0f, 0f, 1000f, 400f,
            intArrayOf(Color.rgb(11, 76, 128), Color.rgb(9, 38, 77), Color.rgb(4, 19, 42)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1000f, 400f, p)
        p.shader = null
        when (mode) {
            Mode.RIDE -> city(canvas)
            Mode.HOME -> home(canvas)
            Mode.CLIMATE -> climate(canvas)
        }
        // Darken the right hand side for clear RTL Arabic typography.
        p.shader = LinearGradient(100f, 0f, 1000f, 0f,
            intArrayOf(0x11051A36, 0xAA061831.toInt(), 0xED06152D.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1000f, 400f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.6f
        p.color = 0xFF148ACC.toInt()
        canvas.drawRoundRect(RectF(1f, 1f, 999f, 399f), 35f, 35f, p)
        p.style = Paint.Style.FILL
        canvas.restoreToCount(saved)
    }
    private fun city(canvas: Canvas) {
        p.color = 0xFF18466E.toInt()
        // Hills and distant Riyadh skyline.
        val horizon = Path().apply {
            moveTo(0f, 238f); cubicTo(145f, 180f, 270f, 221f, 500f, 227f)
            lineTo(510f, 400f); lineTo(0f, 400f); close()
        }
        canvas.drawPath(horizon, p)
        val buildings = listOf(
            floatArrayOf(8f, 183f, 40f, 250f), floatArrayOf(55f, 133f, 94f, 255f),
            floatArrayOf(112f, 168f, 139f, 252f), floatArrayOf(152f, 103f, 198f, 259f),
            floatArrayOf(222f, 146f, 260f, 250f), floatArrayOf(282f, 179f, 308f, 253f),
            floatArrayOf(326f, 91f, 367f, 259f), floatArrayOf(397f, 128f, 428f, 250f),
            floatArrayOf(453f, 164f, 494f, 258f)
        )
        buildings.forEachIndexed { index, r ->
            p.color = if (index % 2 == 0) 0xFF0D3660.toInt() else 0xFF103F6B.toInt()
            canvas.drawRect(r[0], r[1], r[2], r[3], p)
            p.color = 0xAA62D8FC.toInt()
            var x = r[0] + 8
            while (x < r[2] - 4) {
                var y = r[1] + 15
                while (y < r[3] - 8) {
                    canvas.drawCircle(x, y, 1.8f, p)
                    y += 18
                }
                x += 12
            }
        }
        // Kingdom Centre tower profile: a distinctive local visual, not a photo.
        p.color = 0xFF1C6A93.toInt()
        val tower = Path().apply {
            moveTo(203f, 230f); lineTo(210f, 51f); lineTo(251f, 51f)
            lineTo(260f, 230f); close()
        }
        canvas.drawPath(tower, p)
        p.color = sky
        canvas.drawOval(RectF(220f, 59f, 244f, 86f), p)
        p.color = 0xFF12BBF3.toInt()
        canvas.drawRect(209f, 96f, 252f, 101f, p)
        // Road perspective, bright cyan reflective road edges.
        val road = Path().apply {
            moveTo(0f, 400f); lineTo(240f, 245f); lineTo(420f, 245f)
            lineTo(560f, 400f); close()
        }
        p.color = 0xFF091C39.toInt()
        canvas.drawPath(road, p)
        p.color = 0xFF0FB4EF.toInt(); p.strokeWidth = 4f; p.style = Paint.Style.STROKE
        val lane = Path().apply {
            moveTo(75f, 400f); lineTo(272f, 251f)
            moveTo(500f, 400f); lineTo(399f, 251f)
        }
        canvas.drawPath(lane, p); p.style = Paint.Style.FILL
    }
    private fun home(canvas: Canvas) {
        p.color = 0xFF113858.toInt()
        canvas.drawRect(0f, 156f, 630f, 400f, p)
        val roof = Path().apply {
            moveTo(75f, 163f); lineTo(285f, 45f); lineTo(465f, 163f); close()
        }
        p.color = 0xFF176089.toInt(); canvas.drawPath(roof, p)
        p.color = 0xFF0A2B4C.toInt()
        canvas.drawRect(130f, 162f, 445f, 400f, p)
        // Warm entrance lamp and doorway.
        p.shader = LinearGradient(240f, 145f, 340f, 360f,
            0xFFFFC770.toInt(), 0xFF8F663B.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(238f, 162f, 354f, 400f), 7f, 7f, p)
        p.shader = null
        p.color = 0xFFFFDA96.toInt();canvas.drawCircle(350f, 280f, 4.7f, p)
        p.color = 0xFF184C69.toInt()
        canvas.drawRect(170f, 192f, 216f, 250f, p)
        canvas.drawRect(378f, 192f, 420f, 250f, p)
        p.color = 0xFFFFD78C.toInt()
        canvas.drawRect(182f, 203f, 207f, 241f, p)
        canvas.drawRect(388f, 203f, 411f, 241f, p)
    }
    private fun climate(canvas: Canvas) {
        p.color = 0xFF133E5C.toInt()
        canvas.drawRoundRect(RectF(75f, 45f, 375f, 290f), 26f, 26f, p)
        p.color = 0xFF071B30.toInt()
        canvas.drawRoundRect(RectF(105f, 68f, 341f, 260f), 17f, 17f, p)
        p.color = 0xFF3783A4.toInt()
        repeat(5) { i ->
            val y = 87f + i*30
            canvas.drawRoundRect(RectF(116f, y, 329f, y+10f), 5f, 5f, p)
        }
        p.style = Paint.Style.STROKE
        repeat(4) { i ->
            p.color = 0x6623C6FC
            p.strokeWidth = 5f
            val air = Path().apply {
                moveTo(340f, 89f+i*40)
                cubicTo(550f, 45f+i*35, 620f, 300f-i*22, 780f, 95f+i*28)
            }
            canvas.drawPath(air,p)
        }
        p.style = Paint.Style.FILL
    }
    override fun setAlpha(alpha: Int) { p.alpha=alpha;invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { p.colorFilter=filter;invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class Mg5GlyphDrawable(private val kind: String) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val b = bounds; if(b.width()==0||b.height()==0)return
        val saved=canvas.save()
        canvas.translate(b.left.toFloat(),b.top.toFloat())
        canvas.scale(b.width()/100f,b.height()/100f)
        p.style=Paint.Style.FILL
        val primary=when(kind){
            "youtube"->0xFFFE3154.toInt()
            "music"->0xFFC670F6.toInt()
            "radio"->0xFFFFB53F.toInt()
            "maps"->0xFF30D6C9.toInt()
            else->0xFF30DFF1.toInt()
        }
        p.color=primary
        when(kind) {
            "apps" -> for(x in 0..1)for(y in 0..1) canvas.drawRoundRect(RectF(12f+x*40,12f+y*40,45f+x*40,45f+y*40),7f,7f,p)
            "youtube" -> {
                canvas.drawRoundRect(RectF(8f,21f,92f,79f),14f,14f,p)
                p.color=Color.WHITE
                val triangle=Path().apply{moveTo(42f,33f);lineTo(42f,67f);lineTo(70f,50f);close()}
                canvas.drawPath(triangle,p)
            }
            "home" -> {
                val roof=Path().apply{moveTo(10f,45f);lineTo(50f,8f);lineTo(90f,45f);lineTo(80f,55f);lineTo(78f,87f);lineTo(57f,87f);lineTo(57f,63f);lineTo(43f,63f);lineTo(43f,87f);lineTo(22f,87f);lineTo(20f,55f);close()}
                canvas.drawPath(roof,p)
            }
            "climate" -> {
                p.style=Paint.Style.STROKE;p.strokeWidth=8f;p.strokeCap=Paint.Cap.ROUND
                for(i in 0..5){val ang=Math.PI*i/3;canvas.drawLine(50f,50f,(50+38*kotlin.math.cos(ang)).toFloat(),(50+38*kotlin.math.sin(ang)).toFloat(),p)}
                p.style=Paint.Style.FILL;canvas.drawCircle(50f,50f,8f,p)
            }
            "music" -> {
                p.strokeWidth=10f
                canvas.drawRect(56f,20f,65f,72f,p)
                canvas.drawRect(19f,29f,28f,76f,p)
                canvas.drawRect(22f,18f,65f,29f,p)
                canvas.drawOval(RectF(4f,67f,32f,88f),p)
                canvas.drawOval(RectF(42f,64f,72f,85f),p)
            }
            "radio" -> {
                canvas.drawRoundRect(RectF(12f,37f,88f,82f),9f,9f,p)
                p.style=Paint.Style.STROKE;p.strokeWidth=5f
                canvas.drawLine(25f,38f,77f,13f,p)
                p.style=Paint.Style.FILL;p.color=0xFF102541.toInt()
                canvas.drawCircle(69f,60f,13f,p)
                for (i in 0..2) canvas.drawRect(23f,51f+i*9,47f,54f+i*9,p)
            }
            "maps" -> {
                val pin=Path().apply{
                    moveTo(50f,8f);cubicTo(0f,8f,5f,53f,50f,94f)
                    cubicTo(95f,53f,100f,8f,50f,8f);close()
                }
                canvas.drawPath(pin,p);p.color=0xFF0F2245.toInt()
                canvas.drawCircle(50f,42f,16f,p)
            }
            else -> canvas.drawCircle(50f,50f,31f,p)
        }
        canvas.restoreToCount(saved)
    }
    override fun setAlpha(alpha: Int) {p.alpha=alpha;invalidateSelf()}
    @Suppress("DEPRECATION")
    override fun setColorFilter(filter:android.graphics.ColorFilter?) {p.colorFilter=filter;invalidateSelf()}
    @Suppress("DEPRECATION")
    override fun getOpacity()=PixelFormat.TRANSLUCENT
}
