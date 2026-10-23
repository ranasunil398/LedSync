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
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.hoho.android.usbserial.driver.UsbSerialProber

class MainActivity : Activity() {
    private val usbPerm = "com.example.ledsync.USB_PERMISSION"
    private lateinit var status: TextView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) requestCapture()
            else status.text = "USB permission denied"
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        status = TextView(this).apply { text = "Plug in your Arduino/ESP, then press Start"; textSize = 20f }
        val start = Button(this).apply { text = "Start"; setOnClickListener { startFlow() } }
        val stop = Button(this).apply {
            text = "Stop"
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
                status.text = "Stopped"
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(status); addView(start); addView(stop)
        })
        val f = IntentFilter(usbPerm)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, f)
    }

    private fun startFlow() {
        val mgr = getSystemService(USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(mgr).firstOrNull()
        if (driver == null) { status.text = "No USB serial device found"; return }
        if (mgr.hasPermission(driver.device)) { requestCapture(); return }
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        mgr.requestPermission(driver.device, PendingIntent.getBroadcast(this, 0, Intent(usbPerm).setPackage(packageName), flags))
    }

    private fun requestCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION") startActivityForResult(mpm.createScreenCaptureIntent(), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, CaptureService::class.java).putExtra("code", res).putExtra("data", data)
            )
            status.text = "Running. You can switch to your TV content."
        } else status.text = "Screen capture not granted"
    }

    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
}
