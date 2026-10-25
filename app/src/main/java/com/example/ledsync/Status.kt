package com.example.ledsync

/** Simple on-screen status shared between the service and the main screen. */
object Status {
    @Volatile var text: String = "Plug in the LED box, then press Test or Start"
    @Volatile var frames: Long = 0
    @Volatile var err: String = ""
}
