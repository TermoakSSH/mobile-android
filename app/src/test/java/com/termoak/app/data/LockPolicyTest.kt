package com.termoak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the app lock locks again (the iOS app's rule). */
class LockPolicyTest {
    @Test
    fun rule() {
        assertFalse(LockPolicy.shouldLock(false, LockDelay.IMMEDIATELY, 0, 10))
        assertFalse(LockPolicy.shouldLock(true, LockDelay.IMMEDIATELY, null, 10))
        assertTrue(LockPolicy.shouldLock(true, LockDelay.IMMEDIATELY, 1_000, 1_000))
        assertFalse(LockPolicy.shouldLock(true, LockDelay.FIVE_MINUTES, 0, 299_999))
        assertTrue(LockPolicy.shouldLock(true, LockDelay.FIVE_MINUTES, 0, 300_000))
        assertEquals(LockDelay.HOUR, LockDelay.of(3600))
        assertEquals(LockDelay.IMMEDIATELY, LockDelay.of(42))
    }
}
