package com.example.ledsync

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/** Sends Adalight frames ("Ada" + count-1 hi/lo + checksum + RGB bytes) over USB serial. */
class SerialLink(private val ctx: Context) {
    private var port: UsbSerialPort? = null
    private val header = byteArrayOf(
        'A'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(),
        ((Config.TOTAL - 1) shr 8).toByte(),
        ((Config.TOTAL - 1) and 0xFF).toByte(),
        0
    ).also { it[5] = (it[3].toInt() xor it[4].toInt() xor 0x55).toByte() }
    private val frame = ByteArray(header.size + Config.TOTAL * 3).also {
        header.copyInto(it)
    }

    fun open(): Boolean {
        val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(mgr).firstOrNull() ?: return false
        val conn = mgr.openDevice(driver.device) ?: return false
        val p = driver.ports[0]
        p.open(conn)
        p.setParameters(Config.BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        port = p
        return true
    }

    fun send(rgb: ByteArray) {
        rgb.copyInto(frame, header.size)
        try { port?.write(frame, 100) } catch (_: Exception) { }
    }

    fun close() {
        try { port?.write(frame.also { java.util.Arrays.fill(it, header.size, it.size, 0) }, 100) } catch (_: Exception) { }
        try { port?.close() } catch (_: Exception) { }
        port = null
    }
}
