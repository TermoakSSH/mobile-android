package com.termoak.app.data

import android.app.Application
import com.termoak.ffi.AiApprovalPreview
import com.termoak.ffi.AiDecision
import com.termoak.ffi.AiRiskLevel
import com.termoak.ffi.AiRiskReason
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AI approvals of server 0.6: previews (the engine's and the live events') and decisions with an edit or a reason. */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
@Config(sdk = [34], application = Application::class)
class ApprovalPreviewTest {
    private fun engine(kind: String, risk: AiRiskLevel = AiRiskLevel.LOW) = AiApprovalPreview(
        kind = kind, command = null, host = null, risk = risk, reasons = emptyList(), explanation = null, path = null, diff = null,
        added = null, removed = null, newFile = false, truncated = false, diffError = null, plan = null, editable = false,
    )

    @Test
    fun enginePreviews() {
        val cmd = ApprovalPreview.of(
            engine("command", AiRiskLevel.MEDIUM).copy(
                host = "web-1", command = "sudo systemctl restart nginx", editable = true,
                reasons = listOf(AiRiskReason("sudo", "runs as root"), AiRiskReason("service", "service")),
            ),
        )!!
        assertEquals("web-1", cmd.host)
        assertEquals("medium", cmd.risk)
        assertEquals(listOf("sudo", "service"), cmd.reasons.map { it.code })
        assertEquals("sudo systemctl restart nginx", cmd.editableText)
        val file = ApprovalPreview.of(
            engine("file", AiRiskLevel.HIGH).copy(path = "/etc/app.conf", diff = "@@ -1 +1 @@\n-a\n+b", added = 1u, removed = 1u, truncated = true),
        )!!
        assertEquals("/etc/app.conf", file.path)
        assertEquals("high", file.risk)
        assertEquals(1, file.added)
        assertTrue(file.diffTruncated)
        assertFalse(file.editable)
        val plan = ApprovalPreview.of(engine("plan").copy(plan = "1. Check\n2. Fix", editable = true))!!
        assertTrue(plan.isPlan)
        assertEquals("1. Check\n2. Fix", plan.editableText)
        // Servers before 0.6: no preview (the summary is shown).
        assertNull(ApprovalPreview.of(null))
    }

    @Test
    fun livePreviews() {
        val p = ApprovalPreview.parse(
            JSONObject("""{"kind":"file","path":"/etc/x","diff":"+b","added":1,"new_file":true,"risk":"high","diff_truncated":true}"""),
        )!!
        assertEquals("/etc/x", p.path)
        assertTrue(p.newFile)
        assertTrue(p.diffTruncated)
        assertNull(ApprovalPreview.parse(null))
    }

    @Test
    fun decisions() {
        assertEquals(AiDecision(approve = true, always = true), ApprovalDecision(approve = true, always = true).engine())
        assertEquals(AiDecision(approve = true, edited = "ls -la"), ApprovalDecision(approve = true, edited = " ls -la ").engine())
        // A denial carries no edit; empty texts are left out.
        assertEquals(AiDecision(approve = false, reason = "not on prod"), ApprovalDecision(approve = false, edited = "x", reason = "  not on prod  ").engine())
        assertEquals(AiDecision(approve = false), ApprovalDecision(approve = false, reason = "  ").engine())
    }
}
