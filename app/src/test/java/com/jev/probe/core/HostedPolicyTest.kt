package com.jev.probe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostedPolicyTest {
    @Test fun openSourceNeedsSwitchAndToken() {
        assertFalse(HostedPolicy.cloudActive(false, enabled = false, available = true, token = "t"))
        assertFalse(HostedPolicy.cloudActive(false, enabled = true, available = true, token = ""))
        assertTrue(HostedPolicy.cloudActive(false, enabled = true, available = true, token = "t"))
    }

    @Test fun hostedOnlyIgnoresSwitchButStillNeedsToken() {
        assertTrue(HostedPolicy.cloudActive(true, enabled = false, available = true, token = "t"))
        assertFalse(HostedPolicy.cloudActive(true, enabled = false, available = true, token = " "))
        assertFalse(HostedPolicy.cloudActive(true, enabled = true, available = false, token = "t"))
    }

    @Test fun gatewayRouteIsAlwaysOnWhenHostedOnly() {
        assertTrue(HostedPolicy.gatewayRoute(hostedOnly = true, cloudActive = false))
        assertFalse(HostedPolicy.gatewayRoute(hostedOnly = false, cloudActive = false))
        assertTrue(HostedPolicy.gatewayRoute(hostedOnly = false, cloudActive = true))
    }

    @Test fun ownKeyNeverGrantsAccessInHostedOnly() {
        assertFalse(HostedPolicy.hasAccess(true, cloudActive = false, ownJudgeKey = "k"))
        assertTrue(HostedPolicy.hasAccess(false, cloudActive = false, ownJudgeKey = "k"))
        assertTrue(HostedPolicy.hasAccess(true, cloudActive = true, ownJudgeKey = ""))
        assertEquals(false, HostedPolicy.hasAccess(false, cloudActive = false, ownJudgeKey = ""))
    }
}
