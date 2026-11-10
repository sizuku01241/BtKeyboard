package com.example.btkeyboard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var hid: HidManager

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> if (result.values.all { it }) hid.start() else hid.status = "Bluetooth権限が必要です" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hid = HidManager(applicationContext)
        val need = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permLauncher.launch(need)

        setContent {
            App(hid, onDiscoverable = {
                // iPad側から探したいとき: Androidを一定時間「検出可能」にする
                startActivity(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                )
            })
        }
    }

    override fun onDestroy() {
        hid.stop()
        super.onDestroy()
    }
}

@Composable
fun App(hid: HidManager, onDiscoverable: () -> Unit) {
    var layout by remember { mutableStateOf(Layouts.all[0]) }
    var showDevices by remember { mutableStateOf(false) }
    val kb = remember { KeyState { hid.send(it) } }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 上部バー: 状態 / レイアウト切替 / 接続操作
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(hid.status, Modifier.weight(1f), fontSize = 14.sp)
                    Layouts.all.forEach { l ->
                        Button(
                            onClick = { layout = l },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (l == layout) Color(0xFF4F8CFF) else Color(0xFF3A3F47)
                            )
                        ) { Text(l.name) }
                    }
                    Button(onClick = { hid.scan(); showDevices = true }) { Text("iPadを探す") }
                    OutlinedButton(onClick = onDiscoverable) { Text("待ち受け") }
                    if (hid.connected) OutlinedButton(onClick = { hid.disconnect() }) { Text("切断") }
                }
                // キーボード本体: 行は等分、キーはweightで横幅を決める
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    layout.rows.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { key -> KeyButton(key, kb, Modifier.weight(key.weight).fillMaxHeight()) }
                        }
                    }
                }
            }
        }
        if (showDevices) DeviceDialog(hid) { showDevices = false }
    }
}

/** 1キー。キーごとに独立してタッチを受けるので、複数キーの同時押しができる */
@Composable
fun KeyButton(key: Key, kb: KeyState, modifier: Modifier) {
    var down by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (down) Color(0xFF4F8CFF) else Color(0xFF2B2F36))
            .pointerInput(key) {
                detectTapGestures(onPress = {
                    down = true; kb.down(key)
                    try { tryAwaitRelease() } finally { down = false; kb.up(key) }
                })
            },
        contentAlignment = Alignment.Center
    ) { Text(key.label, color = Color.White, fontSize = 18.sp) }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceDialog(hid: HidManager, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = { hid.scan() }) { Text("再検索") } },
        dismissButton = { TextButton(onClick = onClose) { Text("閉じる") } },
        title = { Text(if (hid.scanning) "検索中…(iPadのBluetooth設定を開いておく)" else "接続先を選択") },
        text = {
            LazyColumn(Modifier.heightIn(max = 260.dp)) {
                items(hid.found.toList()) { d ->
                    Text(
                        d.name ?: d.address,
                        Modifier.fillMaxWidth().clickable { hid.connect(d); onClose() }.padding(12.dp)
                    )
                }
            }
        }
    )
}
