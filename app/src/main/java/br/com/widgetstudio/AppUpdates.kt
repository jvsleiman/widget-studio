package br.com.widgetstudio

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Only published stable releases from this repository may offer an APK. */
object AppUpdates {
    const val repository = "https://github.com/jvsleiman/widget-studio"
    const val latestApi = "https://api.github.com/repos/jvsleiman/widget-studio/releases/latest"
    const val assetName = "Widget-Studio.apk"

    data class Version(val major: Int, val minor: Int, val patch: Int): Comparable<Version> {
        override fun compareTo(other: Version) = compareValuesBy(this,other,Version::major,Version::minor,Version::patch)
        companion object {
            fun parse(raw: String): Version? {
                val match=Regex("^v?(\\d{1,6})(?:\\.(\\d{1,6}))?(?:\\.(\\d{1,6}))?$").matchEntire(raw)?:return null
                return Version(match.groupValues[1].toInt(),match.groupValues[2].ifEmpty {"0"}.toInt(),match.groupValues[3].ifEmpty {"0"}.toInt())
            }
        }
    }
    data class Release(val version:String,val downloadUrl:String)
    sealed interface Status {
        data class Available(val release:Release): Status
        data object Current: Status
        data object NoRelease: Status
        data class Failed(val reason:String): Status
    }
    fun validDownload(url:String,tag:String):Boolean = runCatching {
        val uri=URI(url)
        Version.parse(tag)!=null && uri.scheme=="https" && uri.host=="github.com" && uri.port==-1 && uri.userInfo==null && uri.query==null && uri.fragment==null &&
            uri.rawPath=="/jvsleiman/widget-studio/releases/download/$tag/$assetName"
    }.getOrDefault(false)

    fun parseRelease(body:String,installedVersion:String):Status {
        val json=JSONObject(body)
        if(json.optBoolean("draft") || json.optBoolean("prerelease")) return Status.NoRelease
        val tag=json.getString("tag_name")
        val latest=Version.parse(tag)?:return Status.Failed("A versão publicada não tem um número de versão compatível.")
        val current=Version.parse(installedVersion)?:return Status.Failed("Não foi possível identificar a versão instalada.")
        val assets=json.optJSONArray("assets")?:return Status.Failed("A publicação ainda não tem um APK disponível.")
        val asset=(0 until assets.length()).map {assets.getJSONObject(it)}.firstOrNull {it.optString("name")==assetName && it.optString("state")=="uploaded" && it.optLong("size")>0}
            ?:return Status.Failed("A publicação ainda não tem um APK disponível.")
        val download=asset.getString("browser_download_url")
        if(!validDownload(download,tag)) return Status.Failed("O endereço do APK publicado não é válido para este aplicativo.")
        return if(latest>current) Status.Available(Release(tag.removePrefix("v"),download)) else Status.Current
    }

    fun check(installedVersion:String):Status = runCatching {
        val connection=URL(latestApi).openConnection() as HttpURLConnection
        connection.connectTimeout=12000;connection.readTimeout=12000
        connection.setRequestProperty("Accept","application/vnd.github+json")
        connection.setRequestProperty("User-Agent","Widget-Studio-Android")
        try {
            when(connection.responseCode) {
                404 -> Status.NoRelease
                403,429 -> Status.Failed("O GitHub limitou as consultas. Tente novamente mais tarde.")
                200 -> {
                    val body=connection.inputStream.bufferedReader().use {reader ->
                        val buffer=CharArray(4096);val text=StringBuilder()
                        while(true) {val count=reader.read(buffer);if(count<0) break;require(text.length+count<=524288);text.append(buffer,0,count)}
                        text.toString()
                    }
                    parseRelease(body,installedVersion)
                }
                else -> Status.Failed("Não foi possível consultar as atualizações no GitHub. Tente novamente.")
            }
        } finally {connection.disconnect()}
    }.getOrElse {Status.Failed("Não foi possível verificar as atualizações. Confira sua conexão e tente novamente.")}
}
