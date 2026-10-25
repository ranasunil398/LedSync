package com.example.ledsync

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/** Sends Adalight frames ("Ada" + count-1 hi/lo + checksum + RGB bytes) over USB serial. */
class SerialLink(private val ctx: Context, private val dtrRts: Boolean = Config.DTR_RTS) {
    private var port: UsbSerialPort? = null
    var info: String = ""
    private val header = byteArrayOf(
        'A'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(),
        ((Config.TOTAL - 1) shr 8).toByte(),
        ((Config.TOTAL - 1) and 0xFF).toByte(),
        0
    ).also { it[5] = (it[3].toInt() xor it[4].toInt() xor 0x55).toByte() }
    private val frame = ByteArray(header.size + Config.TOTAL * 3).also { header.copyInto(it) }

    fun open(): Boolean {
        try {
            val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(mgr).firstOrNull()
                ?: run { info = "No supported USB serial device found"; return false }
            if (!mgr.hasPermission(driver.device)) { info = "No USB permission"; return false }
            val conn = mgr.openDevice(driver.device)
                ?: run { info = "Could not open USB device"; return false }
            val p = driver.ports[0]
            p.open(conn)
            p.setParameters(Config.BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            if (dtrRts) {
                try { p.setDTR(true) } catch (_: Exception) { }
                try { p.setRTS(true) } catch (_: Exception) { }
            }
            port = p
            info = "Opened ${driver.javaClass.simpleName} (vid ${driver.device.vendorId}, pid ${driver.device.productId})"
            return true
        } catch (e: Exception) {
            info = "Open error: $e"
            return false
        }
    }

    fun send(rgb: ByteArray) {
        rgb.copyInto(frame, header.size)
        try { port?.write(frame, 200); Status.frames++ } catch (e: Exception) { Status.err = "Write error: $e" }
    }

    fun close() {
        try { port?.write(frame.also { java.util.Arrays.fill(it, header.size, it.size, 0) }, 200) } catch (_: Exception) { }
        try { port?.close() } catch (_: Exception) { }
        port = null
    }
}
