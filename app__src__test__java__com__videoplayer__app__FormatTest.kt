package com.videoplayer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("00:00", formatTime(0))
        assertEquals("01:05", formatTime(65_000))
    }

    @Test
    fun formatsHours() {
        assertEquals("1:01:01", formatTime(3_661_000))
    }

    @Test
    fun negativeIsClampedToZero() {
        assertEquals("00:00", formatTime(-5_000))
    }
}
