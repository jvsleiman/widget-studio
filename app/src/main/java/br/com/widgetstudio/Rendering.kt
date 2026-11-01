package br.com.widgetstudio

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.os.BatteryManager
import android.provider.CalendarContract
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

object Rendering {
    val locale = Locale.forLanguageTag("pt-BR")
    fun render(c: Context,d: Design,w: Int,h: Int): Bitmap {
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); val cv=Canvas(b)
        val scale=2f; val p=Paint(3); val pad=d.margin*scale
        cv.clipPath(Path().apply { addRoundRect(RectF(0f,0f,w.toFloat(),h.toFloat()),d.radius*scale,d.radius*scale,Path.Direction.CW) })
        p.color=Color.parseColor(d.bg)
        if(d.gradient) p.shader=LinearGradient(0f,0f,w.toFloat(),h.toFloat(),Color.parseColor(d.bg),Color.parseColor(d.end),Shader.TileMode.CLAMP)
        cv.drawPaint(p); p.shader=null
        val photos=(d.album.split('|').filter { it.isNotBlank() }).ifEmpty { listOf(d.photo).filter { it.isNotBlank() } }
        if(photos.isNotEmpty()) runCatching {
            val path=photos[((System.currentTimeMillis()/(d.interval*60000L))%photos.size).toInt()]
            val f=java.io.File(c.filesDir,path); val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeFile(f.path,opts)
            opts.inSampleSize=max(1,max(opts.outWidth/w,opts.outHeight/h)); opts.inJustDecodeBounds=false
            BitmapFactory.decodeFile(f.path,opts)?.let { photo ->
                val r=max(w.toFloat()/photo.width,h.toFloat()/photo.height); val dw=photo.width*r; val dh=photo.height*r
                cv.drawBitmap(photo,null,RectF((w-dw)/2,(h-dh)/2,(w+dw)/2,(h+dh)/2),p); photo.recycle()
                if(d.kind!="Fotos") { p.color=0x66000000; cv.drawPaint(p) }
            }
        }
        val fg=Color.parseColor(d.fg)
        fun text(s:String,y:Float,size:Float,bold:Boolean=false,maxHeight:Float=h-pad-y) {
            if(maxHeight<=0) return
            val fontScale=c.resources.configuration.fontScale.coerceIn(1f,2f)
            val tp=TextPaint(3).apply { color=fg; textSize=size*scale*fontScale; typeface=if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
            val layout=StaticLayout.Builder.obtain(s,0,s.length,tp,(w-pad*2).toInt().coerceAtLeast(1))
                .setAlignment(if(d.center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(max(1,(maxHeight/(size*scale*fontScale*1.2f)).toInt())).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            cv.save(); cv.translate(pad,y); layout.draw(cv); cv.restore()
        }
        val now=LocalDateTime.now()
        when(d.kind) {
            "Notas" -> { text(d.name.uppercase(locale),pad,12f,true,24*scale); text(d.text,pad+32*scale,d.font) }
            "Relógio" -> {
                text(now.format(DateTimeFormatter.ofPattern("EEE, d MMM",locale)),pad,14f,true)
                if(d.analog) {
                    val x=w/2f; val y=h/2f+10*scale; val r=min(w-pad*2,h-pad*3)/2.8f
                    p.color=fg; p.style=Paint.Style.STROKE; p.strokeWidth=3*scale; cv.drawCircle(x,y,r,p)
                    for(i in 0..11) { val a=i*Math.PI/6; cv.drawLine(x+sin(a).toFloat()*r*.88f,y-cos(a).toFloat()*r*.88f,x+sin(a).toFloat()*r*.96f,y-cos(a).toFloat()*r*.96f,p) }
                    fun hand(v:Double,len:Float) { cv.drawLine(x,y,x+sin(v).toFloat()*r*len,y-cos(v).toFloat()*r*len,p) }
                    hand((now.hour%12+now.minute/60.0)*Math.PI/6,.5f); hand(now.minute*Math.PI/30,.8f); p.style=Paint.Style.FILL
                } else text(now.format(DateTimeFormatter.ofPattern("HH:mm")),h/2f-25*scale,d.font+12,true)
            }
            "Bateria" -> {
                val i=c.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED)); val level=i?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
                val total=i?.getIntExtra(BatteryManager.EXTRA_SCALE,100)?:100; val percent=if(level>=0&&total>0) level*100/total else -1
                text("BATERIA",pad,12f,true); text(if(percent<0) "Indisponível" else "$percent%",pad+30*scale,40f,true)
                p.color=fg; p.alpha=40; val top=h-pad-18*scale; cv.drawRoundRect(pad,top,w-pad,h-pad,8*scale,8*scale,p); p.alpha=255
                if(percent>=0) cv.drawRoundRect(pad,top,pad+(w-2*pad)*percent/100,h-pad,8*scale,8*scale,p)
            }
            "Fotos" -> if(photos.isEmpty()) text("Escolha uma foto\nno editor",pad+24*scale,22f)
            "Calendário" -> {
                val month=now.toLocalDate().withDayOfMonth(1); text(now.format(DateTimeFormatter.ofPattern("MMMM yyyy",locale)).uppercase(locale),pad,14f,true)
                val header="S   T   Q   Q   S   S   D"; text(header,pad+28*scale,13f,true)
                val cell=(w-pad*2)/7; val first=month.dayOfWeek.value-1
                val reserved=if(d.events && h>w*.9f) 50*scale else 0f
                val rowHeight=((h-2*pad-48*scale-reserved)/6).coerceAtLeast(8*scale)
                for(day in 1..month.lengthOfMonth()) {
                    val pos=first+day-1; val x=pad+(pos%7)*cell; val y=pad+54*scale+(pos/7)*rowHeight
                    p.color=fg; p.textSize=min(13*scale,rowHeight*.8f); p.typeface=if(day==now.dayOfMonth) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    if(day==now.dayOfMonth) { p.alpha=35; cv.drawCircle(x+8*scale,y-5*scale,11*scale,p); p.alpha=255 }; cv.drawText(day.toString(),x,y,p)
                }
                if(d.events && h>w*.9f) text(nextEvent(c),h-55*scale,12f)
            }
            "Clima" -> { text(d.city,pad,16f,true); val prefs=Store(c).prefs; val cached=prefs.getString("weather_${d.lat}_${d.lon}",null); val error=prefs.getBoolean("weather_error_${d.lat}_${d.lon}",false); text((cached?:"Sem dados. Toque para atualizar.")+(if(error) "\nFalha de conexão · dados anteriores" else ""),pad+35*scale,18f) }
            "Atalhos" -> { text("ATALHO",pad,12f,true); text(d.name,pad+38*scale,d.font,true); text(if(d.app.isNotBlank()) "Abrir aplicativo ↗" else "Abrir destino ↗",h-pad-24*scale,12f) }
            "Pesquisa" -> { text("PESQUISA",pad,12f,true); text("⌕  Toque para buscar",h/2f-12*scale,21f,true) }
        }
        return b
    }
    fun nextEvent(c:Context):String {
        if(c.checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED) return "Permita acesso à agenda no editor"
        return runCatching {
            val start=System.currentTimeMillis(); val uri=CalendarContract.Instances.CONTENT_URI.buildUpon().apply { ContentUris.appendId(this,start); ContentUris.appendId(this,start+7*86400000L) }.build()
            c.contentResolver.query(uri,arrayOf("title","begin"),null,null,"begin ASC")?.use { if(it.moveToFirst()) it.getString(0) else "Sem eventos nos próximos 7 dias" }?:"Agenda indisponível"
        }.getOrDefault("Agenda indisponível")
    }
}
