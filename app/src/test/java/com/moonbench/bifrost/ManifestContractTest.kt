package com.moonbench.bifrost

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Validate the merged, packaged component surface, including library contributions. */
class ManifestContractTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(requireNotNull(System.getProperty("runelight.mergedManifest"))))
    private fun elements(tag: String): List<Element> = manifest.getElementsByTagName(tag).let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
    }
    private fun Element.attr(name: String) = getAttributeNS(android, name)
    private fun owned(tag: String) = elements(tag).map { it.attr("name") }
        .filter { it.startsWith("com.moonbench.bifrost.") }.toSet()

    @Test fun onlyNativeControlsAndExplicitDebugProbesAreRegistered() {
        val probes = System.getProperty("runelight.debugProbes").toBoolean()
        val activities = setOf("com.moonbench.bifrost.RunelightActivity") + if (probes) setOf(
            "com.moonbench.bifrost.probe.CaptureProbeActivity",
            "com.moonbench.bifrost.probe.ProbeFixtureActivity") else emptySet()
        val services = setOf("com.moonbench.bifrost.services.LEDService",
            "com.moonbench.bifrost.services.BifrostTileService") + if (probes) setOf(
            "com.moonbench.bifrost.probe.CaptureProbeService",
            "com.moonbench.bifrost.probe.ProbeAccessibilityService") else emptySet()
        assertEquals(activities, owned("activity"))
        assertEquals(services, owned("service"))
        assertTrue(owned("receiver").isEmpty())
        assertTrue(elements("queries").isEmpty())
    }

    @Test fun permissionsAreLimitedToLightingAndUserConsentedCapture() {
        assertEquals(setOf("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "FOREGROUND_SERVICE_SPECIAL_USE", "POST_NOTIFICATIONS", "RECORD_AUDIO"),
            elements("uses-permission").map { it.attr("name") }
                .filter { it.startsWith("android.permission.") }
                .map { it.removePrefix("android.permission.") }.toSet())
    }

    @Test fun lightingIsPrivateAndExistingQuickTileKeepsItsIdentityAndBinding() {
        val service = elements("service").single { it.attr("name").endsWith(".services.LEDService") }
        assertEquals("false", service.attr("exported"))
        assertEquals(setOf("mediaProjection", "specialUse"), service.attr("foregroundServiceType").split('|').toSet())
        val tile = elements("service").single { it.attr("name").endsWith(".services.BifrostTileService") }
        assertEquals("true", tile.attr("exported"))
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tile.attr("permission"))
        val metadata = tile.getElementsByTagName("meta-data")
        assertTrue((0 until metadata.length).map { metadata.item(it) as Element }.any {
            it.attr("name") == "android.service.quicksettings.ACTIVE_TILE" && it.attr("value") == "true"
        })
        val app = elements("application").single()
        assertEquals("false", app.attr("allowBackup"))
        assertFalse(app.attr("usesCleartextTraffic").toBoolean())
    }
}
