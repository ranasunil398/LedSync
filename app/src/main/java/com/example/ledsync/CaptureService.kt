package com.example.ledsync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import kotlin.math.max
import kotlin.math.min

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var link: SerialLink? = null
    // Low priority so video playback always wins the CPU.
    private val thread = HandlerThread("ledsync", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val handler by lazy { Handler(thread.looper) }
    private val smooth = FloatArray(Config.TOTAL * 3)
    private val out = ByteArray(Config.TOTAL * 3)
    private var lastFrame = 0L

    // Never send faster than the serial link can carry (6 header bytes + RGB, 10 bits per byte).
    private val minInterval: Long = max(
        1000L / Config.MAX_FPS,
        ((6 + Config.TOTAL * 3) * 10_000L) / Config.BAUD + 2
    )

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("led", "LED Sync", NotificationManager.IMPORTANCE_LOW))
        val note = Notification.Builder(this, "led").setContentTitle("LED Sync running")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, note)

        val code = intent.getIntExtra("code", 0)
        @Suppress("DEPRECATION") val data = intent.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        handler.post {
            val l = SerialLink(this)
            if (!l.open()) { Status.text = "Failed: ${l.info}"; stopSelf(); return@post }
            Status.text = "Running. ${l.info}"
            link = l
            Thread.sleep(1800) // board may reset when the port opens

            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = mpm.getMediaProjection(code, data)
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, handler)
            projection = mp

            val m = resources.displayMetrics
            val w = Config.CAPTURE_WIDTH
            val h = (w * m.heightPixels.toFloat() / m.widthPixels).toInt().coerceAtLeast(16)
            val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            r.setOnImageAvailableListener({ rd ->
                val img = rd.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastFrame >= minInterval) {
                        lastFrame = now
                        process(img)
                        link?.send(out)
                    }
                } finally { img.close() }
            }, handler)
            reader = r
            display = mp.createVirtualDisplay(
                "ledsync", w, h, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler
            )
        }
        return START_NOT_STICKY
    }

    private fun process(img: Image) {
        val p = img.planes[0]
        val buf = p.buffer
        val rs = p.rowStride
        val w = img.width
        val h = img.height
        val depth = (min(w, h) * Config.EDGE_DEPTH).toInt().coerceAtLeast(2)
        var idx = 0

        fun zone(x0: Int, y0: Int, x1: Int, y1: Int) {
            val xe = max(x1, x0 + 1).coerceAtMost(w)
            val ye = max(y1, y0 + 1).coerceAtMost(h)
            var r = 0L; var g = 0L; var b = 0L; var n = 0
            var y = y0
            while (y < ye) {
                var x = x0
                while (x < xe) {
                    val o = y * rs + x * 4
                    r += buf.get(o).toInt() and 0xFF
                    g += buf.get(o + 1).toInt() and 0xFF
                    b += buf.get(o + 2).toInt() and 0xFF
                    n++; x++
                }
                y++
            }
            if (n == 0) n = 1
            val a = Config.SMOOTHING
            val k = idx * 3
            smooth[k] = smooth[k] * a + (r / n) * (1 - a)
            smooth[k + 1] = smooth[k + 1] * a + (g / n) * (1 - a)
            smooth[k + 2] = smooth[k + 2] * a + (b / n) * (1 - a)
            for (c in 0..2) out[k + c] = (smooth[k + c] * Config.BRIGHTNESS).toInt().coerceIn(0, 255).toByte()
            idx++
        }

        for (i in 0 until Config.LEFT) zone(0, h - (i + 1) * h / Config.LEFT, depth, h - i * h / Config.LEFT)
        for (i in 0 until Config.TOP) zone(i * w / Config.TOP, 0, (i + 1) * w / Config.TOP, depth)
        for (i in 0 until Config.RIGHT) zone(w - depth, i * h / Config.RIGHT, w, (i + 1) * h / Config.RIGHT)
        for (i in 0 until Config.BOTTOM) zone(w - (i + 1) * w / Config.BOTTOM, h - depth, w - i * w / Config.BOTTOM, h)
    }

    override fun onDestroy() {
        display?.release(); reader?.close(); projection?.stop(); link?.close()
        thread.quitSafely()
        super.onDestroy()
    }

    companion object { const val ACTION_STOP = "com.example.ledsync.STOP" }
}
