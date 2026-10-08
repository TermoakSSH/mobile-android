package com.termoak.app.data

import com.termoak.app.data.ImportPlan.Dup
import com.termoak.app.data.ImportPlan.Policy
import com.termoak.app.data.ImportPlan.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What happens to each host of an imported file, the CSV columns and the export passphrase (the iOS app's rules). */
class ImportPlanTest {
    @Test
    fun statuses() {
        assertEquals(Status.New, ImportPlan.status(Dup.None, Policy.SKIP, true))
        assertEquals(Status.Unchecked, ImportPlan.status(Dup.None, Policy.SKIP, false))
        assertEquals(Status.ExistingSkipped("web"), ImportPlan.status(Dup.Existing("web"), Policy.SKIP, true))
        assertEquals(Status.Updates("web"), ImportPlan.status(Dup.Existing("web"), Policy.UPDATE, true))
        assertEquals(Status.CopyOf("web"), ImportPlan.status(Dup.Existing("web"), Policy.COPY, true))
        assertEquals(Status.RepeatSkipped, ImportPlan.status(Dup.InFile, Policy.UPDATE, true))
        assertEquals(Status.RepeatCopy, ImportPlan.status(Dup.InFile, Policy.COPY, true))
        assertEquals(
            2,
            ImportPlan.importCount(listOf(Status.New, Status.Unchecked, Status.ExistingSkipped("a"), Status.Updates("b"), Status.RepeatSkipped)),
        )
    }

    @Test
    fun csvColumns() {
        val m = listOf("label" to 0, "address" to 1)
        assertEquals("address", ImportPlan.fieldOfColumn(1, m))
        assertNull(ImportPlan.fieldOfColumn(2, m))
        // A field moves to the new column; a column holds one field.
        assertEquals(listOf("label" to 0, "address" to 2), ImportPlan.assign("address", 2, m))
        assertEquals(listOf("user" to 0, "address" to 1), ImportPlan.assign("user", 0, m))
        assertEquals(listOf("label" to 0), ImportPlan.assign(null, 1, m))
        assertTrue(ImportPlan.hasAddress(m))
        assertFalse(ImportPlan.hasAddress(listOf("label" to 0)))
        val sample = listOf(listOf("name", "host"), listOf("", ""), listOf("web", " 10.0.0.1 "))
        assertEquals("10.0.0.1", ImportPlan.example(1, sample, hasHeader = true))
        assertEquals("name", ImportPlan.example(0, sample, hasHeader = false))
        assertNull(ImportPlan.example(5, sample, hasHeader = true))
    }

    @Test
    fun exportPassphrase() {
        assertEquals(ImportPlan.PassphraseProblem.SHORT, ImportPlan.passphraseProblem("1234567", "1234567"))
        assertEquals(ImportPlan.PassphraseProblem.MISMATCH, ImportPlan.passphraseProblem("12345678", "12345679"))
        assertNull(ImportPlan.passphraseProblem("12345678", "12345678"))
    }
}
