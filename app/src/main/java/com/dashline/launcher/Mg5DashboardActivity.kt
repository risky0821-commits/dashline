// MG5 Dashboard custom cockpit; based on Dashline (GPL-3.0-only).
// Copyright (C) 2026 MG5 Dashboard contributors
// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.app.AlertDialog
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.abs
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Automotive-first 1280x720 landscape dashboard.
 * Layout direction is deliberately LTR to preserve physical control positions.
 * Individual labels remain Arabic and right-aligned.
 * All vehicle apps remain installed; this is a launcher, not a CANBUS replacement.
 */
class Mg5DashboardActivity : BaseActivity() {
    private val ink = Color.rgb(237, 246, 245)
    private val muted = Color.rgb(153, 176, 180)
    private val mint = Color.rgb(40, 235, 212)
    private val dark = Color.rgb(15, 27, 35)
    private lateinit var dashboard: LinearLayout
    private lateinit var clock: TextView
    private lateinit var gregorian: TextView
    private lateinit var hijri: TextView
    private lateinit var weatherLabel: TextView
    private lateinit var wifiIndicator: LinearLayout
    private var weatherLastFetched = 0L
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeFired = false
    private var athkarIndex = 0
    private val rideDhikr = "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ، وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ."
    private val exitDhikr = "بِسْمِ اللَّهِ، تَوَكَّلْتُ عَلَى اللَّهِ، وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ."
    private lateinit var athkarLabel: TextView
    private val athkar = listOf(
        "سبحان الله وبحمده",
        "لا حول ولا قوة إلا بالله",
        "أستغفر الله وأتوب إليه",
        "اللهم صل وسلم على نبينا محمد",
        "سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر"
    )
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateDates()
            updateWifiIndicator()
            handler.postDelayed(this, 8000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildDashboard()
        updateDates()
        updateWifiIndicator()
        fetchWeather()
    }

