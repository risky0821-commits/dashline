// MG5 Metro Dark Preview — GPL-3.0-only, inherits Dashline source license.
package com.dashline.launcher

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.HorizontalScrollView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Safe standalone-preview launcher, no CANBUS / SystemUI hooks.
 * 8-column Metro grid with persisted order and adjustable tile spans.
 */
class Mg5MetroActivity : BaseActivity() {
    private data class Tile(val id: String, val label: String, val w: Int, val h: Int,
                            val fill: String, val action: String, val icon: String)
    private val defaults = listOf(
        Tile("maps", "خرائط", 2, 2, "#125577", "maps", "➤"),
        Tile("clock", "الساعة", 2, 1, "#163A64", "", "◷"),
        Tile("date", "التاريخ", 2, 1, "#273E65", "", "▦"),
        Tile("weather", "الطقس", 2, 1, "#19576A", "", "☀"),
        Tile("music", "الموسيقى", 2, 1, "#42345C", "music", "♫"),
        Tile("speed", "سرعة GPS", 2, 1, "#194959", "gps", "◉"),
        Tile("ac", "المكيف", 1, 1, "#245075", "ac", "❄"),
        Tile("wifi", "Wi-Fi", 1, 1, "#0B635E", "wifi", "◒"),
        Tile("apps", "التطبيقات", 2, 1, "#20527C", "apps", "▦"),
        Tile("radio", "الراديو", 2, 1, "#5D3A47", "radio", "◉"),
        Tile("settings", "الضبط", 1, 1, "#405464", "settings", "⚙"),
        Tile("youtube", "يوتيوب", 1, 1, "#712E49", "youtube", "▶"),
        Tile("factory", "الأصلية", 2, 1, "#28535A", "factory", "⌂")
    )
    private val byId get() = defaults.associateBy { it.id }
    private val prefs by lazy { getSharedPreferences("mg5_metro_layout_1", MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var grid: FrameLayout
    private lateinit var headerAction: TextView
    private val values = mutableMapOf<String, TextView>()
    private val order = mutableListOf<String>()
    private var editing = false
    private var weatherTemp: Double? = null
    private var weatherText = "الرياض"
    private var weatherCode = -1
    private var gpsSpeed: Float? = null
    private var gpsListener: LocationListener? = null
    private var lastWeatherFetch = 0L
    private val ticker = object : Runnable {
        override fun run() {
            updateLive()
            mainHandler.postDelayed(this, 10000L)
        }
    }
    private fun px(d: Float) = (d * resources.displayMetrics.density + 0.5f).toInt()
    private fun color(hex: String) = Color.parseColor(hex)
    private fun bg(hex: String) = GradientDrawable().apply {
        setColor(color(hex)); cornerRadius = px(7f).toFloat()
    }
    private fun txt(s: String, size: Float, bold: Boolean = false, fg: Int = Color.WHITE) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(fg)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            includeFontPadding = false
            maxLines = 4
            typeface = android.graphics.Typeface.create(
                if (bold) "sans-serif-medium" else "sans-serif",
                android.graphics.Typeface.NORMAL
            )
        }
    private fun dims(id: String): Pair<Int,Int> {
        val def = byId[id] ?: return 1 to 1
        val stored = prefs.getString("size_" + id, null)?.split("x")
        val w = stored?.getOrNull(0)?.toIntOrNull()?.coerceIn(1,2) ?: def.w
        val h = stored?.getOrNull(1)?.toIntOrNull()?.coerceIn(1,2) ?: def.h
        return w to h
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val saved = prefs.getString("order", "")!!.split(",").filter { it in byId }
        order.addAll(saved.distinct())
        defaults.forEach { if (it.id !in order) order.add(it.id) }
        buildUi()
        grid.post { drawGrid() }
    }
    override fun onResume() {
        super.onResume()
        mainHandler.removeCallbacks(ticker)
        mainHandler.post(ticker)
        requestWeather()
    }
    override fun onPause() {
        mainHandler.removeCallbacks(ticker)
        stopGps()
        super.onPause()
    }
    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(px(19f),px(13f),px(19f),px(12f))
            background = MetroSkyDrawable(weatherCode)
        }
        setContentView(root)
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = txt("MG5  /  METRO DARK", 26f, true).apply { gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL }
        top.addView(brand, LinearLayout.LayoutParams(0, px(65f), 1f))
        headerAction = txt("تخصيص البلاطات: اضغط مطولًا", 16f, false, color("#B6C9DC")).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            setPadding(px(6f),0,px(7f),0)
            setOnClickListener { if (editing) { editing = false; drawGrid() } }
        }
        top.addView(headerAction, LinearLayout.LayoutParams(px(315f),px(60f)))
        root.addView(top, LinearLayout.LayoutParams(-1, px(66f)))
        grid = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            clipChildren = true
            clipToPadding = false
        }
        root.addView(grid, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(txt("METRO  •  MG5    |    TS10S", 13f, false, color("#728CA9")).apply {
            gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1,px(20f)))
    }
    private fun saveOrder() { prefs.edit().putString("order", order.joinToString(",")).apply() }
    private fun drawGrid() {
        if (!::grid.isInitialized || grid.width == 0 || grid.height == 0) return
        grid.removeAllViews()
        values.clear()
        headerAction.text = if(editing) "✓  إنهاء التعديل" else "اضغط مطولًا على بلاطة للتخصيص"
        val gap = px(9f)
        val cellWidth = (grid.width-gap*7)/8f
        val cellHeight = (grid.height-gap*2)/3f
        val used = Array(3) { BooleanArray(8) }
        for(id in order) {
            val def = byId[id] ?: continue
            val (w,h) = dims(id)
            var found: Pair<Int,Int>? = null
            search@for(row in 0..(3-h)) for(col in 0..(8-w)) {
                var available = true
                for(y in row until row+h) for(x in col until col+w) {
                    if(used[y][x]) available = false
                }
                if(available) { found = col to row; break@search }
            }
            if(found == null) continue
            val (col,row) = found
            for(y in row until row+h) for(x in col until col+w) used[y][x] = true
            val tile = tileView(def,w,h)
            val x = ((cellWidth+gap)*col).roundToInt()
            val y = ((cellHeight+gap)*row).roundToInt()
            val width = (cellWidth*w + gap*(w-1)).roundToInt()
            val height = (cellHeight*h + gap*(h-1)).roundToInt()
            val params = FrameLayout.LayoutParams(width,height).apply {
                leftMargin=x; topMargin=y
            }
            grid.addView(tile,params)
        }
        updateLive()
    }
    private fun tileView(def: Tile,w:Int,h:Int):View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            setPadding(px(if(w==1) 10f else 19f), px(11f), px(if(w==1) 10f else 19f), px(12f))
            background = bg(def.fill)
            isClickable = true
            isFocusable = true
            contentDescription = def.label
        }
        val first = txt(def.icon,if(w==1) 31f else if(h==2) 76f else 32f,true,color("#C9F9F4")).apply {
            gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        }
        box.addView(first,LinearLayout.LayoutParams(-1,0,if(h==2) 1.5f else 0.6f))
        val name = txt(def.label, if(w==1) 17f else 21f, true)
        box.addView(name, LinearLayout.LayoutParams(-1,px(if(h==2) 45f else 34f)))
        if (def.id in setOf("clock","date","weather","speed","wifi","music")) {
            val content = txt("—",when(def.id) {
                "clock" -> 37f; "date" -> 16f; "weather" -> 20f; "speed" -> 26f
                else -> 15f
            },def.id in setOf("clock","speed")).apply {
                gravity = Gravity.RIGHT or Gravity.TOP
                maxLines = if(def.id=="date") 3 else 2
            }
            box.addView(content, LinearLayout.LayoutParams(-1,0,1f))
            values[def.id] = content
        } else {
            val hint = if(def.id=="maps") "افتح Google Maps" else if(def.id=="ac") "" else ""
            if(hint.isNotEmpty()) box.addView(txt(hint,15f,false,color("#D3E9F3")))
            box.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        }
        box.setOnLongClickListener {
            if (!editing) {
                editing=true
                Toast.makeText(this,"وضع التعديل: اسحب البلاطة لترتيبها، أو اضغطها لتغيير الحجم",Toast.LENGTH_LONG).show()
                drawGrid()
                true
            } else {
                val shadow = View.DragShadowBuilder(box)
                if (Build.VERSION.SDK_INT >= 24) box.startDragAndDrop(null,shadow,def.id,0)
                else @Suppress("DEPRECATION") box.startDrag(null,shadow,def.id,0)
                true
            }
        }
        box.setOnClickListener {
            if(editing) sizeDialog(def) else launch(def.action)
        }
        box.setOnDragListener { _,event ->
            when(event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DROP -> {
                    val source = event.localState as? String
                    if(source!=null && source!=def.id) {
                        order.remove(source)
                        order.add(order.indexOf(def.id),source)
                        saveOrder()
                        grid.post { drawGrid() }
                    }
                    true
                }
                else -> true
            }
        }
        return box
    }
    private fun sizeDialog(tile:Tile) {
        val choices = arrayOf("صغيرة 1×1","مستطيلة 2×1","مربعة كبيرة 2×2","إنهاء التعديل")
        AlertDialog.Builder(this).setTitle("حجم بلاطة: "+tile.label)
            .setItems(choices) { _, index ->
                if(index==3) { editing=false; drawGrid(); return@setItems }
                val (w,h) = when(index) { 0 -> 1 to 1;1 -> 2 to 1;else -> 2 to 2 }
                val currentCount = order.sumOf { key ->
                    val (a,b) = dims(key)
                    a*b
                }
                val (oldW,oldH) = dims(tile.id)
                if (currentCount - oldW*oldH + w*h > 24) {
                    Toast.makeText(this,"المساحة لا تكفي. صغّر بلاطة أخرى أولًا",Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString("size_"+tile.id,"${w}x${h}").apply()
                    drawGrid()
                }
            }.show()
    }
    private fun updateLive() {
        if(!::grid.isInitialized) return
        val now = Date()
        values["clock"]?.text = SimpleDateFormat("HH:mm",Locale.US).format(now)
        val day=SimpleDateFormat("EEEE، d MMMM yyyy",Locale("ar","SA")).format(now)
        val hijri=try {
            val cal = android.icu.util.IslamicCalendar(android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura"))
            cal.timeInMillis=now.time
            val months=arrayOf("محرم","صفر","ربيع الأول","ربيع الآخر","جمادى الأولى","جمادى الآخرة","رجب","شعبان","رمضان","شوال","ذو القعدة","ذو الحجة")
            val dd=cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
            val mm=cal.get(android.icu.util.Calendar.MONTH)
            val yy=cal.get(android.icu.util.Calendar.YEAR)
            "$dd ${months[mm]} $yy هـ"
        } catch (_:Exception){""}
        values["date"]?.text = toLatin(day)+"\n"+toLatin(hijri)
        values["weather"]?.text = if(weatherTemp==null) "الرياض  •  —°" else
            String.format(Locale.US,"%.0f°  •  %s",weatherTemp,weatherText)
        values["speed"]?.text = if(gpsSpeed==null) "— km/h\nاضغط لتفعيل GPS" else
            String.format(Locale.US,"%.0f km/h  GPS",gpsSpeed)
        values["wifi"]?.text = if(wifiConnected()) "متصل" else "غير متصل"
        values["music"]?.text = "اضغط لفتح المشغل"
        if(::root.isInitialized) root.background=MetroSkyDrawable(weatherCode)
    }
    private fun toLatin(s:String) = buildString {
        s.forEach { c -> append(when(c) {
            in '٠'..'٩' -> ('0'.code+c.code-'٠'.code).toChar()
            in '۰'..'۹' -> ('0'.code+c.code-'۰'.code).toChar()
            else -> c
        }) }
    }
    private fun wifiConnected():Boolean = try {
        val m=getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network=m.activeNetwork
        network!=null && m.getNetworkCapabilities(network)?.hasTransport(
            NetworkCapabilities.TRANSPORT_WIFI)==true
    } catch(_:Exception){false}
    private fun requestWeather() {
        if(System.currentTimeMillis()-lastWeatherFetch<20*60*1000) return
        lastWeatherFetch=System.currentTimeMillis()
        thread(name="metro-weather") {
            try {
                val conn=URL("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,weather_code&timezone=Asia%2FRiyadh")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout=6000;conn.readTimeout=6000
                val data=try {conn.inputStream.bufferedReader().use{it.readText()}} finally{conn.disconnect()}
                val current=JSONObject(data).getJSONObject("current")
                val t=current.getDouble("temperature_2m")
                val code=current.getInt("weather_code")
                runOnUiThread {
                    weatherTemp=t
                    weatherCode=code
                    weatherText=when(code) {
                        0 -> "صحو"
                        1,2 -> "غائم جزئيًا"
                        3 -> "غائم"
                        45,48 -> "ضباب"
                        in 51..67, in 80..82 -> "مطر"
                        in 95..99 -> "عواصف"
                        else -> "طقس متغير"
                    }
                    updateLive()
                }
            } catch(_:Exception){}
        }
    }
    private fun launch(action:String) {
        when(action){
            "maps" -> app("com.google.android.apps.maps")
            "apps" -> startActivity(Intent(this,Mg5AppsActivity::class.java))
            "youtube" -> app("com.google.android.youtube")
            "radio" -> app("com.tw.radio")
            "ac" -> app("com.tw.ac")
            "factory" -> app("com.dofun.variety")
            "wifi" -> startActivity(Intent(if(Build.VERSION.SDK_INT>=29)
                Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS))
            "settings" -> startActivity(Intent(Settings.ACTION_SETTINGS))
            "music" -> openMusic()
            "gps" -> startGps()
        }
    }
    private fun app(pkg:String) {
        if(!AppRepository.launch(this,pkg)) {
            Toast.makeText(this,"تعذر فتح التطبيق: $pkg",Toast.LENGTH_SHORT).show()
        }
    }
    private fun openMusic() {
        val pm=packageManager
        val apps=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
        val a=apps.firstOrNull { val v=it.loadLabel(pm).toString().lowercase(Locale.ROOT)
            v.contains("lark")||v.contains("musicolet")||v.contains("music player")||v.contains("الموسيقى") }
        if(a==null || !AppRepository.launch(this,a.activityInfo.packageName)) {
            Toast.makeText(this,"افتح مشغل الموسيقى من التطبيقات",Toast.LENGTH_SHORT).show()
        }
    }
    private fun startGps() {
        if(Build.VERSION.SDK_INT>=23 &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),841)
            return
        }
        try {
            val manager=getSystemService(LOCATION_SERVICE) as LocationManager
            val listener=object:LocationListener {
                override fun onLocationChanged(location:Location){
                    gpsSpeed=if(location.hasSpeed()) (location.speed*3.6f).coerceAtLeast(0f) else null
                    updateLive()
                }
                @Suppress("DEPRECATION")
                override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?){}
                override fun onProviderEnabled(provider:String){}
                override fun onProviderDisabled(provider:String){gpsSpeed=null;updateLive()}
            }
            stopGps()
            gpsListener=listener
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1200L,0f,listener)
            Toast.makeText(this,"جارٍ قراءة سرعة GPS",Toast.LENGTH_SHORT).show()
        } catch(_:Exception){Toast.makeText(this,"تحقق من تشغيل GPS",Toast.LENGTH_SHORT).show()}
    }
    private fun stopGps() {
        val l=gpsListener ?:return
        try {(getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(l)}catch(_:Exception){}
        gpsListener=null
    }
    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, results:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,results)
        if(requestCode==841 && results.firstOrNull()==PackageManager.PERMISSION_GRANTED) startGps()
    }
}

