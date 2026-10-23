package com.example.ledsync

/** Edit to match your strip. LEDs are ordered clockwise starting at the bottom-left corner:
 *  up the left side, across the top, down the right side, across the bottom (right to left). */
object Config {
    const val LEFT = 17
    const val TOP = 30
    const val RIGHT = 17
    const val BOTTOM = 30
    const val TOTAL = LEFT + TOP + RIGHT + BOTTOM   // must equal NUM_LEDS in the Arduino sketch

    const val BAUD = 115200
    const val BRIGHTNESS = 0.8f     // 0..1
    const val SMOOTHING = 0.5f      // 0 = no smoothing, closer to 1 = slower/softer
    const val EDGE_DEPTH = 0.15f    // fraction of screen sampled inward from each edge
    const val MAX_FPS = 30
    const val CAPTURE_WIDTH = 192   // small on purpose; height follows screen aspect
}
