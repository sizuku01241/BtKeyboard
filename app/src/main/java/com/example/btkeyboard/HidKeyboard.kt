package com.example.btkeyboard

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context

/** Android を Bluetooth HID キーボードとして振る舞わせる */
@SuppressLint("MissingPermission")
class HidKeyboard(private val ctx: Context, private val onState: (String) -> Unit) {

    private val adapter: BluetoothAdapter =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var hid: BluetoothHidDevice? = null
    var host: BluetoothDevice? = null
        private set

    private val pressed = LinkedHashSet<Int>()
    var modifiers = 0
        private set

    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // 標準的な8バイトキーボードレポート(Report ID 1)
    private val descriptor = b(
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01,
        0x85, 0x01,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
        0xC0
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            onState(if (registered) "HID登録完了" else "HID未登録")
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> { host = device; onState("接続中: ${device.name}") }
                BluetoothProfile.STATE_CONNECTING -> onState("接続しています…")
                BluetoothProfile.STATE_DISCONNECTED -> { if (host == device) host = null; onState("切断") }
            }
        }
    }

    fun start() {
        adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val h = proxy as BluetoothHidDevice
                hid = h
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "BT Keyboard", "Custom layout keyboard", "Me",
                    BluetoothHidDevice.SUBCLASS1_KEYBOARD, descriptor
                )
                h.registerApp(sdp, null, null, ctx.mainExecutor, callback)
            }
            override fun onServiceDisconnected(profile: Int) { hid = null }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun connect(d: BluetoothDevice) {
        adapter.cancelDiscovery()
        hid?.connect(d)
    }

    fun disconnect() { host?.let { hid?.disconnect(it) } }

    private fun send() {
        val d = host ?: return
        val r = ByteArray(8)
        r[0] = modifiers.toByte()
        pressed.take(6).forEachIndexed { i, c -> r[2 + i] = c.toByte() }
        hid?.sendReport(d, 1, r)
    }

    fun down(code: Int) { pressed.add(code); send() }
    fun up(code: Int) { pressed.remove(code); send() }
    fun toggleMod(bit: Int) { modifiers = modifiers xor bit; send() }
    fun clearMods() { if (modifiers != 0) { modifiers = 0; send() } }

    fun stop() {
        hid?.unregisterApp()
        hid?.let { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
    }
}
