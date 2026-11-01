package br.com.widgetstudio

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
import androidx.test.uiautomator.*
import android.content.Intent

@Suppress("DEPRECATION")
class WidgetTests {
    val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    @Test fun testUpdateReleaseStates() {
        val fixture="""{"tag_name":"v1.0.2","draft":false,"prerelease":false,"assets":[{"name":"Widget-Studio.apk","state":"uploaded","size":100,"browser_download_url":"https://github.com/jvsleiman/widget-studio/releases/download/v1.0.2/Widget-Studio.apk"}]}"""
        assertTrue(AppUpdates.parseRelease(fixture,"1.0.1") is AppUpdates.Status.Available)
        assertEquals(AppUpdates.Status.Current,AppUpdates.parseRelease(fixture,"1.0.2"))
        assertEquals(AppUpdates.Status.Current,AppUpdates.parseRelease(fixture,"2.0.0"))
        assertEquals(AppUpdates.Status.NoRelease,AppUpdates.parseRelease(fixture.replace("\"draft\":false","\"draft\":true"),"1.0.1"))
        assertTrue(AppUpdates.parseRelease(fixture.replace("github.com","example.com"),"1.0.1") is AppUpdates.Status.Failed)
        assertTrue(AppUpdates.parseRelease("""{"tag_name":"v1.0.2","assets":[]}""","1.0.1") is AppUpdates.Status.Failed)
    }
    @Test fun testUpdateButtonIsAvailableInSettings() {
        val c=instrumentation.targetContext; val device=UiDevice.getInstance(instrumentation)
        Store(c).prefs.edit().putBoolean("welcomed",true).commit()
        c.startActivity(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("Configurações")),15000))
        device.findObject(By.text("Configurações")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Verificar atualizações")),5000))
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/widget-studio-updates.png").use {descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {it.readBytes()}}
    }
    @Test fun testPersistenceAndIndependentInstances() {
        val c=instrumentation.targetContext; val s=Store(c)
        val a=Design(name="Teste A",text="Nota independente A",bg="#E8DDF7")
        val b=a.copy(id=UUID.randomUUID().toString(),name="Teste B",text="Nota B",bg="#D8E7DD")
        s.save(a); s.save(b); s.bind(99001,a); s.bind(99002,b)
        assertEquals("Nota independente A",Store(c).bound(99001)!!.text)
        assertEquals("Nota B",Store(c).bound(99002)!!.text)
        s.bind(99001,a.copy(text="Alterada"))
        assertEquals("Nota B",Store(c).bound(99002)!!.text)
        assertTrue(Store(c).all().any {it.id==a.id})
        s.remove(a);s.remove(b);s.prefs.edit().remove("bound_99001").remove("bound_99002").commit()
    }
    @Test fun testAllRenderersAtSupportedSizes() {
        val c=instrumentation.targetContext
        for(kind in kinds) for((w,h) in listOf(220 to 220,600 to 280,600 to 680)) {
            val bitmap=Rendering.render(c,Design(kind=kind,text="Texto longo ".repeat(100)),w,h)
            assertEquals(w,bitmap.width); assertEquals(h,bitmap.height); bitmap.recycle()
        }
    }
    @Test fun testRealWidgetBindingAndUpdates() {
        val c=instrumentation.targetContext
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            val host=AppWidgetHost(c,2026); val manager=AppWidgetManager.getInstance(c)
            for(kind in listOf("Notas","Relógio","Fotos","Bateria")) {
                val id=host.allocateAppWidgetId()
                try {
                    assertTrue(manager.bindAppWidgetIdIfAllowed(id,ComponentName(c,StudioProvider::class.java)))
                    val fixture=android.graphics.Bitmap.createBitmap(640,480,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(103,140,119))}
                    val file=java.io.File(c.filesDir,"test-photo.jpg")
                    file.outputStream().use {fixture.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it)};fixture.recycle()
                    Store(c).bind(id,Design(kind=kind,name="Teste $kind",photo=if(kind=="Fotos") file.name else ""))
                    manager.updateAppWidgetOptions(id,Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,160);putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,160) })
                    StudioProvider.update(c,id)
                    assertNotNull(manager.getAppWidgetInfo(id))
                    assertNotNull(StudioProvider.action(c,Store(c).bound(id)!!,id))
                } finally {host.deleteAppWidgetId(id)}
            }
        } finally {instrumentation.uiAutomation.dropShellPermissionIdentity()}
    }
    @Test fun testPinNoteFromAppToLauncher() {
        val c=instrumentation.targetContext; val device=UiDevice.getInstance(instrumentation)
        val store=Store(c);store.prefs.edit().putBoolean("welcomed",true).commit()
        store.save(Design(name="Nota no launcher",text="Widget real · teste local"))
        c.startActivity(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("Meus widgets")),15000))
        device.findObject(By.text("Meus widgets")).click()
        val scroll=UiScrollable(UiSelector().scrollable(true))
        scroll.scrollTextIntoView("Adicionar")
        device.findObject(By.text("Adicionar")).click()
        device.wait(Until.hasObject(By.text("Adicionar à tela inicial")),5000)
        device.findObject(By.text("Adicionar à tela inicial")).click()
        device.waitForIdle()
        device.dumpWindowHierarchy(java.io.File(c.filesDir,"pin-dialog.xml"))
        device.takeScreenshot(java.io.File(c.filesDir,"pin-dialog.png"))
        val confirm=device.wait(Until.findObject(By.textContains("Add to home screen")),10000)
            ?: device.findObject(By.text("ADD AUTOMATICALLY"))
            ?: device.findObject(By.text("Add automatically"))
            ?: device.findObject(By.text("Adicionar automaticamente"))
        assertNotNull("Confirmação do launcher não encontrada; confira pin-dialog.xml",confirm)
        requireNotNull(confirm).click()
        device.pressHome();device.waitForIdle()
        assertTrue("Callback de instalação deve persistir a configuração",(0..30).any {
            if(StudioProvider.ids(c).any { id->store.bound(id)?.name=="Nota no launcher" }) true
            else {Thread.sleep(300);false}
        })
        device.takeScreenshot(java.io.File(c.filesDir,"launcher-note.png"))
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/widget-studio-launcher.png").use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {it.readBytes()} }
    }
}
