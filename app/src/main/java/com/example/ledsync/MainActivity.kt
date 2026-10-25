package com.example.ledsync

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.hoho.android.usbserial.driver.UsbSerialProber

class MainActivity : Activity() {
    private val usbPerm = "com.example.ledsync.USB_PERMISSION"
    private lateinit var status: TextView
    private var pending: (() -> Unit)? = null
    private val ui = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            var t = Status.text
            if (Status.frames > 0) t += "\nFrames sent: ${Status.frames}"
            if (Status.err.isNotEmpty()) t += "\n${Status.err}"
            status.text = t
            ui.postDelayed(this, 500)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) pending?.invoke()
            else Status.text = "USB permission denied"
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        status = TextView(this).apply { textSize = 18f }
        val test = Button(this).apply { text = "Test lights"; setOnClickListener { ensureUsb { runTest() } } }
        val start = Button(this).apply { text = "Start"; setOnClickListener { ensureUsb { requestCapture() } } }
        val stop = Button(this).apply {
            text = "Stop"
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
                Status.text = "Stopped"
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(status); addView(test); addView(start); addView(stop)
        })
        val f = IntentFilter(usbPerm)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, f)
        ui.post(ticker)
    }

    private fun ensureUsb(then: () -> Unit) {
        pending = then
        val mgr = getSystemService(USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(mgr).firstOrNull()
        if (driver == null) { Status.text = "No supported USB serial device found"; return }
        if (mgr.hasPermission(driver.device)) { then(); return }
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        mgr.requestPermission(driver.device, PendingIntent.getBroadcast(this, 0, Intent(usbPerm).setPackage(packageName), flags))
    }

    /** Sends solid red, green, blue to the whole strip, first with DTR/RTS off, then on. */
    private fun runTest() {
        Status.frames = 0; Status.err = ""
        Thread {
            for (dtr in listOf(false, true)) {
                val tag = "Test (DTR/RTS " + (if (dtr) "ON" else "OFF") + ")"
                val link = SerialLink(this, dtr)
                Status.text = "$tag: opening..."
                if (!link.open()) { Status.text = "$tag: ${link.info}"; return@Thread }
                Status.text = "$tag: ${link.info}. Waiting for board..."
                Thread.sleep(2000)
                for ((name, c) in listOf("RED" to 0, "GREEN" to 1, "BLUE" to 2)) {
                    Status.text = "$tag: all LEDs $name"
                    val buf = ByteArray(Config.TOTAL * 3) { i -> if (i % 3 == c) 200.toByte() else 0 }
                    repeat(25) { link.send(buf); Thread.sleep(100) }
                }
                link.close()
                Thread.sleep(500)
            }
            Status.text = "Test finished. Which round lit the strip: OFF, ON, or none?"
        }.start()
    }

    private fun requestCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION") startActivityForResult(mpm.createScreenCaptureIntent(), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK && data != null) {
            Status.frames = 0; Status.err = ""
            Status.text = "Starting..."
            startForegroundService(
                Intent(this, CaptureService::class.java).putExtra("code", res).putExtra("data", data)
            )
        } else Status.text = "Screen capture not granted"
    }

    override fun onDestroy() { ui.removeCallbacks(ticker); unregisterReceiver(receiver); super.onDestroy() }
}