    override fun onResume() {
        super.onResume()
        if (::dashboard.isInitialized) dashboard.background = backdrop()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        updateWifiIndicator()
        if (System.currentTimeMillis() - weatherLastFetched > 30L * 60L * 1000L) fetchWeather()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    /**
     * Detect swipes during MOVE rather than only on UP: Topway touch stacks can
     * cancel end-of-gesture events when starting from the bottom edge.
     * Normal taps still reach their own buttons.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = ev.x
                swipeStartY = ev.y
                swipeFired = false
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (swipeFired) {
                    if (ev.actionMasked == MotionEvent.ACTION_UP) swipeFired = false
                    return true
                }
                val vertical = swipeStartY - ev.y
                val horizontal = abs(swipeStartX - ev.x)
                if (vertical > dp(68) && vertical > horizontal * 1.5f) {
                    swipeFired = true
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    openApps()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> swipeFired = false
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun openApps() {
        startActivity(Intent(this, Mg5AppsActivity::class.java))
    }

    /** Real Wi-Fi transport state, not merely whether the Wi-Fi switch is enabled. */
    private fun updateWifiIndicator() {
        if (!::wifiIndicator.isInitialized) return
        val connected = try {
            val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = manager.activeNetwork
                network != null &&
                    manager.getNetworkCapabilities(network)?.hasTransport(
                        NetworkCapabilities.TRANSPORT_WIFI
                    ) == true
            } else {
                @Suppress("DEPRECATION")
                val info = manager.activeNetworkInfo
                @Suppress("DEPRECATION")
                info?.isConnected == true && info.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (_: Exception) {
            false
        }
        wifiIndicator.visibility = if (connected) View.VISIBLE else View.GONE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun shade(hex: String) = Color.parseColor(hex)
    private fun backdrop() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(shade("#061426"), shade("#092847"), shade("#07152C"))
    )
    private fun shape(a: Int, b: Int = a, radius: Int = 24, border: Int = 0): GradientDrawable {
        val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b))
        d.cornerRadius = dp(radius).toFloat()
        if (border != 0) d.setStroke(dp(1), border)
        return d
    }

    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false,
                     gravity: Int = Gravity.RIGHT): TextView {
        val t = TextView(this)
        t.text = value
        t.textSize = size
        t.setTextColor(color)
        t.gravity = gravity or Gravity.CENTER_VERTICAL
        t.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        t.includeFontPadding = false
        t.typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        return t
    }

    private fun gap(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun card(start: String = "#162631", end: String = "#15222E"): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(20), dp(18), dp(20), dp(16))
            background = shape(shade(start), shade(end), 22, shade("#29414A"))
        }

    private fun buildDashboard() {
        dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        setContentView(dashboard)
        dashboard.background = backdrop()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("MG5  /  DASHBOARD", 23f, ink, true, Gravity.LEFT))
        brand.addView(gap(3))
        brand.addView(text("قيادتك، على طريقتك", 14f, muted, false, Gravity.LEFT))
        top.addView(brand, LinearLayout.LayoutParams(0, dp(54), 1f))
        wifiIndicator = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = shape(shade("#13466A"), shade("#0A2C4D"), 17, shade("#20D9E2"))
            contentDescription = "Wi-Fi متصل"
            visibility = View.GONE
        }
        wifiIndicator.addView(ImageView(this).apply {
            setImageDrawable(Mg5WifiDrawable())
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(45), dp(45)))
        wifiIndicator.addView(text("Wi-Fi", 18f, mint, true, Gravity.CENTER))
        top.addView(wifiIndicator, LinearLayout.LayoutParams(dp(142), dp(56)))
        top.addView(View(this), LinearLayout.LayoutParams(dp(9), 1))
        val classic = button("الكلاسيكية", "↗", shade("#1D3440"), shade("#223744")) {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        top.addView(classic, LinearLayout.LayoutParams(dp(158), dp(50)))
        dashboard.addView(top, LinearLayout.LayoutParams(-1, dp(60)))
        dashboard.addView(gap(12))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        body.addView(left, LinearLayout.LayoutParams(0, -1, 1.45f))
        body.addView(View(this), LinearLayout.LayoutParams(dp(14), 1))
        body.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        dashboard.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        dashboard.addView(gap(10))

        val ride = card("#113E68", "#06192E")
        ride.background = Mg5SceneDrawable(Mg5SceneDrawable.Mode.RIDE)
        ride.addView(text("أذكار ركوب السيارة", 29f, ink, true))
        ride.addView(gap(18))
        ride.addView(text("سبحان الذي سخر لنا هذا وما كنا له مقرنين\nوإنا إلى ربنا لمنقلبون", 19f, ink, true))
        ride.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        ride.addView(button("عرض الذكر", "▤", shade("#19C5ED"), shade("#0576CB")) {
            showDhikr("ذكر ركوب السيارة", rideDhikr)
        }, LinearLayout.LayoutParams(-1, dp(68)))
        ride.setOnClickListener { showDhikr("ذكر ركوب السيارة", rideDhikr) }
        left.addView(ride, LinearLayout.LayoutParams(-1, 0, 1.45f))
        left.addView(gap(12))

        val media = card("#1B2C3A", "#172633")
        val title = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        title.addView(text("الموسيقى  •  مشغل الصوت", 20f, ink, true),
            LinearLayout.LayoutParams(0, dp(33), 1f))
        val openMedia = button("فتح", "↗", shade("#304652"), shade("#304652")) {
            openMusic()
        }
        title.addView(openMedia, LinearLayout.LayoutParams(dp(98), dp(42)))
        media.addView(title)
        media.addView(gap(8))
        media.addView(text("استمع إلى أغانيك  •  تحكم سريع بالمقاطع", 14f, muted))
        media.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val prev = button("السابق", "◀◀", shade("#273F4A")) { sendMedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        val pause = button("تشغيل / إيقاف", "▶ ❚❚", shade("#2D655A"), shade("#205548")) {
            sendMedia(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        val next = button("التالي", "▶▶", shade("#273F4A")) { sendMedia(KeyEvent.KEYCODE_MEDIA_NEXT) }
        controls.addView(prev, LinearLayout.LayoutParams(0, dp(67), 1f))
        controls.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        controls.addView(pause, LinearLayout.LayoutParams(0, dp(67), 1.3f))
        controls.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        controls.addView(next, LinearLayout.LayoutParams(0, dp(67), 1f))
        media.addView(controls)
        left.addView(media, LinearLayout.LayoutParams(-1, 0, 1f))

        val dateCard = card("#132D54", "#0B1F3E")
        dateCard.addView(text("الساعة والتاريخ  •  الرياض", 16f, mint, true))
        dateCard.addView(gap(8))
        val clockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
        }
        val weatherColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        weatherLabel = text("☀  --°", 23f, ink, true, Gravity.CENTER)
        weatherColumn.addView(weatherLabel)
        weatherColumn.addView(text("الرياض", 15f, muted, true, Gravity.CENTER))
        clockRow.addView(weatherColumn, LinearLayout.LayoutParams(0, dp(85), 0.45f))
        clockRow.addView(View(this).apply { setBackgroundColor(shade("#237AB0")) },
            LinearLayout.LayoutParams(dp(1), dp(75)))
        clock = text("--:--", 63f, ink, true)
        clock.textDirection = View.TEXT_DIRECTION_LTR
        clockRow.addView(clock, LinearLayout.LayoutParams(0, dp(85), 1f))
        dateCard.addView(clockRow)
        gregorian = text("", 16f, ink)
        hijri = text("", 16f, muted)
        dateCard.addView(gregorian)
        dateCard.addView(hijri)
        right.addView(dateCard, LinearLayout.LayoutParams(-1, 0, 1.09f))
        right.addView(gap(10))

        val exitCard = card("#233C51", "#0C2444")
        exitCard.background = Mg5SceneDrawable(Mg5SceneDrawable.Mode.HOME)
        exitCard.setPadding(dp(14), dp(10), dp(14), dp(10))
        exitCard.addView(text("دعاء الخروج من المنزل", 20f, ink, true))
        exitCard.addView(gap(8))
        exitCard.addView(text("بسم الله، توكلت على الله،\nولا حول ولا قوة إلا بالله", 16f, ink, true))
        exitCard.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        exitCard.addView(button("عرض الدعاء", "▤", shade("#08ABD8"), shade("#086BAF")) {
            showDhikr("دعاء الخروج من المنزل", exitDhikr)
        }, LinearLayout.LayoutParams(-1, dp(51)))
        exitCard.setOnClickListener { showDhikr("دعاء الخروج من المنزل", exitDhikr) }
        right.addView(exitCard, LinearLayout.LayoutParams(-1, 0, 1.02f))
        right.addView(gap(10))

        val climate = card("#153B55", "#102A43")
        climate.background = Mg5SceneDrawable(Mg5SceneDrawable.Mode.CLIMATE)
        climate.setPadding(dp(14), dp(9), dp(14), dp(9))
        climate.addView(text("❄  المكيف", 20f, mint, true))
        climate.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        climate.addView(button("افتح المكيف", "❄", shade("#118FC9"), shade("#09629B")) {
            openClimateApp()
        }, LinearLayout.LayoutParams(-1, dp(54)))
        climate.setOnClickListener { openClimateApp() }
        right.addView(climate, LinearLayout.LayoutParams(-1, 0, 0.72f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val shortcuts = listOf(
            Triple("التطبيقات", "▦", "apps"),
            Triple("رجوع", "↶", "back"),
            Triple("يوتيوب", "▶", "youtube"),
            Triple("خرائط", "➤", "maps"),
            Triple("المكيف", "❄", "climate"),
            Triple("الموسيقى", "♫", "music"),
            Triple("الراديو", "◉", "radio"),
            Triple("الأصلية", "⌂", "dofun")
        )
        shortcuts.forEachIndexed { index, (label, symbol, action) ->
            val tile = button(label, symbol, shade("#1B2C36"), shade("#15242D"))
            tile.setOnClickListener {
                when (action) {
                    "apps" -> openApps()
                    "back" -> {
                        if (isTaskRoot) moveTaskToBack(true)
                        else onBackPressedDispatcher.onBackPressed()
                    }
                    "radio" -> openCarApp("com.tw.radio", "com.tw.radio.RadioActivity")
                    "climate" -> openClimateApp()
                    "youtube" -> openPackage("com.google.android.youtube")
                    "maps" -> openPackage("com.google.android.apps.maps")
                    "music" -> openMusic()
                    "dofun" -> openCarApp("com.dofun.variety", "com.dofun.overseasvariety.Launcher")
                }
            }
            if (index > 0) footer.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            footer.addView(tile, LinearLayout.LayoutParams(0, dp(87), 1f))
        }
        dashboard.addView(footer, LinearLayout.LayoutParams(-1, dp(87)))
    }

    private fun showDhikr(title: String, words: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(words).setPositiveButton("إغلاق", null).show()
    }

    private fun button(label: String, mark: String, start: Int, end: Int = start,
                       foreground: Int = ink, click: (() -> Unit)? = null): LinearLayout {
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = shape(start, end, 15)
            isClickable = true
            isFocusable = true
            contentDescription = label
        }
        val glyph = when (mark) {
            "▦" -> "apps"
            "↶" -> "back"
            "❄" -> "climate"
            "▶" -> "youtube"
            "➤" -> "maps"
            "♫" -> "music"
            "◉" -> "radio"
            "⌂" -> "home"
            else -> ""
        }
        if (glyph.isNotEmpty()) {
            val icon = ImageView(this).apply {
                setImageDrawable(Mg5GlyphDrawable(glyph))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            button.addView(icon, LinearLayout.LayoutParams(dp(47), 0, 1f))
        } else {
            val icon = text(mark, 27f, foreground, true, Gravity.CENTER)
            button.addView(icon, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        button.addView(text(label, 17f, foreground, true, Gravity.CENTER),
            LinearLayout.LayoutParams(-1, 0, 1f))
        if (click != null) button.setOnClickListener { click() }
        return button
    }


    /** Opens an installed, dedicated music app; prefers Lark Player already on TS10S.
     * No APK bundling or dependency on VLC. Media keys control the active player.
     */
    private fun openMusic() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = packageManager
        val installed = pm.queryIntentActivities(intent, 0)
        val preferred = listOf("musicolet", "lark player", "ymusic", "music player", "الموسيقى")
        val found = preferred.firstNotNullOfOrNull { needle ->
            installed.firstOrNull { app ->
                app.loadLabel(pm).toString().lowercase(Locale.ROOT).contains(needle)
            }?.activityInfo?.packageName
        }
        if (found == null || !AppRepository.launch(this, found)) {
            Toast.makeText(this, "ثبّت مشغل موسيقى مثل Musicolet أو افتح Lark Player", Toast.LENGTH_LONG).show()
        }
    }

    /** Open the factory AirCondition app by its Android launcher label. */
    private fun openClimateApp() {
        val pm = packageManager
        val candidates = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val target = candidates.firstOrNull {
            val name = it.loadLabel(pm).toString().lowercase(Locale.ROOT)
            name == "aircondition" || name.contains("air conditioning") ||
                name.contains("aircondition") || name.contains("المكيف") ||
                name.contains("تكييف")
        }
        if (target != null) {
            try {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(target.activityInfo.packageName, target.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (_: Exception) {}
        }
        Toast.makeText(this, "تطبيق المكيف غير متاح من هذا الاختصار", Toast.LENGTH_LONG).show()
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "صحو"
        1, 2 -> "غائم جزئيًا"
        3 -> "غائم"
        45, 48 -> "ضباب"
        in 51..67, in 80..82 -> "مطر"
        in 71..77, in 85..86 -> "ثلوج"
        in 95..99 -> "عواصف"
        else -> "طقس متغير"
    }

    /** Fetch measured current Riyadh weather from Open-Meteo, without location permission. */
    private fun fetchWeather() {
        if (!::weatherLabel.isInitialized) return
        weatherLastFetched = System.currentTimeMillis()
        thread(name = "MG5-current-weather") {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,weather_code&timezone=Asia%2FRiyadh")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val response = try { conn.inputStream.bufferedReader().use { it.readText() } }
                    finally { conn.disconnect() }
                val current = JSONObject(response).getJSONObject("current")
                val temperature = current.getDouble("temperature_2m")
                val code = current.optInt("weather_code", -1)
                runOnUiThread {
                    if (!isFinishing) {
                        weatherLabel.text = String.format(
                            Locale.US, "☀  %.0f°", temperature
                        )
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isFinishing) weatherLabel.text = "☁  --°"
                }
            }
        }
    }

    private fun openPackage(pkg: String) {
        if (!AppRepository.launch(this, pkg)) {
            Toast.makeText(this, "تعذر فتح التطبيق", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCarApp(pkg: String, className: String) {
        if (AppRepository.launch(this, pkg)) return
        try {
            startActivity(Intent().setClassName(pkg, className).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح التطبيق. تحقق من إعدادات الشاشة.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMedia(key: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    private fun latin(value: String): String = buildString {
        value.forEach { ch ->
            append(when (ch) {
                in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
                in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
                else -> ch
            })
        }
    }

    private fun updateDates() {
        if (!::clock.isInitialized) return
        val now = java.util.Date()
        clock.text = SimpleDateFormat("HH:mm", Locale.US).format(now)
        gregorian.text = latin(SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar", "SA")).format(now))
        try {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                val cal = android.icu.util.IslamicCalendar(
                    android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura")
                )
                cal.timeInMillis = now.time
                val monthNames = arrayOf(
                    "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
                    "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
                )
                val d = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
                val m = cal.get(android.icu.util.Calendar.MONTH)
                val y = cal.get(android.icu.util.Calendar.YEAR)
                hijri.text = String.format(Locale.US, "%d %s %d هـ", d, monthNames[m], y)
            }
        } catch (_: Exception) {
            hijri.text = ""
        }
    }

}
