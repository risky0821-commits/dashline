// MG5 Dashboard tablet-style app drawer.
// Source: Dashline (GPL-3.0-only). Copyright (C) 2026 MG5 Dashboard contributors.
package com.dashline.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/** Real installed apps, compact configurable 6–8 column landscape drawer. */
class Mg5AppsActivity : BaseActivity() {
    private val accent = Color.rgb(22, 226, 215)
    private val white = Color.rgb(239, 248, 255)
    private val muted = Color.rgb(169, 193, 212)
    private lateinit var root: LinearLayout
    private lateinit var search: EditText
    private lateinit var grid: RecyclerView
    private lateinit var adapter: TilesAdapter
    private lateinit var prefs: Prefs
    private val chips = mutableListOf<TextView>()
    private var selected = 0
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeConsumed = false
    private var drawerCols = 8
    private lateinit var columnsButton: TextView
    private val drawerPrefs by lazy { getSharedPreferences("mg5_metro_drawer", MODE_PRIVATE) }
    private var apps: List<Tile> = emptyList()
    private val labels = listOf("الكل", "الملاحة", "الوسائط", "الأدوات")

    private data class Tile(
        val title: String,
        val pkg: String,
        val icon: android.graphics.drawable.Drawable?,
        val special: String = ""
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun col(hex: String) = Color.parseColor(hex)
    private fun panel(a: String, b: String, radius: Int = 18, outline: String = "#207DB1"): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(col(a), col(b))).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), col(outline))
        }
    }
    private fun text(value: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(white)
        gravity = Gravity.CENTER
        includeFontPadding = false
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }
    private fun space(width: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }
    private fun background() = panel("#061324", "#062C50", 0, "#061324")

    /** Pull down from the top of the drawer to return to Metro home.
     *  Ordinary app-grid scrolling is preserved when the list is scrolled.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = ev.x
                swipeStartY = ev.y
                swipeConsumed = false
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (swipeConsumed) return true
                val downward = ev.y - swipeStartY
                val sideways = kotlin.math.abs(ev.x - swipeStartX)
                if (::grid.isInitialized && !grid.canScrollVertically(-1) &&
                    downward > dp(74) && downward > sideways * 1.5f) {
                    swipeConsumed = true
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    finish()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> swipeConsumed = false
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        drawerCols = drawerPrefs.getInt("columns", 8).coerceIn(6, 8)
        build()
        refreshApps()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) root.background = background()
        refreshApps()
    }

    private fun build() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(20), dp(12), dp(20), dp(10))
            isFocusableInTouchMode = true
        }
        setContentView(root)
        root.background = background()
        root.requestFocus()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        columnsButton = text("$drawerCols في الصف", 15f, true).apply {
            contentDescription = "تغيير عدد التطبيقات في الصف"
            background = panel("#123F66","#0B2847",12,"#277EB0")
            isClickable = true
            isFocusable = true
            setOnClickListener {
                drawerCols = if (drawerCols == 8) 7 else if (drawerCols == 7) 6 else 8
                drawerPrefs.edit().putInt("columns", drawerCols).apply()
                text = "$drawerCols في الصف"
                grid.layoutManager = GridLayoutManager(this@Mg5AppsActivity, drawerCols)
                adapter = TilesAdapter()
                grid.adapter = adapter
                refreshFilter()
            }
        }
        header.addView(columnsButton, LinearLayout.LayoutParams(dp(112), dp(48)))
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(text("كل التطبيقات", 27f, true), LinearLayout.LayoutParams(dp(235), dp(57)))
        header.addView(text("▦", 30f, true).apply { setTextColor(accent) },
            LinearLayout.LayoutParams(dp(45), dp(57)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(66)))

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = panel("#0D3B64", "#082947", 20)
        }
        val chipsRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        labels.asReversed().forEach { name ->
            val index = labels.indexOf(name)
            val chip = text(name, 16f, true).apply {
                setPadding(dp(12), 0, dp(12), 0)
                isClickable = true
                setOnClickListener { selected = index; refreshFilter() }
            }
            chips.add(chip)
            chipsRow.addView(chip, LinearLayout.LayoutParams(dp(106), dp(47)))
            chipsRow.addView(space(5))
        }
        toolbar.addView(chipsRow, LinearLayout.LayoutParams(-2, dp(50)))
        toolbar.addView(space(15))
        search = EditText(this).apply {
            setSingleLine(true)
            hint = "البحث في التطبيقات"
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_RTL
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 17f
            setPadding(dp(16), 0, dp(20), 0)
            background = panel("#135081", "#0A395E", 16)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        toolbar.addView(search, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(65)))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshFilter()
            override fun afterTextChanged(s: Editable?) {}
        })
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(13)))
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@Mg5AppsActivity, drawerCols)
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(12))
        }
        adapter = TilesAdapter()
        grid.adapter = adapter
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))

        // Reachable, fixed back control at the bottom-left of the application drawer.
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        }
        val back = text("↶   رجوع", 22f, true).apply {
            contentDescription = "رجوع إلى واجهة MG5"
            background = panel("#164F77", "#103352", 12, "#29A6C9")
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@Mg5AppsActivity, Mg5MetroActivity::class.java))
                finish()
            }
        }
        bottom.addView(back, LinearLayout.LayoutParams(dp(194), dp(61)))
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(71)))
        refreshFilter()
    }

    private fun labelFor(title: String, pkg: String): String {
        val key = title.lowercase(Locale.ROOT)
        return when {
            pkg == "com.google.android.apps.maps" -> "خرائط"
            key.contains("lark player") || key.contains("musicolet") || key.contains("ymusic") -> "الموسيقى"
            pkg == "com.tw.radio" -> "الراديو"
            pkg == "com.tw.bt" -> "بلوتوث"
            else -> title
        }
    }

    private fun priority(tile: Tile): Int {
        val title = tile.title.lowercase(Locale.ROOT)
        val pkg = tile.pkg.lowercase(Locale.ROOT)
        return when {
            pkg.contains("maps") -> 0
            pkg == "com.tw.radio" -> 1
            pkg == "com.tw.bt" -> 2
            title == "الموسيقى" -> 3
            pkg.contains("youtube") -> 4
            pkg.contains("chrome") -> 5
            title.contains("الهاتف") || title.contains("phone") -> 6
            title.contains("file") || title.contains("ملف") -> 7
            pkg.contains("zlink") -> 8
            title.contains("setting") || title.contains("إعداد") -> 9
            else -> 20
        }
    }

    private fun refreshApps() {
        if (!::adapter.isInitialized) return
        val installed = AppRepository.loadLaunchableApps(this, prefs.hiddenApps)
        val mutable = installed.map {
            Tile(labelFor(it.label, it.packageName), it.packageName, it.icon)
        }.toMutableList()
        for ((pkg, name, activity) in listOf(
            Triple("com.tw.radio", "الراديو", "com.tw.radio.RadioActivity"),
            Triple("com.tw.bt", "بلوتوث", "com.tw.bt.ATBluetoothActivity")
        )) {
            if (pkg !in prefs.hiddenApps && mutable.none { it.pkg == pkg }) {
                try {
                    packageManager.getApplicationInfo(pkg, 0)
                    mutable.add(Tile(name, pkg, AppRepository.iconFor(this, pkg), activity))
                } catch (_: Exception) {}
            }
        }
        apps = mutable.sortedWith(compareBy<Tile> { priority(it) }.thenBy { it.title.lowercase(Locale.ROOT) })
        refreshFilter()
        grid.scrollToPosition(0)
    }

    private fun matchesCategory(tile: Tile): Boolean {
        if (selected == 0) return true
        val t = (tile.pkg + " " + tile.title).lowercase(Locale.ROOT)
        val navigation = listOf("map", "خرائط", "waze", "gps", "navigation", "carma")
        val media = listOf("music", "player", "lark", "ymusic", "musicolet", "youtube", "newpipe",
            "radio", "الراديو", "موسيقى", "vlc", "bluetooth", "بلوتوث", "com.tw.bt")
        return when (selected) {
            1 -> navigation.any { t.contains(it) }
            2 -> media.any { t.contains(it) }
            else -> navigation.none { t.contains(it) } && media.none { t.contains(it) }
        }
    }
    private fun refreshFilter() {
        if (!::adapter.isInitialized || !::search.isInitialized) return
        chips.forEachIndexed { position, chip ->
            val name = labels.asReversed()[position]
            chip.background = if (labels.indexOf(name) == selected)
                panel("#13D7CC", "#0A849D", 14, "#5FF2EE")
            else panel("#144970", "#0A3556", 14, "#24688B")
        }
        val q = search.text.toString().trim().lowercase(Locale.ROOT)
        val filtered = apps.filter { tile ->
            matchesCategory(tile) && (q.isEmpty() ||
                tile.title.lowercase(Locale.ROOT).contains(q) ||
                tile.pkg.lowercase(Locale.ROOT).contains(q))
        }
        adapter.submit(filtered)
    }

    private fun launch(tile: Tile) {
        if (AppRepository.launch(this, tile.pkg)) return
        if (tile.special.isNotBlank()) {
            try {
                startActivity(Intent().setClassName(tile.pkg, tile.special).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {}
        }
        Toast.makeText(this, "تعذر فتح ${tile.title}", Toast.LENGTH_SHORT).show()
    }

    private fun hide(tile: Tile) {
        AlertDialog.Builder(this)
            .setTitle(tile.title)
            .setMessage("إخفاء التطبيق من القائمة؟ يمكن استعادته من الإعدادات.")
            .setPositiveButton("إخفاء") { _, _ ->
                prefs.hideApp(tile.pkg)
                refreshApps()
            }
            .setNegativeButton("إلغاء", null).show()
    }

    private inner class TilesAdapter : RecyclerView.Adapter<TilesAdapter.Holder>() {
        private var shown = emptyList<Tile>()
        inner class Holder(val container: LinearLayout, val icon: ImageView, val label: TextView):
            RecyclerView.ViewHolder(container)
        fun submit(values: List<Tile>) {
            shown = values
            notifyDataSetChanged()
        }
        override fun getItemCount() = shown.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val wrapper = LinearLayout(this@Mg5AppsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = panel("#12517D", "#092E51", 11, "#1598C8")
                isClickable = true
                isFocusable = true
                setPadding(dp(3), dp(8), dp(3), dp(6))
            }
            val p = RecyclerView.LayoutParams(-1, dp(when(drawerCols) {
                6 -> 150; 7 -> 140; else -> 132
            })).apply {
                setMargins(dp(4), dp(5), dp(4), dp(5))
            }
            wrapper.layoutParams = p
            val icon = ImageView(this@Mg5AppsActivity).apply {
                // Render the icon with its native proportions; no centerCrop
                // or drawable tint. Preserve original app-brand artwork.
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = false
                imageTintList = null
                tag = "noTint"
                setPadding(dp(2), dp(2), dp(2), dp(2))
            }
            wrapper.addView(icon, LinearLayout.LayoutParams(dp(when(drawerCols) {
                6 -> 66; 7 -> 60; else -> 54
            }),dp(when(drawerCols) { 6 -> 66; 7 -> 60; else -> 54 })))
            wrapper.addView(View(this@Mg5AppsActivity), LinearLayout.LayoutParams(1, dp(5)))
            val label = text("", if (drawerCols == 8) 14f else 15f, true).apply { maxLines = 2 }
            wrapper.addView(label, LinearLayout.LayoutParams(-1, dp(40)))
            return Holder(wrapper, icon, label)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val tile = shown[position]
            holder.icon.setImageDrawable(tile.icon)
            holder.label.text = tile.title
            holder.container.contentDescription = tile.title
            holder.container.setOnClickListener { launch(tile) }
            holder.container.setOnLongClickListener { hide(tile); true }
        }
    }
}
