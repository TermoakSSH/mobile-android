package com.termoak.app.data

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AI approvals of server 0.6: previews and decisions with an edit or a reason. */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
@Config(sdk = [34], application = Application::class)
class ApprovalPreviewTest {
    private val task = """{"id":"t","pending_approvals":[
        {"id":"a1","tool":"run_command","preview":{"kind":"command","host":"web-1","command":"sudo systemctl restart nginx",
          "risk":"medium","reasons":[{"code":"sudo","text":"runs as root"},{"code":"service","text":"service"}],"editable":true}},
        {"id":"a2","tool":"write_file","preview":{"kind":"file","path":"/etc/app.conf","diff":"@@ -1 +1 @@\n-a\n+b","added":1,"removed":1,
          "new_file":false,"risk":"high","diff_truncated":true}},
        {"id":"a3","tool":"plan","preview":{"kind":"plan","plan":"1. Check\n2. Fix","risk":"low","editable":true}},
        {"id":"old","tool":"run_command"}]}"""

    @Test
    fun previews() {
        val p = ApprovalPreview.byApproval(task)
        assertEquals(setOf("a1", "a2", "a3"), p.keys)
        val cmd = p.getValue("a1")
        assertEquals("web-1", cmd.host)
        assertEquals("medium", cmd.risk)
        assertEquals(listOf("sudo", "service"), cmd.reasons.map { it.code })
        assertEquals("sudo systemctl restart nginx", cmd.editableText)
        val file = p.getValue("a2")
        assertEquals("/etc/app.conf", file.path)
        assertEquals(1, file.added)
        assertTrue(file.diffTruncated)
        assertFalse(file.editable)
        assertTrue(p.getValue("a3").isPlan)
        assertEquals("1. Check\n2. Fix", p.getValue("a3").editableText)
        // GET /ai/approvals is a plain array.
        assertEquals(setOf("x"), ApprovalPreview.byApproval("""[{"id":"x","preview":{"kind":"other","risk":"low"}}]""").keys)
        assertEquals(emptyMap<String, ApprovalPreview>(), ApprovalPreview.byApproval("not json"))
        assertNull(ApprovalPreview.parse(null))
    }

    @Test
    fun decisions() {
        val plain = JSONObject(ApprovalPreview.decisionBody(ApprovalDecision(approve = true, always = true)))
        assertTrue(plain.getBoolean("approve"))
        assertTrue(plain.getBoolean("always"))
        assertFalse(plain.has("edited"))
        assertFalse(plain.has("reason"))
        val edited = JSONObject(ApprovalPreview.decisionBody(ApprovalDecision(approve = true, edited = "ls -la")))
        assertEquals("ls -la", edited.getString("edited"))
        val denied = JSONObject(ApprovalPreview.decisionBody(ApprovalDecision(approve = false, reason = "  not on prod  ")))
        assertFalse(denied.getBoolean("approve"))
        assertEquals("not on prod", denied.getString("reason"))
        assertFalse(JSONObject(ApprovalPreview.decisionBody(ApprovalDecision(approve = false, reason = "  "))).has("reason"))
    }
}
