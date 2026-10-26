// MG5 Metro Dark Preview — GPL-3.0-only, inherits Dashline source license.
package com.dashline.launcher

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.widget.ImageView
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
import android.view.MotionEvent
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
        Tile("maps", "خرائط", 2, 1, "#125577", "maps", "➤"),
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
        Tile("factory", "الأصلية", 1, 1, "#28535A", "factory", "⌂"),
        Tile("fav_thmanyah", "ثمانية", 1, 1, "#3B405E", "fav:fav_thmanyah", "▶"),
        Tile("fav_video", "بديل يوتيوب", 1, 1, "#3F365C", "fav:fav_video", "▶"),
        Tile("fav_custom", "إضافة تطبيق", 1, 1, "#244D52", "fav:fav_custom", "+")
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
    private var gestureX = 0f
    private var gestureY = 0f
    private var gestureTriggered = false
    private var weatherTemp: Double? = null
    private var weatherText = "الرياض"
    private var weatherCode = -1
    private var gpsSpeed: Float? = null
    private val favoriteIds = setOf("fav_thmanyah", "fav_video", "fav_custom")
    private val editableAppIds = favoriteIds + setOf(
        "maps", "music", "radio", "ac", "youtube", "factory"
    )
    private var cachedLaunchables: List<AppInfo> = emptyList()
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
    /** Open apps with a deliberate upward gesture, not during tile edit/drag. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureX = ev.x
                gestureY = ev.y
                gestureTriggered = false
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (gestureTriggered) return true
                val up = gestureY - ev.y
                val across = kotlin.math.abs(ev.x - gestureX)
                if (!editing && up > px(72f) && up > across * 1.4f) {
                    gestureTriggered = true
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    startActivity(Intent(this, Mg5AppsActivity::class.java))
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> gestureTriggered = false
        }
        return super.dispatchTouchEvent(ev)
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
        if (::grid.isInitialized) grid.post { drawGrid() }
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
        top.addView(headerAction, LinearLayout.LayoutParams(px(290f),px(60f)))
        root.addView(top, LinearLayout.LayoutParams(-1, px(66f)))
        grid = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            clipChildren = true
            clipToPadding = false
        }
        root.addView(grid, LinearLayout.LayoutParams(-1,0,1f))
        // Avoid a redundant footer under the last row. Full tile height is
        // more useful than decorative text on a 720p car display.
    }
    private fun saveOrder() { prefs.edit().putString("order", order.joinToString(",")).apply() }
    private fun drawGrid() {
        if (!::grid.isInitialized || grid.width == 0 || grid.height == 0) return
        grid.removeAllViews()
        cachedLaunchables = AppRepository.loadLaunchableApps(this)
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
            background = if(def.id=="clock" || def.id=="date") bg(def.fill)
                else {
                    val assigned = if(def.id in editableAppIds) chosenApp(def.id) else null
                    MetroFullTileDrawable(
                        if(assigned != null) "custom" else def.id,
                        color(def.fill), assigned?.icon
                    )
                }
            isClickable = true
            isFocusable = true
            contentDescription = def.label
        }
        val primaryInfo = def.id == "clock" || def.id == "date"
        if (primaryInfo) {
            // Treat clock and date as information, not app shortcuts:
            // no obvious title/icon, centered with all the tile space available.
            box.gravity = Gravity.CENTER
            box.setPadding(px(10f), px(10f), px(10f), px(10f))
            val content = txt("—", if (def.id == "clock") 46f else 20f,
                def.id == "clock").apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                textDirection = if (def.id == "clock") View.TEXT_DIRECTION_LTR
                    else View.TEXT_DIRECTION_FIRST_STRONG
                maxLines = if (def.id == "clock") 1 else 3
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAutoSizeTextTypeUniformWithConfiguration(
                        if (def.id == "clock") 24 else 14,
                        if (def.id == "clock") 46 else 22,
                        1,
                        android.util.TypedValue.COMPLEX_UNIT_SP
                    )
                }
                setLineSpacing(px(5f).toFloat(), 1f)
            }
            box.addView(content, LinearLayout.LayoutParams(-1, -1))
            values[def.id] = content
        } else {
            // Entire tile carries its app identity; avoid small glyph centered
            // on an otherwise empty square. Titles sit on a bottom fade.
            box.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            box.setPadding(px(9f),px(6f),px(9f),px(10f))
            val favoriteApp = if(def.id in editableAppIds) chosenApp(def.id) else null
            val name = txt(favoriteApp?.label ?: def.label,
                if(w==1) 17f else 21f,true).apply {
                gravity=Gravity.CENTER
                textAlignment=View.TEXT_ALIGNMENT_CENTER
                maxLines=2
                if(Build.VERSION.SDK_INT>=26) {
                    setAutoSizeTextTypeUniformWithConfiguration(
                        12, if(w==1) 17 else 21, 1,
                        android.util.TypedValue.COMPLEX_UNIT_SP)
                }
            }
            if(def.id in setOf("weather","speed","wifi","music")) {
                val value=txt("—",when(def.id){
                    "speed"->25f; "weather"->21f;else->16f
                },def.id=="speed").apply{
                    gravity=Gravity.CENTER
                    textAlignment=View.TEXT_ALIGNMENT_CENTER
                    maxLines=3
                }
                box.addView(value,LinearLayout.LayoutParams(-1,0,1f))
                values[def.id]=value
            }else {
                box.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
            }
            box.addView(name,LinearLayout.LayoutParams(-1,
                px(if(h==2) 50f else 37f)))
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
            if(editing) sizeDialog(def) else launchTile(def)
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
        val canSwap = tile.id in editableAppIds
        val hasReplacement = if (tile.id in favoriteIds) {
            prefs.contains("favorite_" + tile.id)
        } else prefs.contains("shortcut_" + tile.id)
        val choices = mutableListOf("صغيرة 1×1", "مستطيلة 2×1", "مربعة كبيرة 2×2")
        if (canSwap) choices.add("تغيير التطبيق")
        if (canSwap && hasReplacement) choices.add("استعادة التطبيق الأصلي")
        choices.add("إنهاء التعديل")
        AlertDialog.Builder(this).setTitle("بلاطة: " + tile.label)
            .setItems(choices.toTypedArray()) { _, index ->
                val chosen = choices[index]
                when(chosen) {
                    "تغيير التطبيق" -> chooseTileApp(tile.id)
                    "استعادة التطبيق الأصلي" -> {
                        val key = if(tile.id in favoriteIds) "favorite_" else "shortcut_"
                        prefs.edit().remove(key + tile.id).apply()
                        drawGrid()
                    }
                    "إنهاء التعديل" -> { editing=false; drawGrid() }
                    else -> {
                        val (w,h) = when(index) {
                            0 -> 1 to 1
                            1 -> 2 to 1
                            else -> 2 to 2
                        }
                        val currentCount = order.sumOf { key ->
                            val (a,b) = dims(key)
                            a*b
                        }
                        val (oldW,oldH) = dims(tile.id)
                        if (currentCount - oldW*oldH + w*h > 24) {
                            Toast.makeText(this,
                                "المساحة لا تكفي. صغّر بلاطة أخرى أولًا",
                                Toast.LENGTH_SHORT).show()
                        } else {
                            prefs.edit().putString("size_"+tile.id,
                                "${w}x${h}").apply()
                            drawGrid()
                        }
                    }
                }
            }.show()
    }
    private fun updateLive() {
        if(!::grid.isInitialized) return
        val now = Date()
        val clockValue = SimpleDateFormat("hh:mm", Locale.US).format(now)
        val meridiem = if (Calendar.getInstance().get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
        values["clock"]?.text = "$clockValue $meridiem"
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
        values["music"]?.text = liveMusicText()
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
            "maps" -> chooseNavigationApp()
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
            else -> if (action.startsWith("fav:")) launchFavorite(action.substringAfter("fav:"))
        }
    }
    private fun chosenApp(id:String): AppInfo? {
        if (id in favoriteIds) return favoriteApp(id)
        val pkg = prefs.getString("shortcut_" + id, null) ?: return null
        val all = if(cachedLaunchables.isEmpty()) AppRepository.loadLaunchableApps(this)
            else cachedLaunchables
        return all.firstOrNull { it.packageName == pkg }
    }

    private fun launchTile(tile:Tile) {
        val pkg = if(tile.id in favoriteIds) null
            else prefs.getString("shortcut_" + tile.id, null)
        if(!pkg.isNullOrBlank()) {
            if(AppRepository.launch(this, pkg)) return
            Toast.makeText(this,"تعذر فتح التطبيق المحدد",Toast.LENGTH_SHORT).show()
        }
        launch(tile.action)
    }

    private fun chooseTileApp(id:String) {
        val installed = AppRepository.loadLaunchableApps(this)
        if(installed.isEmpty()) {
            Toast.makeText(this,"لا توجد تطبيقات متاحة",Toast.LENGTH_SHORT).show()
            return
        }
        val labels = installed.map { it.label }.toTypedArray()
        AlertDialog.Builder(this).setTitle("اختر التطبيق لهذه البلاطة")
            .setItems(labels) { _, index ->
                val key = if(id in favoriteIds) "favorite_" else "shortcut_"
                prefs.edit().putString(key+id,installed[index].packageName).apply()
                editing=false
                drawGrid()
            }.setNegativeButton("إلغاء",null).show()
    }

    /** Pin real installed applications, never guess app package names. */
    private fun favoriteApp(id:String): AppInfo? {
        val installed = if(cachedLaunchables.isEmpty())
            AppRepository.loadLaunchableApps(this) else cachedLaunchables
        val saved = prefs.getString("favorite_" + id, null)
        if (!saved.isNullOrBlank()) return installed.firstOrNull { it.packageName == saved }
        val needles = when(id) {
            "fav_thmanyah" -> listOf("ثمانية", "ثمانيه", "thmanyah", "thmany")
            "fav_video" -> listOf("smarttube", "newpipe", "new pipe", "libretube",
                "tubular", "pipepipe", "grayjay", "revanced", "vanced", "smart youtube")
            else -> emptyList()
        }
        return installed.firstOrNull { app ->
            val haystack = (app.label + " " + app.packageName).lowercase(Locale.ROOT)
            needles.any { haystack.contains(it) }
        }
    }

    private fun launchFavorite(id:String) {
        val item = favoriteApp(id)
        if (item != null && AppRepository.launch(this, item.packageName)) return
        chooseFavorite(id)
    }

    private fun chooseFavorite(id:String) {
        val installed = AppRepository.loadLaunchableApps(this)
        if (installed.isEmpty()) {
            Toast.makeText(this, "لا توجد تطبيقات متاحة", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = installed.map { it.label + "  •  " + it.packageName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("اختر تطبيقًا للبلاطة")
            .setItems(labels) { _, index ->
                prefs.edit().putString("favorite_" + id, installed[index].packageName).apply()
                editing = false
                drawGrid()
                Toast.makeText(this, "تم تثبيت " + installed[index].label + " في الرئيسية", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null).show()
    }

    /** Only known navigation app labels/packages are offered; chooser shows installed apps. */
    private fun chooseNavigationApp() {
        val installed = AppRepository.loadLaunchableApps(this)
        val keywords = listOf("google maps", "maps", "خرائط", "waze", "ويز", "here wego",
            "osmand", "sygic", "petal", "navitel", "igo", "2gis", "mapfactor",
            "magic earth", "mapy", "navigation", "navigat", "الملاحة", "carma")
        val options = installed.filter { item ->
            val key = (item.label + " " + item.packageName).lowercase(Locale.ROOT)
            keywords.any { key.contains(it) }
        }.distinctBy { it.packageName }
        if (options.isEmpty()) {
            app("com.google.android.apps.maps")
            return
        }
        AlertDialog.Builder(this).setTitle("تطبيقات الخرائط والملاحة")
            .setItems(options.map { it.label }.toTypedArray()) { _, index ->
                app(options[index].packageName)
            }.setNegativeButton("إلغاء", null).show()
    }

    /** Real media metadata when Android notification access has been granted. */
    private fun liveMusicText(): String {
        return try {
            val service = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val owner = ComponentName(this, MediaNotificationListener::class.java)
            val sessions = service.getActiveSessions(owner)
            val active = sessions.firstOrNull {
                it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull { it.metadata != null }
            val meta = active?.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            if (title.isNullOrBlank()) "لا توجد أغنية نشطة"
            else if (artist.isNullOrBlank()) title else "$title\\n$artist"
        } catch (_: SecurityException) {
            "اسم الأغنية يحتاج إذن الإشعارات"
        } catch (_: Exception) {
            "اضغط لفتح مشغل الموسيقى"
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
