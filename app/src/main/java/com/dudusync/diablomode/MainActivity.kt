package com.dudusync.diablomode

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {

    // ---------- paleta ----------
    private val BG = Color.parseColor("#0B0B0E")
    private val CARD = Color.parseColor("#17171C")
    private val STROKE = Color.parseColor("#26262E")
    private val RED = Color.parseColor("#FF3B30")
    private val GREEN = Color.parseColor("#34C759")
    private val ORANGE = Color.parseColor("#FF9F0A")
    private val GRAY = Color.parseColor("#8A8A93")
    private val WHITE = Color.parseColor("#F2F2F7")

    private val ui = Handler(Looper.getMainLooper())

    private val POL0 = "/sys/devices/system/cpu/cpufreq/policy0"
    private val POL6 = "/sys/devices/system/cpu/cpufreq/policy6"
    private val KGSL = "/sys/class/kgsl/kgsl-3d0"
    private val CL = "/sys/devices/system/cpu/cpufreq_limit"

    private val scriptFile get() = File(filesDir, "diablo_loop.sh")
    private val statusFile get() = File(filesDir, "status.sh")
    private val flagFile get() = File(filesDir, "diablo.on")
    private val intFile get() = File(filesDir, "interval.txt")
    private val modeFile get() = File(filesDir, "mode.txt")
    private val stockFile get() = File(filesDir, "stock.txt")
    private val zonesFile get() = File(filesDir, "zones.txt")
    private val valuesCache get() = File(filesDir, "values.cache")
    private var loadingOverlay: LinearLayout? = null
    private var firstRenderDone = false

    private val steps = floatArrayOf(0.5f, 1f, 2f, 3f, 4f, 5f, 6f)

    // views
    private lateinit var pill: TextView
    private lateinit var toggle: TextView
    private lateinit var logView: TextView
    private lateinit var intervalLabel: TextView
    private lateinit var barMid: BarView
    private lateinit var barPrime: BarView
    private lateinit var barGpu: BarView
    private lateinit var barBatt: BarView
    private lateinit var midBig: TextView
    private lateinit var primeBig: TextView
    private lateinit var gpuBig: TextView
    private lateinit var battBig: TextView
    private lateinit var midSub: TextView
    private lateinit var primeSub: TextView
    private lateinit var gpuSub: TextView
    private lateinit var battSub: TextView
    private lateinit var midTemp: TextView
    private lateinit var primeTemp: TextView
    private lateinit var gpuTemp: TextView
    private lateinit var battTemp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(28))
        }
        scroll.addView(col)
        val frame = FrameLayout(this).apply { setBackgroundColor(BG) }
        frame.addView(scroll)
        loadingOverlay = buildLoading()
        frame.addView(loadingOverlay)
        setContentView(frame)

        // header
        col.addView(TextView(this).apply {
            text = "DIABLO MODE"
            textSize = 34f
            setTextColor(RED)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            text = "by DuduSync"
            textSize = 13f
            setTextColor(GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(18))
        })

        // status pill
        pill = TextView(this).apply {
            text = "VERIFICANDO..."
            textSize = 14f
            setTextColor(WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(12))
            background = rounded(Color.parseColor("#2A2A30"), 14)
        }
        col.addView(pill, matchWrap(top = 0, bottom = 16))

        // cards
        val (cMid, bMid, bigMid, subMid, tmpMid) = makeCard("CPU 0-5  ·  MID", "—")
        barMid = bMid; midBig = bigMid; midSub = subMid; midTemp = tmpMid
        col.addView(cMid, matchWrap(top = 0, bottom = 12))

        val (cPrime, bPrime, bigPrime, subPrime, tmpPrime) = makeCard("CPU 6-7  ·  PRIME", "—")
        barPrime = bPrime; primeBig = bigPrime; primeSub = subPrime; primeTemp = tmpPrime
        col.addView(cPrime, matchWrap(top = 0, bottom = 12))

        val (cGpu, bGpu, bigGpu, subGpu, tmpGpu) = makeCard("GPU  ·  ADRENO", "—")
        barGpu = bGpu; gpuBig = bigGpu; gpuSub = subGpu; gpuTemp = tmpGpu
        col.addView(cGpu, matchWrap(top = 0, bottom = 12))

        val (cBatt, bBatt, bigBatt, subBatt, tmpBatt) = makeCard("BATERIA", "—")
        barBatt = bBatt; battSub = subBatt; battTemp = tmpBatt
        col.addView(cBatt, matchWrap(top = 0, bottom = 12))
        battBig = bigBatt

        // mode card (TURBO / AUTO)
        col.addView(makeModeCard(), matchWrap(top = 0, bottom = 12))

        // interval card
        col.addView(makeIntervalCard(), matchWrap(top = 0, bottom = 18))

        // toggle
        toggle = TextView(this).apply {
            text = "VERIFICANDO ROOT..."
            textSize = 20f
            setTextColor(WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
            background = rounded(Color.parseColor("#3A3A42"), 22)
            setOnClickListener { onToggleTap() }
        }
        col.addView(toggle, matchWrap(top = 0, bottom = 12))

        col.addView(TextView(this).apply {
            text = "Modo Diablo trava CPU/GPU no máximo e ignora os caps do sistema.\n" +
                    "⚠️ Sem cooler, esquenta muito e pode reduzir a vida útil."
            textSize = 11f
            setTextColor(GRAY)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(14))
        })

        logView = TextView(this).apply {
            text = ""
            textSize = 11f
            setTextColor(Color.parseColor("#7DD87D"))
            typeface = Typeface.MONOSPACE
        }
        col.addView(logView)

        writeAsset("diablo_loop.sh", scriptFile, mapOf("__FLAG__" to flagFile.absolutePath, "__INTF__" to intFile.absolutePath, "__MODE__" to modeFile.absolutePath))
        writeAsset("status.sh", statusFile, emptyMap())
        if (!intFile.exists()) intFile.writeText("2")
        if (!modeFile.exists()) modeFile.writeText("turbo")

        loadCachedValues()
        refresh()
        ui.postDelayed(object : Runnable {
            override fun run() { refresh(); ui.postDelayed(this, 1200) }
        }, 1200)
    }

    // ---------------- UI helpers ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            setCornerRadius(dp(radiusDp).toFloat())
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }
    }

    private fun matchWrap(top: Int, bottom: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    }

    private fun writeAsset(name: String, dst: File, repl: Map<String, String>) {
        try {
            var txt = assets.open(name).bufferedReader().use { it.readText() }
            for ((k, v) in repl) txt = txt.replace(k, v)
            dst.writeText(txt)
        } catch (_: Exception) {}
    }

    private data class CardRefs(
        val root: LinearLayout,
        val bar: BarView,
        val big: TextView,
        val sub: TextView,
        val temp: TextView
    )

    private fun makeCard(title: String, initial: String): CardRefs {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(CARD, 20, STROKE)
        }

        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val t = TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(GRAY)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val temp = TextView(this).apply {
            text = initial
            textSize = 13f
            setTextColor(GREEN)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.END
        }
        head.addView(t)
        head.addView(temp)

        val big = TextView(this).apply {
            text = initial
            textSize = 30f
            setTextColor(WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        }

        val bar = BarView(this).apply {
            barColor = RED
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
            )
        }

        val sub = TextView(this).apply {
            text = ""
            textSize = 11f
            setTextColor(Color.parseColor("#9A9AA3"))
            setPadding(0, dp(8), 0, 0)
            typeface = Typeface.MONOSPACE
        }

        card.addView(head)
        card.addView(big)
        card.addView(bar)
        card.addView(sub)
        return CardRefs(card, bar, big, sub, temp)
    }

    // ---------------- root ----------------

    private fun su(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.waitFor()
            out
        } catch (_: Exception) { "" }
    }

    private fun rootOk(): Boolean = su("id").contains("uid=0")

    // ---------------- status ----------------

    private data class S(
        val gm: String, val gc: String, val gx: String, val gt: String,
        val c0m: String, val c0c: String, val c0x: String,
        val c6m: String, val c6c: String, val c6x: String,
        val gov: String, val kcap: String,
        val bt: String, val bc: String, val bs: String,
        val tmid: String, val tprime: String
    )

    private fun refresh() {
        Thread {
            val root = rootOk()
            val raw = if (root) su("sh ${statusFile.absolutePath} ${zonesFile.absolutePath}") else ""
            val m = HashMap<String, String>()
            for (line in raw.split("\n")) {
                val i = line.indexOf('=')
                if (i > 0) m[line.substring(0, i).trim()] = line.substring(i + 1).trim()
            }
            val on = flagFile.exists()
            ui.post {
                render(root, on, m)
                if (m.isNotEmpty()) writeValuesCache(m)
            }
        }.start()
    }

    private fun render(root: Boolean, on: Boolean, m: Map<String, String>) {
        toggle.text = when {
            !root -> "SEM ROOT"
            on -> "DESLIGAR"
            else -> "ATIVAR MODO DIABLO"
        }
        toggle.background = rounded(if (on) Color.parseColor("#1E7A34") else Color.parseColor("#B3261E"), 22)
        pill.text = when {
            !root -> "SEM ROOT — autorize no KernelSU/Magisk"
            on -> "DIABLO ON  ·  clock travado no máximo"
            else -> "STOCK  ·  modo desligado"
        }
        pill.background = rounded(if (on) Color.parseColor("#3A1414") else Color.parseColor("#1C1C22"), 14)
        pill.setTextColor(if (on) RED else GRAY)

        if (!root) return
        renderValues(m)
        if (!firstRenderDone) {
            firstRenderDone = true
            hideLoading()
        }
    }

    private fun renderValues(m: Map<String, String>) {
        val kcap = num(m["kcap"])

        // CPU mid (0-5)
        val c0m = m["c0m"] ?: "0"; val c0c = m["c0c"] ?: "0"; val c0x = m["c0x"] ?: "0"
        val c0hw = m["c0hw"] ?: c0x
        val eff0 = minOf(num(c0x), if (kcap > 0) kcap else num(c0x))
        midBig.text = ghz(c0c)
        barMid.fraction = frac(c0c, c0hw)
        barMid.barColor = heatColor(num(m["tmid"]))
        midSub.text = "min ${ghz(c0m)}  ·  chip ${ghz(c0hw)}  ·  ${m["gov"] ?: "?"}"
        if (eff0 < num(c0hw)) midSub.append("  ·  cap ${ghz(eff0.toString())}")
        midTemp.text = temp(m["tmid"])
        midTemp.setTextColor(heatColor(num(m["tmid"])))

        // CPU prime (6-7)
        val c6m = m["c6m"] ?: "0"; val c6c = m["c6c"] ?: "0"; val c6x = m["c6x"] ?: "0"
        val c6hw = m["c6hw"] ?: c6x
        val eff6 = minOf(num(c6x), if (kcap > 0) kcap else num(c6x))
        primeBig.text = ghz(c6c)
        barPrime.fraction = frac(c6c, c6hw)
        barPrime.barColor = heatColor(num(m["tprime"]))
        primeSub.text = "min ${ghz(c6m)}  ·  chip ${ghz(c6hw)}"
        if (eff6 < num(c6hw)) primeSub.append("  ·  cap ${ghz(eff6.toString())}")
        primeTemp.text = temp(m["tprime"])
        primeTemp.setTextColor(heatColor(num(m["tprime"])))

        // GPU
        val gm = m["gm"] ?: "0"; val gc = m["gc"] ?: "0"; val gx = m["gx"] ?: "0"
        val ghw = m["ghw"] ?: gx
        gpuBig.text = "${gc.trim().toLongOrNull() ?: 0} MHz"
        val ghwMhz = (ghw.trim().toLongOrNull() ?: 0L) / 1_000_000L
        barGpu.fraction = if (ghwMhz > 0) (gc.trim().toLongOrNull() ?: 0L) / ghwMhz.toFloat() else 0f
        barGpu.barColor = heatColor(num(m["gt"]))
        gpuSub.text = "min ${hzToMhz(gm)}  ·  chip ${hzToMhz(ghw)}"
        gpuTemp.text = temp(m["gt"])
        gpuTemp.setTextColor(heatColor(num(m["gt"])))

        // bateria
        val bt = m["bt"] ?: "0"; val bc = m["bc"] ?: "?"
        battBig.text = "$bc%"
        barBatt.fraction = (num(bc) / 100f).coerceIn(0f, 1f)
        barBatt.barColor = if (num(bc) > 20) GREEN else ORANGE
        battSub.text = "${m["bs"] ?: ""}"
        battTemp.text = battTempStr(bt)
    }

    // ---------------- formatting ----------------

    private fun num(s: String?): Long = s?.trim()?.toLongOrNull() ?: 0L

    private fun ghz(khz: String): String {
        val v = khz.trim().toLongOrNull() ?: return "—"
        return String.format("%.2f GHz", v / 1_000_000.0)
    }

    private fun mhz(khz: String): String {
        val v = khz.trim().toLongOrNull() ?: return "—"
        return if (v > 1_000_000) "${v / 1000} MHz" else "$v MHz"
    }

    private fun hzToMhz(hz: String): String {
        val v = hz.trim().toLongOrNull() ?: return "—"
        return "${v / 1_000_000} MHz"
    }

    private fun mhzToHz(v: String): String {
        // devfreq vem em Hz (1200000000); clock_mhz vem em MHz (1200)
        val n = v.trim().toLongOrNull() ?: return "0"
        return if (n > 100_000) n.toString() else (n * 1_000_000).toString()
    }

    private fun frac(cur: String, max: String): Float {
        val c = cur.trim().toLongOrNull() ?: return 0f
        val x = max.trim().toLongOrNull() ?: return 0f
        if (x <= 0) return 0f
        return (c.toFloat() / x.toFloat())
    }

    private fun temp(mc: String?): String {
        val v = mc?.trim()?.toLongOrNull() ?: return "—"
        return String.format("%.1f°C", v / 1000.0)
    }

    private fun battTempStr(t: String?): String {
        val v = t?.trim()?.toLongOrNull() ?: return "—"
        return String.format("%.1f°C", v / 10.0)
    }

    private fun heatColor(mc: Long): Int {
        val c = mc / 1000.0
        return when {
            c >= 80 -> RED
            c >= 65 -> ORANGE
            else -> GREEN
        }
    }

    // ---------------- loading / cache ----------------

    private fun buildLoading(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setGravity(Gravity.CENTER)
        }
        box.addView(TextView(this).apply {
            text = "DIABLO MODE"
            textSize = 26f
            setTextColor(RED)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        val pb = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { topMargin = dp(24) }
        }
        box.addView(pb)
        box.addView(TextView(this).apply {
            text = "carregando clocks do aparelho…"
            textSize = 12f
            setTextColor(GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })
        return box
    }

    private fun hideLoading() {
        val o = loadingOverlay ?: return
        o.animate().alpha(0f).setDuration(220).withEndAction {
            o.visibility = View.GONE
        }.start()
    }

    private fun loadCachedValues() {
        try {
            if (!valuesCache.exists()) return
            val m = HashMap<String, String>()
            for (line in valuesCache.readLines()) {
                val i = line.indexOf('=')
                if (i > 0) m[line.substring(0, i)] = line.substring(i + 1)
            }
            if (m.isNotEmpty()) renderValues(m)
        } catch (_: Exception) {}
    }

    private fun writeValuesCache(m: Map<String, String>) {
        try {
            val sb = StringBuilder()
            for ((k, v) in m) sb.append(k).append('=').append(v).append('\n')
            valuesCache.writeText(sb.toString())
        } catch (_: Exception) {}
    }

    // ---------------- actions ----------------

    private fun onToggleTap() {
        if (!rootOk()) { log("sem root — autorize no KernelSU"); return }
        if (flagFile.exists()) turnOff() else confirmTurnOn()
    }

    private fun confirmTurnOn() {
        val d = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            background = rounded(Color.parseColor("#1A1A20"), 24, STROKE)
        }
        val title = TextView(this).apply {
            text = "⚠️  MODO DIABLO"
            textSize = 20f
            setTextColor(RED)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val warn = TextView(this).apply {
            text = "Sem cooler, o celular esquenta muito. O calor excessivo " +
                    "pode reduzir a vida útil da bateria e do chip a longo prazo."
            textSize = 13f
            setTextColor(Color.parseColor("#DDDDDD"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(14))
        }
        val count = TextView(this).apply {
            text = "Ativando em 5s..."
            textSize = 16f
            setTextColor(ORANGE)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cancel = TextView(this).apply {
            text = "CANCELAR"
            textSize = 14f
            setTextColor(WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            background = rounded(Color.parseColor("#2A2A30"), 16)
            setOnClickListener { d.dismiss() }
        }
        box.addView(title)
        box.addView(warn)
        box.addView(count)
        box.addView(cancel, matchWrap(top = 14, bottom = 0))
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        var sec = 6
        val h = Handler(Looper.getMainLooper())
        val run = object : Runnable {
            override fun run() {
                sec--
                if (sec <= 0) {
                    d.dismiss()
                    turnOn()
                } else {
                    count.text = "Ativando em ${sec}s..."
                    h.postDelayed(this, 1000)
                }
            }
        }
        d.setOnDismissListener { h.removeCallbacks(run) }
        d.show()
        h.postDelayed(run, 1000)
    }

    private fun turnOn() {
        log(">> ligando DIABLO...")
        saveStockOnce()
        // mata loops concorrentes (modulo antigo, app, etc)
        su("pkill -f diablo_loop.sh")
        su("pkill -f banana_unlock/service.sh")
        try { flagFile.writeText("1") } catch (e: Exception) { log("erro flag: ${e.message}"); return }
        if (!intFile.exists()) intFile.writeText("2")
        if (!modeFile.exists()) modeFile.writeText("turbo")
        val sh = scriptFile.absolutePath
        su("chmod 755 $sh")
        su("setsid sh $sh </dev/null >/dev/null 2>&1 &")
        log("   loop iniciado (intervalo ${intFile.readText()}s)")
        refresh()
    }

    private fun turnOff() {
        log(">> desligando (stock: walt)...")
        try { flagFile.delete() } catch (_: Exception) {}
        su("pkill -f diablo_loop.sh")
        // governador stock Samsung = walt + faixas stock
        su("echo walt > $POL0/scaling_governor")
        su("echo walt > $POL6/scaling_governor")
        su("echo 556800 > $POL0/scaling_min_freq")
        su("echo 3072000 > $POL0/scaling_max_freq")
        su("echo 1017600 > $POL6/scaling_min_freq")
        su("echo 3513600 > $POL6/scaling_max_freq")
        // religa thermal + FDE.AI (re-capam como stock)
        su("setprop ctl.start thermal-engine")
        su("setprop ctl.start vendor.samsung.hardware.thermal-default")
        su("pm enable --user 0 com.feravolt.fdeai.donate")
        stockFile.delete()
        log("   stock: walt, thermal on, FDE on")
        refresh()
    }

    private fun saveStockOnce() {
        try {
            if (stockFile.exists()) return
            val g0 = su("cat $POL0/scaling_governor").trim()
            val g6 = su("cat $POL6/scaling_governor").trim()
            if (g0.isEmpty() || g6.isEmpty()) return
            stockFile.writeText("g0=$g0\ng6=$g6\n")
            log("   stock salvo: $g0 / $g6")
        } catch (_: Exception) {}
    }

    private fun readStock(k: String): String? {
        return try {
            if (!stockFile.exists()) return null
            stockFile.readLines().firstOrNull { it.startsWith("$k=") }?.substringAfter("=")
        } catch (_: Exception) { null }
    }

    // ---------------- interval card ----------------

    private fun makeModeCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(CARD, 20, STROKE)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setGravity(Gravity.CENTER_VERTICAL)
        }
        val title = TextView(this).apply {
            text = "MODO TURBO (trava no máximo)"
            textSize = 14f
            setTextColor(WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val curMode = try { modeFile.readText().trim() } catch (_: Exception) { "turbo" }
        val sw = Switch(this).apply {
            isChecked = curMode != "auto"
            setOnCheckedChangeListener { _, checked ->
                try { modeFile.writeText(if (checked) "turbo" else "auto") } catch (_: Exception) {}
                log(if (checked) "TURBO: trava min=max no máximo" else "AUTO: caps removidos, escala sob demanda")
            }
        }
        row.addView(title)
        row.addView(sw)
        card.addView(row)
        card.addView(TextView(this).apply {
            text = "TURBO = sempre no talo (esquenta muito).  AUTO = remove os caps e escala sob demanda (mais frio, mesmo pico em jogo)."
            textSize = 10f
            setTextColor(Color.parseColor("#9A9AA3"))
            setPadding(0, dp(6), 0, 0)
        })
        return card
    }

    private fun makeIntervalCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            background = rounded(CARD, 20, STROKE)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val t = TextView(this).apply {
            text = "INTERVALO DE RE-APLICAÇÃO"
            textSize = 12f
            setTextColor(GRAY)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        intervalLabel = TextView(this).apply {
            text = "2s"
            textSize = 14f
            setTextColor(RED)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.END
        }
        head.addView(t)
        head.addView(intervalLabel)

        val seek = SeekBar(this).apply {
            max = steps.size - 1
            progress = 2
            setPadding(0, dp(10), 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val sec = steps[p]
                    intervalLabel.text = if (sec < 1f) "${sec}s" else "${sec.toInt()}s"
                    try { intFile.writeText(if (sec < 1f) "0.5" else sec.toInt().toString()) } catch (_: Exception) {}
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        card.addView(head)
        card.addView(seek)
        card.addView(TextView(this).apply {
            text = "0.5s · 1 · 2 · 3 · 4 · 5 · 6s   (menor = menos dip, gasta mais)"
            textSize = 10f
            setTextColor(Color.parseColor("#666670"))
        })
        return card
    }

    private fun log(s: String) {
        ui.post { logView.append(s + "\n") }
    }
}
