package br.com.widgetstudio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val kinds = listOf("Notas", "Relógio", "Bateria", "Fotos", "Calendário", "Clima", "Atalhos", "Pesquisa")
data class Design(
    val id: String = UUID.randomUUID().toString(), val kind: String = "Notas", val name: String = "Minha nota",
    val text: String = "Um dia de cada vez.", val bg: String = "#E8DDF7", val fg: String = "#262136",
    val end: String = "#BCCFEA", val gradient: Boolean = false, val photo: String = "", val album: String = "",
    val size: Int = 1, val font: Float = 24f, val margin: Float = 18f, val radius: Float = 28f,
    val center: Boolean = false, val analog: Boolean = false, val city: String = "São Paulo",
    val lat: String = "-23.55", val lon: String = "-46.63", val url: String = "https://www.google.com",
    val app: String = "", val events: Boolean = false, val interval: Int = 60
) {
    fun json() = JSONObject().apply {
        put("id",id); put("kind",kind); put("name",name); put("text",text); put("bg",bg); put("fg",fg); put("end",end)
        put("gradient",gradient); put("photo",photo); put("album",album); put("size",size); put("font",font); put("margin",margin)
        put("radius",radius); put("center",center); put("analog",analog); put("city",city); put("lat",lat); put("lon",lon)
        put("url",url); put("app",app); put("events",events); put("interval",interval)
    }
    companion object {
        fun from(j: JSONObject): Design {
            require(j.optString("kind") in kinds) { "Categoria inválida" }
            fun color(k: String, fallback: String) = j.optString(k,fallback).also { android.graphics.Color.parseColor(it) }
            return Design(id=j.optString("id",UUID.randomUUID().toString()),kind=j.getString("kind"),name=j.optString("name","Widget").take(100),
                text=j.optString("text").take(4000),bg=color("bg","#E8DDF7"),fg=color("fg","#262136"),end=color("end","#BCCFEA"),
                gradient=j.optBoolean("gradient"),photo=j.optString("photo"),album=j.optString("album"),size=j.optInt("size",1).coerceIn(0,2),
                font=j.optDouble("font",24.0).toFloat().coerceIn(12f,40f),margin=j.optDouble("margin",18.0).toFloat().coerceIn(8f,32f),
                radius=j.optDouble("radius",28.0).toFloat().coerceIn(0f,40f),center=j.optBoolean("center"),analog=j.optBoolean("analog"),
                city=j.optString("city","São Paulo"),lat=j.optString("lat","-23.55"),lon=j.optString("lon","-46.63"),
                url=j.optString("url","https://www.google.com"),app=j.optString("app"),events=j.optBoolean("events"),interval=j.optInt("interval",60).coerceIn(30,1440))
        }
    }
}
class Store(val c: Context) {
    val prefs = c.getSharedPreferences("studio",Context.MODE_PRIVATE)
    fun all(): List<Design> = runCatching { val a=JSONArray(prefs.getString("designs","[]")); (0 until a.length()).map { Design.from(a.getJSONObject(it)) } }.getOrDefault(emptyList())
    fun write(items: List<Design>) { check(prefs.edit().putString("designs",JSONArray(items.map { it.json() }).toString()).commit()) }
    fun save(d: Design) = write(all().filterNot { it.id==d.id }+d)
    fun remove(d: Design) = write(all().filterNot { it.id==d.id })
    fun bound(id: Int): Design? = prefs.getString("bound_$id",null)?.let { runCatching { Design.from(JSONObject(it)) }.getOrNull() }
    fun bind(id: Int,d: Design) { prefs.edit().putString("bound_$id",d.json().toString()).commit() }
}
