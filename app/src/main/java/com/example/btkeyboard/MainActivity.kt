package com.example.btkeyboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
class MainActivity : Activity() {

    private lateinit var kb: HidKeyboard
    private lateinit var status: TextView
    private lateinit var keyArea: LinearLayout
    private val modButtons = mutableListOf<Pair<Int, Button>>()

    private val DEFAULT_LAYOUT = """
Esc 1 2 3 4 5 6 7 8 9 0 - = Bs*2
Tab q w e r t y u i o p [ ] \
Ctrl a s d f g h j k l ; ' Enter*2
Shift z x c v b n m , . / Shift
Alt Win Space*6 Left Up Down Right
""".trim()

    // ---- キー名 → HID Usage ----
    private val named: Map<String, Int> = buildMap {
        put("esc", 0x29); put("enter", 0x28); put("bs", 0x2A); put("tab", 0x2B); put("space", 0x2C)
        put("right", 0x4F); put("left", 0x50); put("down", 0x51); put("up", 0x52)
        put("del", 0x4C); put("home", 0x4A); put("end", 0x4D); put("pgup", 0x4B); put("pgdn", 0x4E)
        for (i in 1..12) put("f$i", 0x3A + i - 1)
        ('a'..'z').forEachIndexed { i, c -> put(c.toString(), 0x04 + i) }
        "1234567890".forEachIndexed { i, c -> put(c.toString(), 0x1E + i) }
        mapOf("-" to 0x2D, "=" to 0x2E, "[" to 0x2F, "]" to 0x30, "\\" to 0x31, ";" to 0x33,
            "'" to 0x34, "`" to 0x35, "," to 0x36, "." to 0x37, "/" to 0x38).forEach { (k, v) -> put(k, v) }
    }
    private val mods = mapOf("ctrl" to 0x01, "shift" to 0x02, "alt" to 0x04, "win" to 0x08, "cmd" to 0x08)

    private val prefs by lazy { getSharedPreferences("kb", MODE_PRIVATE) }

