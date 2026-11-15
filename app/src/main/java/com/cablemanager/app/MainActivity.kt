package com.cablemanager.app

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var infoView: TextView
    private var receiverRegistered = false

    private val permissionAction = "com.cablemanager.app.USB_PERMISSION"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refresh()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(permissionAction)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            receiverRegistered = true
        } catch (e: Exception) {
            receiverRegistered = false
        }
        refresh()
    }

    override fun onStop() {
        super.onStop()
        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // ignore
            }
            receiverRegistered = false
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F6FA"))
            setPadding(dp(16), dp(32), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = "Cable Manager"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1565C0"))
            gravity = Gravity.CENTER
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val refreshBtn = Button(this).apply {
            text = "Refresh"
            setOnClickListener { refresh() }
        }
        val permBtn = Button(this).apply {
            text = "USB Permission"
            setOnClickListener { requestUsbPermissions() }
        }
        buttonRow.addView(
            refreshBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        buttonRow.addView(
            permBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        infoView = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#222222"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this)
        scroll.addView(
            infoView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun requestUsbPermissions() {
        try {
            val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            val flags = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val intent = Intent(permissionAction).setPackage(packageName)
            val pi = PendingIntent.getBroadcast(this, 0, intent, flags)
            for (device in usbManager.deviceList.values) {
                if (!usbManager.hasPermission(device)) {
                    usbManager.requestPermission(device, pi)
                }
            }
        } catch (e: Exception) {
            infoView.text = "Permission error: ${e.message}"
        }
    }

    private fun refresh() {
        if (!::infoView.isInitialized) return
        val sb = StringBuilder()
        try {
            sb.append(chargingInfo())
        } catch (e: Exception) {
            sb.append("Charging info paowa jayni: ${e.message}\n")
        }
        sb.append("\n")
        try {
            sb.append(usbInfo())
        } catch (e: Exception) {
            sb.append("USB info paowa jayni: ${e.message}\n")
        }
        sb.append("\n")
        sb.append(noteText())
        infoView.text = sb.toString()
    }

    private fun chargingInfo(): String {
        val sb = StringBuilder()
        sb.append("=== CHARGING INFO ===\n")

        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) {
            sb.append("Battery data paowa jayni.\n")
            return sb.toString()
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0

        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging hocche"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Charge hocche na"
            else -> "Unknown"
        }

        val pluggedText = when (plugged) {
            0 -> "Kono charger connect nei"
            1 -> "AC charger (wall adapter)"
            2 -> "USB (computer/USB port)"
            4 -> "Wireless"
            8 -> "Dock"
            else -> "Onno dhoron ($plugged)"
        }

        sb.append("Battery: ${if (percent >= 0) "$percent%" else "N/A"}\n")
        sb.append("Status: $statusText\n")
        sb.append("Power source: $pluggedText\n")
        sb.append("Voltage: ${String.format(Locale.US, "%.2f", voltage / 1000.0)} V\n")
        sb.append("Temperature: ${String.format(Locale.US, "%.1f", temp)} °C\n")

        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val rawNow = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val rawAvg = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)

        val nowMa = toMilliAmp(rawNow)
        val avgMa = toMilliAmp(rawAvg)

        if (nowMa != null) {
            sb.append("Current (ekhon): ${String.format(Locale.US, "%.0f", nowMa)} mA\n")
        } else {
            sb.append("Current (ekhon): ei phone support kore na\n")
        }
        if (avgMa != null) {
            sb.append("Current (gor): ${String.format(Locale.US, "%.0f", avgMa)} mA\n")
        }

        if (nowMa != null && voltage > 0) {
            val watt = Math.abs(nowMa) * voltage / 1_000_000.0
            sb.append("Approx power: ${String.format(Locale.US, "%.1f", watt)} W\n")
        }

        if (plugged != 0 && nowMa != null) {
            val a = Math.abs(nowMa)
            val rating = when {
                a < 300 -> "Khub kom current. Cable kharap/dhire charger hote pare."
                a < 1000 -> "Dhire (USB 2.0 level)."
                a < 2000 -> "Majhari speed."
                a < 3000 -> "Bhalo speed (~2A)."
                else -> "Fast charging (3A ba tar beshi)."
            }
            sb.append("Cable/charger estimate: $rating\n")
        }

        return sb.toString()
    }

    private fun toMilliAmp(raw: Long): Double? {
        if (raw == Long.MIN_VALUE || raw == 0L) return null
        val abs = Math.abs(raw)
        // Beshirbhag phone microamp dey, kichu phone milliamp dey
        return if (abs >= 20000L) raw / 1000.0 else raw.toDouble()
    }

    private fun usbInfo(): String {
        val sb = StringBuilder()
        sb.append("=== USB DEVICES ===\n")

        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usbManager.deviceList

        if (devices.isEmpty()) {
            sb.append("Kono USB device connect nei.\n")
            sb.append("(Sudhu charger lagale ekhane kichu dekhabe na.)\n")
            return sb.toString()
        }

        var index = 1
        for (device in devices.values) {
            sb.append("\nDevice #$index\n")
            sb.append("Name: ${device.deviceName}\n")
            sb.append("Vendor ID: ${device.vendorId} (0x${Integer.toHexString(device.vendorId)})\n")
            sb.append("Product ID: ${device.productId} (0x${Integer.toHexString(device.productId)})\n")
            sb.append("Type: ${deviceTypes(device)}\n")
            sb.append("Interface count: ${device.interfaceCount}\n")

            val hasPerm = usbManager.hasPermission(device)
            sb.append("Permission: ${if (hasPerm) "Ache" else "Nei (USB Permission button chapo)"}\n")

            if (hasPerm) {
                sb.append("Manufacturer: ${safe { device.manufacturerName }}\n")
                sb.append("Product: ${safe { device.productName }}\n")
                sb.append("Serial: ${safe { device.serialNumber }}\n")
                if (Build.VERSION.SDK_INT >= 23) {
                    sb.append("Version: ${safe { device.version }}\n")
                }
            }
            index++
        }
        return sb.toString()
    }

    private fun safe(block: () -> String?): String {
        return try {
            block() ?: "N/A"
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun deviceTypes(device: UsbDevice): String {
        val names = LinkedHashSet<String>()
        if (device.deviceClass != 0) {
            names.add(className(device.deviceClass))
        }
        for (i in 0 until device.interfaceCount) {
            names.add(className(device.getInterface(i).interfaceClass))
        }
        return if (names.isEmpty()) "Unknown" else names.joinToString(", ")
    }

    private fun className(c: Int): String = when (c) {
        1 -> "Audio"
        2 -> "Communication (CDC)"
        3 -> "HID (keyboard/mouse)"
        5 -> "Physical"
        6 -> "Still image (camera/PTP)"
        7 -> "Printer"
        8 -> "Mass storage (pendrive/disk)"
        9 -> "Hub"
        10 -> "CDC data"
        11 -> "Smart card"
        13 -> "Content security"
        14 -> "Video"
        224 -> "Wireless controller"
        239 -> "Miscellaneous"
        254 -> "Application specific"
        255 -> "Vendor specific"
        else -> "Class $c"
    }

    private fun noteText(): String {
        return "=== JENE RAKHO ===\n" +
            "- Cable-er bhetorer wire, length, quality phone mapte pare na.\n" +
            "- Charging estimate charger ar phone-er upor-o nirbhor kore.\n" +
            "- Cable test korte: ekoi charger ar ekoi phone-e cable bodole dekho.\n"
    }
}
