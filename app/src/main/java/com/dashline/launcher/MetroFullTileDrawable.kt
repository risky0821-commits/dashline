package com.dashline.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

/** Offline full-tile app illustrations, not floating central shortcut glyphs. */
class MetroFullTileDrawable(
    private val type: String,
    private val themeColor: Int,
    private val appIcon: Drawable? = null
) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun stroke(canvas:Canvas, x:Float,y:Float,x2:Float,y2:Float,c:Int,width:Float){
        p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=width
        p.strokeCap=Paint.Cap.ROUND;p.color=c
        canvas.drawLine(x,y,x2,y2,p);p.style=Paint.Style.FILL
    }
    override fun draw(canvas: Canvas) {
        val b=bounds
        if(b.width()<=0||b.height()<=0)return
        val tileWidth = b.width().toFloat()
        val tileHeight = b.height().toFloat()
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.clipRect(0f, 0f, tileWidth, tileHeight)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, tileWidth, tileHeight,
            intArrayOf(themeColor, darken(themeColor,0.9f), darken(themeColor,0.64f)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, tileWidth, tileHeight, p)
        p.shader = null
        // Uniform "centerCrop" geometry for full-bleed decorations.
        // Non-uniform canvas.scale(x, y) distorted square icons on 1x1 tiles.
        val artSave = canvas.save()
        val cover = max(tileWidth / 480f, tileHeight / 300f)
        canvas.translate((tileWidth - 480f * cover) / 2f,
            (tileHeight - 300f * cover) / 2f)
        canvas.scale(cover, cover)
        when(type){
            "maps" -> {
                for(i in -1..8){
                    val x=i*78f
                    stroke(canvas,x,-40f,x+155f,330f,0x447DDEFF,16f)
                    stroke(canvas,-30f,x-24f,510f,x+70f,0x555DB7CE,12f)
                }
                val route=Path().apply {
                    moveTo(35f,300f);cubicTo(95f,170f,195f,247f,240f,140f)
                    cubicTo(270f,70f,370f,130f,410f,0f)
                }
                p.color=0xFF24D0E8.toInt();p.strokeWidth=19f;p.style=Paint.Style.STROKE
                p.strokeCap=Paint.Cap.ROUND;canvas.drawPath(route,p);p.style=Paint.Style.FILL
            }
            "music" -> {
                p.color=0x8837166E.toInt();canvas.drawCircle(140f,135f,142f,p)
                p.color=0xCC160A37.toInt();canvas.drawCircle(140f,135f,100f,p)
                p.color=0xFFBB78F5.toInt();canvas.drawCircle(140f,135f,26f,p)
                for(i in 0..18) {
                    val h=28f+(i*37%108)
                    p.color=0x7756F8F7
                    canvas.drawRoundRect(RectF(220f+i*14f,148f-h/2f,
                        230f+i*14f,148f+h/2f),4f,4f,p)
                }
            }
            "radio" -> {
                p.color=0x774D0A33;canvas.drawRoundRect(RectF(30f,24f,445f,235f),24f,24f,p)
                stroke(canvas,65f,53f,255f,6f,0x99FCE4CF.toInt(),9f)
                p.color=0xFFCF8495.toInt();canvas.drawCircle(352f,153f,59f,p)
                p.color=0xFF352540.toInt();canvas.drawCircle(352f,153f,41f,p)
                for(i in 0..10) stroke(canvas,73f+i*21f,112f,73f+i*21f,170f,0x88FFE4DE.toInt(),5f)
            }
            "ac" -> {
                p.color=0xAA0A2B49.toInt()
                canvas.drawRoundRect(RectF(35f,25f,440f,170f),19f,19f,p)
                for(i in 0..6)stroke(canvas,57f,58f+i*13f,419f,58f+i*13f,0x9998EFFF.toInt(),7f)
                for(i in 0..4)stroke(canvas,50f+i*93f,200f,78f+i*93f,281f,0x7783E9FF,9f)
            }
            "wifi" -> {
                for(i in 0..3){
                    val r=65f+i*49f
                    p.color=0x8892FFF1.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=13f
                    canvas.drawArc(RectF(136f-r,163f-r,136f+r,163f+r),218f,104f,false,p)
                }
                p.style=Paint.Style.FILL;p.color=0xFFB8FFF9.toInt();canvas.drawCircle(136f,197f,14f,p)
            }
            "apps","fav_custom" -> {
                for(y in 0..3)for(x in 0..4){
                    p.color=if((x+y)%2==0)0x6649E5F5 else 0x556AA6FF
                    canvas.drawRoundRect(RectF(27f+x*93f,20f+y*69f,
                        99f+x*93f,74f+y*69f),6f,6f,p)
                }
            }
            "settings" -> for(i in 0..2){
                val r=110f-i*25f
                p.style=Paint.Style.STROKE;p.color=0x7775BBEA;p.strokeWidth=19f
                canvas.drawCircle(173f,125f,r,p)
                p.style=Paint.Style.FILL
            }
            "youtube","fav_video" -> {
                p.color=0xD9D62355.toInt();canvas.drawRoundRect(RectF(35f,18f,418f,230f),26f,26f,p)
                p.color=Color.WHITE
                val triangle=Path().apply {moveTo(196f,60f);lineTo(196f,193f);lineTo(309f,125f);close()}
                canvas.drawPath(triangle,p)
            }
            else -> {
                for(i in 0..12){
                    val x=i*44f
                    p.color=if(i%2==0)0x3344CCF6 else 0x224B9AFF
                    canvas.drawRect(x,130f-i%4*18f,x+34f,300f,p)
                }
            }
        }
        canvas.restoreToCount(artSave)
        // Draw the genuine app icon after restoring real screen coordinates.
        // It is never stretched, even on 1x1, 2x1 or 2x2 resized tiles.
        appIcon?.let { original ->
            val icon = original.constantState?.newDrawable()?.mutate()
                ?: original.mutate()
            val old = Rect(icon.bounds)
            val naturalW = icon.intrinsicWidth.coerceAtLeast(1).toFloat()
            val naturalH = icon.intrinsicHeight.coerceAtLeast(1).toFloat()
            val aspect = naturalW / naturalH
            val diameter = min(
                tileWidth * (if (tileWidth > tileHeight * 1.3f) 0.39f else 0.65f),
                tileHeight * 0.60f
            )
            val width = if (aspect >= 1f) diameter else diameter * aspect
            val height = if (aspect >= 1f) diameter / aspect else diameter
            val centerX = if(tileWidth > tileHeight * 1.3f)
                tileWidth * 0.33f else tileWidth * 0.5f
            val centerY = tileHeight * 0.39f
            icon.setBounds(
                (centerX - width / 2f).toInt(),
                (centerY - height / 2f).toInt(),
                (centerX + width / 2f).toInt(),
                (centerY + height / 2f).toInt()
            )
            icon.draw(canvas)
            icon.bounds = old
        }
        // Bottom text scrim in actual tile coordinates, not 480:300 geometry.
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, tileHeight * 0.51f,
            0f, tileHeight, 0x00000000, 0xD4000A18.toInt(),
            Shader.TileMode.CLAMP)
        canvas.drawRect(0f, tileHeight * 0.51f,tileWidth,tileHeight,p)
        p.shader = null
        canvas.restoreToCount(save)
    }
    private fun darken(c:Int,f:Float)=Color.rgb(
        (Color.red(c)*f).toInt().coerceIn(0,255),
        (Color.green(c)*f).toInt().coerceIn(0,255),
        (Color.blue(c)*f).toInt().coerceIn(0,255))
    override fun setAlpha(alpha:Int){p.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?){p.colorFilter=filter;invalidateSelf()}
    @Suppress("DEPRECATION")
    override fun getOpacity()=PixelFormat.TRANSLUCENT
}
