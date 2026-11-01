package br.com.widgetstudio

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.work.*
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class StudioProvider: AppWidgetProvider() {
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray) { ids.forEach { update(c,it) }; schedule(c) }
    override fun onAppWidgetOptionsChanged(c:Context,m:AppWidgetManager,id:Int,b:Bundle) { update(c,id) }
    override fun onDeleted(c:Context,ids:IntArray) { ids.forEach { Store(c).prefs.edit().remove("bound_$it").apply() } }
    override fun onRestored(c:Context,old:IntArray,new:IntArray) { old.zip(new).forEach { (a,b) -> Store(c).bound(a)?.let { Store(c).bind(b,it); update(c,b) } } }
    companion object {
        fun schedule(c:Context) { val interval=Store(c).prefs.getInt("refreshMinutes",30).toLong(); WorkManager.getInstance(c).enqueueUniquePeriodicWork("widget-refresh",ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<RefreshWorker>(interval,TimeUnit.MINUTES).build()) }
        fun ids(c:Context)=AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,StudioProvider::class.java))
        fun all(c:Context) { ids(c).forEach { update(c,it) }; schedule(c) }
        fun update(c:Context,id:Int) {
            val d=Store(c).bound(id)?:return; val m=AppWidgetManager.getInstance(c); val opts=m.getAppWidgetOptions(id)
            val minW=opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,160).coerceIn(110,600)
            val maxW=opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,minW).coerceIn(110,600)
            val minH=opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,160).coerceIn(110,600)
            val maxH=opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,minH).coerceIn(110,600)
            m.updateAppWidget(id,RemoteViews(views(c,id,d,maxW,minH),views(c,id,d,minW,maxH)))
        }
        private fun views(c:Context,id:Int,d:Design,width:Int,height:Int):RemoteViews {
            val rv=RemoteViews(c.packageName,R.layout.widget)
            val rendered=Rendering.render(c,if(d.kind=="Relógio"&&!d.analog) d.copy(kind="Fundo") else d,width*2,height*2)
            val metrics=c.resources.displayMetrics
            // Budget both orientation bitmaps below the launcher's 1.5-screen pixel allowance.
            val budget=(metrics.widthPixels.toLong()*metrics.heightPixels*.65).toLong().coerceAtLeast(16000)
            val factor=kotlin.math.sqrt(budget.toDouble()/(rendered.width.toLong()*rendered.height)).coerceAtMost(1.0)
            val art=if(factor<1.0) android.graphics.Bitmap.createScaledBitmap(rendered,(rendered.width*factor).toInt().coerceAtLeast(1),(rendered.height*factor).toInt().coerceAtLeast(1),true).also {rendered.recycle()} else rendered
            rv.setImageViewBitmap(R.id.art,art)
            rv.setContentDescription(R.id.art,"${d.kind}: ${d.name}. ${if(d.kind=="Notas") d.text else ""} Toque para abrir.")
            if(d.kind=="Relógio"&&!d.analog) {
                rv.setViewVisibility(R.id.clock,View.VISIBLE); rv.setTextColor(R.id.clock,android.graphics.Color.parseColor(d.fg))
                rv.setTextViewTextSize(R.id.clock,android.util.TypedValue.COMPLEX_UNIT_SP,d.font+12)
                rv.setCharSequence(R.id.clock,"setFormat24Hour","HH:mm\ndd MMM"); rv.setCharSequence(R.id.clock,"setFormat12Hour","hh:mm a\ndd MMM")
            }
            val action=action(c,d,id)
            rv.setOnClickPendingIntent(R.id.root,PendingIntent.getActivity(c,id,action,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return rv
        }
        fun action(c:Context,d:Design,id:Int):Intent {
            val edit=Intent(c,MainActivity::class.java).putExtra("edit_widget",id).setData(Uri.parse("studio://edit/$id"))
            return when(d.kind) {
                "Atalhos" -> if(d.app.isNotBlank()) c.packageManager.getLaunchIntentForPackage(d.app)?:edit else safeUri(d.url)?:edit
                "Pesquisa" -> safeUri(d.url)?:edit
                "Calendário" -> Intent(Intent.ACTION_VIEW,Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"))
                else -> edit
            }.let { if(it.resolveActivity(c.packageManager)!=null) it else edit }
        }
        fun safeUri(url:String):Intent? = runCatching { val u=Uri.parse(url); if(u.scheme in listOf("https","http","geo")) Intent(Intent.ACTION_VIEW,u) else null }.getOrNull()
    }
}
class PinReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        val id=i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); val raw=i.getStringExtra("design")?:return
        if(id!=-1) { Store(c).bind(id,Design.from(JSONObject(raw))); StudioProvider.update(c,id) }
    }
}
class RefreshWorker(c:Context,p:WorkerParameters):Worker(c,p) {
    override fun doWork():Result {
        val c=applicationContext; val designs=StudioProvider.ids(c).toList().mapNotNull { Store(c).bound(it) }.filter { it.kind=="Clima" }
        var failed=false
        designs.distinctBy { "${it.lat}_${it.lon}" }.forEach { if(!Weather.fetch(c,it)) failed=true }
        StudioProvider.all(c)
        return if(failed) Result.retry() else Result.success()
    }
}
object Weather {
    data class Place(val name:String,val lat:String,val lon:String)
    fun findCity(name:String):List<Place> {
        require(name.isNotBlank())
        val query=java.net.URLEncoder.encode(name,"UTF-8")
        val conn=java.net.URL("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=5&language=pt&format=json").openConnection() as java.net.HttpURLConnection
        conn.connectTimeout=10000;conn.readTimeout=10000
        return try {
            val results=JSONObject(conn.inputStream.bufferedReader().use {it.readText()}).optJSONArray("results")?:return emptyList()
            (0 until results.length()).map {n -> val p=results.getJSONObject(n);Place("${p.getString("name")}, ${p.optString("admin1")} · ${p.optString("country")}",p.getDouble("latitude").toString(),p.getDouble("longitude").toString())}
        } finally {conn.disconnect()}
    }
    fun fetch(c:Context,d:Design):Boolean = runCatching {
        val lat=d.lat.toDouble(); val lon=d.lon.toDouble(); require(lat in -90.0..90.0 && lon in -180.0..180.0)
        val base=Store(c).prefs.getString("weatherBase","https://api.open-meteo.com")!!.trimEnd('/')
        val u=java.net.URI(base); require(u.scheme=="https" && u.host!=null)
        val conn=java.net.URL("$base/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1").openConnection() as java.net.HttpURLConnection
        conn.connectTimeout=10000; conn.readTimeout=10000
        try {
            val j=JSONObject(conn.inputStream.bufferedReader().use { it.readText() }); val t=j.getJSONObject("current").getDouble("temperature_2m")
            val daily=j.getJSONObject("daily"); val time=java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            val date=java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
            Store(c).prefs.edit().putString("weather_${d.lat}_${d.lon}","${t}°C\nHoje ${daily.getJSONArray("temperature_2m_min").getDouble(0)}° / ${daily.getJSONArray("temperature_2m_max").getDouble(0)}°\nAtualizado $date às $time").putBoolean("weather_error_${d.lat}_${d.lon}",false).apply()
        } finally { conn.disconnect() }; true
    }.getOrElse { Store(c).prefs.edit().putBoolean("weather_error_${d.lat}_${d.lon}",true).apply(); false }
}
