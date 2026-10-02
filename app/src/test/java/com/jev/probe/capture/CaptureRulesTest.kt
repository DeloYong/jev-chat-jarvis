package com.jev.probe.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure decisions behind the capture service's failure paths.
 *
 * Every case here used to be a silent dead end: the manual "分析当前对话" tap did
 * nothing at all when no snapshot had been read, and the manual "截屏识别一次"
 * answered with one vague toast covering three unrelated causes. WeChat hits both
 * because it strips the node text its adapter matches on.
 */
class CaptureRulesTest {

    private val own = "com.jev.probe"

    // --------------------------------------------- foregrounds we must ignore

    @Test fun ownAppSystemUiAndLaunchersAreExcluded() {
        assertTrue(CaptureRules.isExcludedForeground(own, own))
        assertTrue(CaptureRules.isExcludedForeground("com.android.systemui", own))
        assertTrue(CaptureRules.isExcludedForeground("com.miui.home", own))
        assertTrue(CaptureRules.isExcludedForeground("com.android.launcher3", own))
        assertTrue(CaptureRules.isExcludedForeground("com.miui.launcher", own))
    }

    @Test fun chatAppsAreNotExcluded() {
        assertFalse(CaptureRules.isExcludedForeground("com.tencent.mm", own))
        assertFalse(CaptureRules.isExcludedForeground("com.tencent.mobileqq", own))
        assertFalse(CaptureRules.isExcludedForeground("com.ss.android.lark", own))
        assertFalse(CaptureRules.isExcludedForeground("com.x.android", own))
    }

    @Test fun unknownPackageCountsAsExcluded() {
        assertTrue(CaptureRules.isExcludedForeground(null, own))
        assertTrue(CaptureRules.isExcludedForeground("", own))
    }

    /** An unadapted app is NOT excluded: the manual menu is its only way in. */
    @Test fun unadaptedAppsAreNotExcluded() {
        assertFalse(CaptureRules.isExcludedForeground("com.alibaba.android.rimet", own))
        assertFalse(CaptureRules.isExcludedForeground("org.telegram.messenger", own))
    }

    // --------------------------------------- why a manual analyze tap did nothing

    @Test fun disabledMasterSwitchWins() {
        assertEquals(ManualBlock.DISABLED,
            CaptureRules.manualBlock(hasSnapshot = true, analyzing = false, enabled = false))
    }

    @Test fun analysisInFlightIsBusyNotMissing() {
        assertEquals(ManualBlock.BUSY,
            CaptureRules.manualBlock(hasSnapshot = true, analyzing = true, enabled = true))
    }

    @Test fun nothingReadYetIsReportedAsNoSnapshot() {
        assertEquals(ManualBlock.NO_SNAPSHOT,
            CaptureRules.manualBlock(hasSnapshot = false, analyzing = false, enabled = true))
    }

    @Test fun readyToRunHasNoBlocker() {
        assertEquals(ManualBlock.NONE,
            CaptureRules.manualBlock(hasSnapshot = true, analyzing = false, enabled = true))
    }

    @Test fun everyBlockerCarriesItsOwnActionableMessage() {
        assertNull(ManualBlock.NONE.message)
        val blockers = listOf(ManualBlock.DISABLED, ManualBlock.BUSY, ManualBlock.NO_SNAPSHOT)
        blockers.forEach { b ->
            val m = b.message
            assertTrue("$b 没有可用文案", m != null && m.isNotBlank())
        }
        assertEquals("messages must not be interchangeable",
            3, blockers.map { it.message }.toSet().size)
    }

    /** The one answer for a user stuck in WeChat: point at the other menu item. */
    @Test fun noSnapshotMessagePointsAtManualCapture() {
        val m = ManualBlock.NO_SNAPSHOT.message
        assertTrue("$m", m != null && m.contains("截屏识别一次"))
    }

    // ------------------------------------------------- the logcat id inventory

    @Test fun inventoryDedupesSortsAndDropsBlanks() {
        val out = CaptureRules.idInventory(listOf(
            "com.tencent.mm:id/bkl", null, "", "com.tencent.mm:id/bkl", "com.tencent.mm:id/aur"))
        assertEquals("2 个不同 id：com.tencent.mm:id/aur, com.tencent.mm:id/bkl", out)
    }

    @Test fun inventoryBoundsTheSampleButKeepsTheTrueCount() {
        val many = (1..500).map { "pkg:id/v%03d".format(it) }
        val out = CaptureRules.idInventory(many, limit = 5)
        assertTrue(out, out.startsWith("500 个不同 id（前 5）："))
        assertTrue(out, out.endsWith("…"))
        assertTrue(out, out.contains("pkg:id/v001"))
        assertFalse(out, out.contains("pkg:id/v006"))
    }

    @Test fun inventoryOfSmallSetIsNotMarkedTruncated() {
        assertFalse(CaptureRules.idInventory(listOf("a:id/x", "a:id/y")).contains("…"))
    }

    /** Zero ids is itself the diagnosis: the tree exposes no resource ids at all. */
    @Test fun emptyInventorySaysSoExplicitly() {
        assertEquals("0 个不同 id（树里没有带 id 的节点）", CaptureRules.idInventory(emptyList()))
        assertEquals("0 个不同 id（树里没有带 id 的节点）",
            CaptureRules.idInventory(listOf(null, "", null)))
    }

    @Test fun inventoryNeverCarriesMessageText() {
        // Only resource ids go in; the caller walks viewIdResourceName, never text.
        val out = CaptureRules.idInventory(listOf("com.tencent.mm:id/bkl"))
        assertFalse(out, out.contains("null"))
    }
}
