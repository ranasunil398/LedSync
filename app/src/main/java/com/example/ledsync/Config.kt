package com.example.ledsync

/**
 * EDIT ONLY THIS NUMBER: the total LEDs on your strip.
 * It must match what your LED Box is set up for.
 */
object Config {
    const val TOTAL = 200

    // Split around a 16:9 TV, clockwise from bottom-left. Top takes any remainder.
    val LEFT = TOTAL * 9 / 50
    val RIGHT = LEFT
    val BOTTOM = TOTAL * 16 / 50
    val TOP = TOTAL - LEFT - RIGHT - BOTTOM

    const val BAUD = 115200         // try 500000 only if your box supports it
    const val BRIGHTNESS = 0.8f     // 0..1
    const val SMOOTHING = 0.5f      // 0 = none, closer to 1 = softer
    const val EDGE_DEPTH = 0.15f    // how far in from each edge to sample

    // Lag settings: lower = lighter on the TV. The app also slows itself
    // automatically if the serial link cannot keep up.
    const val MAX_FPS = 20

    // Small capture = less TV load. Grows automatically for long strips.
    val CAPTURE_WIDTH = (TOP * 2).coerceIn(64, 160)
}
