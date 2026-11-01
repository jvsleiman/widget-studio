package br.com.widgetstudio

import org.junit.Test
import org.junit.Assert.*

class AppUpdatesTest {
    @Test fun normalizesInitialVersion() {assertEquals(AppUpdates.Version.parse("1.0.0"),AppUpdates.Version.parse("v1.0"))}
    @Test fun comparesNumbersRatherThanText() {assertTrue(AppUpdates.Version.parse("1.10.0")!!>AppUpdates.Version.parse("1.9.9")!!)}
    @Test fun detectsPatchUpdate() {assertTrue(AppUpdates.Version.parse("v1.0.2")!!>AppUpdates.Version.parse("1.0.1")!!)}
    @Test fun rejectsUnsupportedTags() {listOf("latest","v1.0.2-beta","1.2.3.4","-1.0.0","9999999999.0").forEach {assertNull(AppUpdates.Version.parse(it))}}
    @Test fun acceptsOnlyOfficialAsset() {assertTrue(AppUpdates.validDownload("https://github.com/jvsleiman/widget-studio/releases/download/v1.0.2/Widget-Studio.apk","v1.0.2"))}
    @Test fun rejectsForeignAssets() {
        listOf("https://example.org/update.apk","http://github.com/jvsleiman/widget-studio/releases/download/v1.0.2/Widget-Studio.apk","https://github.com/another/repo/releases/download/v1.0.2/Widget-Studio.apk","https://github.com/jvsleiman/widget-studio/releases/download/v1.0.2/Widget-Studio.apk?redirect=other").forEach {assertFalse(AppUpdates.validDownload(it,"v1.0.2"))}
    }
}