/** Offline time-aware Metro skyline artwork; no video, no frame animation. */
private class MetroSkyDrawable(private val weatherCode:Int):Drawable() {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c:Canvas) {
        val b=bounds
        val w=b.width().toFloat()
        val h=b.height().toFloat()
        if(w<=0||h<=0)return
        val hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val tone=when(hour) {
            in 5..7 -> 0xFF163651.toInt()
            in 8..16 -> 0xFF0C2E48.toInt()
            in 17..19 -> 0xFF241C3F.toInt()
            else -> 0xFF080F22.toInt()
        }
        p.color=tone;c.drawRect(b.left.toFloat(),b.top.toFloat(),b.right.toFloat(),b.bottom.toFloat(),p)
        val cx=b.left+w*0.83f;val cy=b.top+h*0.21f
        p.color=if(hour in 6..18) 0x3328AFC9 else 0x223C6DCA
        c.drawCircle(cx,cy,h*0.2f,p)
        p.color=if(hour in 6..18) 0x668AD5DD else 0x557CB5EE
        c.drawCircle(cx,cy,h*0.05f,p)
        p.color=0x261CCDD8
        for(i in 0..12){
            val x=b.left+w*(i+0.4f)/13f
            val height=h*(0.10f+(i*17%9)/100f)
            c.drawRect(x,b.bottom-height,x+w*0.046f,b.bottom.toFloat(),p)
        }
        if(weatherCode in 51..67 || weatherCode in 80..82 || weatherCode in 95..99) {
            p.color=0x335DD0FF
            p.strokeWidth=2f
            for(i in 0..23) {
                val x=b.left+w*(i+0.5f)/24f
                c.drawLine(x,b.top+h*0.12f,x-w*.014f,b.top+h*.21f,p)
            }
        }
    }
    override fun setAlpha(alpha:Int){p.alpha=alpha;invalidateSelf()}
    @Suppress("DEPRECATION")
    override fun setColorFilter(f:android.graphics.ColorFilter?){p.colorFilter=f;invalidateSelf()}
    @Suppress("DEPRECATION")
    override fun getOpacity()=PixelFormat.TRANSLUCENT
}