    private val devices = mutableListOf<BluetoothDevice>()
    private var devAdapter: ArrayAdapter<String>? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                    if (d.name != null && devices.none { it.address == d.address }) {
                        devices.add(d); refreshDevList()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val d: BluetoothDevice = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                    if (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0) == BluetoothDevice.BOND_BONDED) {
                        kb.connect(d)
                    }
                }
            }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        buildUi()
        registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        })
        val perms = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (perms.all { checkSelfPermission(it) == 0 }) initKb() else requestPermissions(perms, 1)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        if (r.all { it == 0 }) initKb() else status.text = "権限が必要です"
    }

    private fun initKb() {
        kb = HidKeyboard(this) { msg -> runOnUiThread { status.text = msg } }
        kb.start()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#1e1e1e")) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(8, 4, 8, 4) }
        val btnConn = Button(this).apply { text = "iPadを探して接続"; setOnClickListener { showScan() } }
        val btnEdit = Button(this).apply { text = "レイアウト編集"; setOnClickListener { showEdit() } }
        status = TextView(this).apply { text = "初期化中…"; setTextColor(Color.WHITE); setPadding(16, 0, 0, 0) }
        bar.addView(btnConn); bar.addView(btnEdit); bar.addView(status)
        keyArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 4, 4, 4) }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        root.addView(keyArea, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderLayout(prefs.getString("layout", DEFAULT_LAYOUT)!!)
    }

    // ---- レイアウト描画 ----
    private fun renderLayout(text: String) {
        keyArea.removeAllViews(); modButtons.clear()
        text.lines().filter { it.isNotBlank() }.forEach { line ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            line.trim().split(Regex("\\s+")).forEach { tok ->
                val name = tok.substringBefore("*")
                val weight = tok.substringAfter("*", "1").toFloatOrNull() ?: 1f
                val lower = name.lowercase()
                val lp = LinearLayout.LayoutParams(0, -1, weight).apply { setMargins(3, 3, 3, 3) }
                if (name == "_") { row.addView(Space(this), lp); return@forEach }
                val usage = named[lower]
                val bit = mods[lower]
                if (usage == null && bit == null) return@forEach
                val btn = Button(this).apply {
                    this.text = if (name.length == 1) name.uppercase() else name
                    setTextColor(Color.WHITE); isAllCaps = false; typeface = Typeface.DEFAULT_BOLD
                    setBackgroundColor(Color.parseColor("#3a3a3a")); setPadding(0, 0, 0, 0)
                }
                
                if (bit != null) {
                    modButtons.add(bit to btn)
                    // トグル動作をなくし、「押している間だけON、離したらOFF」にする処理
                    btn.setOnTouchListener { v, e ->
                        when (e.action) {
                            MotionEvent.ACTION_DOWN -> {
                                v.setBackgroundColor(Color.parseColor("#5a8dee"))
                                if ((kb.modifiers and bit) == 0) {
                                    kb.toggleMod(bit)
                                }
                                refreshMods()
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.setBackgroundColor(Color.parseColor("#3a3a3a"))
                                if ((kb.modifiers and bit) != 0) {
                                    kb.toggleMod(bit)
                                }
                                refreshMods()
                            }
                        }
                        true
                    }
                } else {
                    btn.setOnTouchListener { v, e ->
                        when (e.action) {
                            MotionEvent.ACTION_DOWN -> { v.setBackgroundColor(Color.parseColor("#5a8dee")); kb.down(usage!!) }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.setBackgroundColor(Color.parseColor("#3a3a3a")); kb.up(usage!!)
                                kb.clearMods(); refreshMods()
                            }
                        }
                        true
                    }
                }
                row.addView(btn, lp)
            }
            keyArea.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun refreshMods() = modButtons.forEach { (bit, btn) ->
        btn.setBackgroundColor(Color.parseColor(if (kb.modifiers and bit != 0) "#e8a33d" else "#3a3a3a"))
    }

    // ---- レイアウト編集 ----
    private fun showEdit() {
        val et = EditText(this).apply {
            setText(prefs.getString("layout", DEFAULT_LAYOUT)); typeface = Typeface.MONOSPACE; setLines(8); gravity = Gravity.TOP
        }
        AlertDialog.Builder(this)
            .setTitle("1行=1段 / スペース区切り / キー*幅 / _*幅=空白")
            .setView(et)
            .setPositiveButton("適用") { _, _ -> prefs.edit().putString("layout", et.text.toString()).apply(); renderLayout(et.text.toString()) }
            .setNeutralButton("初期化") { _, _ -> prefs.edit().remove("layout").apply(); renderLayout(DEFAULT_LAYOUT) }
            .setNegativeButton("キャンセル", null).show()
    }

    // ---- iPad検索&接続 ----
    private fun showScan() {
        val bt = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (!bt.isEnabled) { status.text = "Bluetoothをオンにしてください"; return }
        devices.clear(); devices.addAll(bt.bondedDevices)
        devAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        refreshDevList()
        val lv = ListView(this).apply { adapter = devAdapter }
        val dlg = AlertDialog.Builder(this).setTitle("iPadのBluetooth設定画面を開いておく")
            .setView(lv).setNegativeButton("閉じる") { _, _ -> bt.cancelDiscovery() }.show()
        lv.setOnItemClickListener { _, _, pos, _ ->
            val d = devices[pos]; bt.cancelDiscovery(); dlg.dismiss()
            if (d.bondState == BluetoothDevice.BOND_BONDED) kb.connect(d) else d.createBond()
        }
        bt.startDiscovery()
    }

    private fun refreshDevList() {
        val a = devAdapter ?: return
        a.clear()
        // iPad っぽいものを上に
        devices.sortByDescending { it.name?.contains("iPad", true) == true }
        a.addAll(devices.map { (if (it.bondState == BluetoothDevice.BOND_BONDED) "★ " else "") + it.name + "  (" + it.address + ")" })
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        if (::kb.isInitialized) kb.stop()
    }
}
