package br.com.widgetstudio

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONArray
import java.util.UUID

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); setResult(RESULT_CANCELED)
        setContent { Studio() }
    }
    @Composable fun Studio() {
        val store=remember { Store(this) }
        var items by remember { mutableStateOf(store.all()) }
        var theme by remember { mutableStateOf(store.prefs.getString("theme","Sistema")!!) }
        val dark=theme=="Escuro" || theme=="Sistema"&&isSystemInDarkTheme()
        var tab by remember { mutableStateOf("Galeria") }
        val configure=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        var installed by remember {mutableStateOf(intent.getIntExtra("edit_widget",-1))}
        var edit by remember { mutableStateOf(store.bound(installed)) }
        var welcome by remember { mutableStateOf(!store.prefs.getBoolean("welcomed",false) && configure==-1 && installed==-1) }
        var confirmation by remember { mutableStateOf<Design?>(null) }
        var message by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Todos") }
        var filterSize by remember { mutableStateOf(1) }
        var chooseApp by remember {mutableStateOf(false)}
        var cityResults by remember {mutableStateOf<List<Weather.Place>>(emptyList())}
        var checkingUpdate by remember {mutableStateOf(false)}
        var availableUpdate by remember {mutableStateOf<AppUpdates.Release?>(null)}
        val appVersion=remember {packageManager.getPackageInfo(packageName,0).versionName?:"1.0.1"}
        val photos=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
            if(uris.isNotEmpty()) runCatching {
                val paths=uris.map { uri ->
                    val file=java.io.File(filesDir,"photo-${UUID.randomUUID()}.jpg")
                    // Decode a bounded image once; widgets never pass full-resolution photos to the launcher.
                    val source=android.graphics.ImageDecoder.createSource(contentResolver,uri)
                    val bitmap=android.graphics.ImageDecoder.decodeBitmap(source) { decoder,info,_ ->
                        val factor=minOf(1f,1200f/maxOf(info.size.width,info.size.height)); decoder.setTargetSize((info.size.width*factor).toInt().coerceAtLeast(1),(info.size.height*factor).toInt().coerceAtLeast(1)); decoder.allocator=android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it) }; bitmap.recycle(); file.name
                }
                edit=edit?.copy(photo=paths.first(),album=paths.joinToString("|"))
            }.onFailure { message="Não foi possível ler a foto selecionada." }
        }
        val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            edit=edit?.copy(events=granted); if(!granted) message="Sem permissão: o calendário continuará mostrando o mês."
        }
        val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { runCatching { contentResolver.openOutputStream(it)?.use { stream -> stream.write(JSONArray(store.all().map { d -> d.json() }).toString(2).toByteArray()) }; message="Configurações exportadas. Fotos permanecem neste aparelho." }.onFailure { message="Falha na exportação." } }
        }
        val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { runCatching {
                val text=contentResolver.openInputStream(it)!!.use { input ->
                    val bytes=ByteArray(1_000_001); var count=0
                    while(count<bytes.size) {val read=input.read(bytes,count,bytes.size-count);if(read<0) break;count+=read}
                    require(count<=1_000_000);String(bytes,0,count,Charsets.UTF_8)
                }
                val a=JSONArray(text); require(a.length()<=100)
                val incoming=(0 until a.length()).map { n -> Design.from(a.getJSONObject(n)).copy(id=UUID.randomUUID().toString(),photo="",album="") }
                store.write(store.all()+incoming); items=store.all(); message="${incoming.size} configurações importadas. Selecione novamente as fotos."
            }.onFailure { message="Arquivo inválido. Nenhuma configuração foi importada." } }
        }
        fun save(d:Design) {
            store.save(d); items=store.all()
            if(installed!=-1) { store.bind(installed,d); StudioProvider.update(this,installed) }
            if(configure!=-1) { store.bind(configure,d); StudioProvider.update(this,configure); setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,configure)); finish() }
            else { edit=null; confirmation=d }; StudioProvider.schedule(this)
        }
        MaterialTheme(colorScheme=if(dark) darkColorScheme(primary=Color(0xFFCCB7FF)) else lightColorScheme(primary=Color(0xFF6244A3),background=Color(0xFFF6F4FA),surface=Color.White)) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                Column(Modifier.safeDrawingPadding().fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(22.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Column { Text("WIDGET / STUDIO",fontSize=12.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold); Text(if(edit!=null) "Seu toque pessoal" else "Pequenos espaços.\nGrandes possibilidades.",fontSize=27.sp,fontWeight=FontWeight.Bold) }
                        if(edit!=null) TextButton(onClick={ edit=null }) { Text("Voltar") }
                    }
                    if(edit==null) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("Galeria","Meus widgets","Configurações").forEach { title -> FilterChip(selected=tab==title,onClick={tab=title},label={Text(title)}) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        val d=edit
                        if(d!=null) {
                            Preview(d)
                            Text("Conteúdo",fontWeight=FontWeight.Bold,fontSize=20.sp)
                            Field("Nome",d.name) { edit=d.copy(name=it) }
                            if(d.kind=="Notas") Field("Sua nota",d.text) { edit=d.copy(text=it.take(4000)) }
                            if(d.kind=="Fotos" || d.photo.isNotBlank()) Text("${d.album.split('|').filter { it.isNotBlank() }.size} foto(s) · armazenadas no aparelho")
                            OutlinedButton(onClick={ photos.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if(d.kind=="Fotos") "Escolher fotos / álbum" else "Usar foto como fundo") }
                            if(d.photo.isNotBlank()) TextButton(onClick={edit=d.copy(photo="",album="")}) { Text("Remover fotos") }
                            if(d.kind=="Fotos") { Text("Alternância: ${d.interval} min (Android pode adiar)"); Slider(d.interval.toFloat(),onValueChange={edit=d.copy(interval=it.toInt())},valueRange=30f..1440f) }
                            if(d.kind=="Relógio") Toggle("Relógio analógico (atualização periódica)",d.analog) {edit=d.copy(analog=it)}
                            if(d.kind=="Calendário") Toggle("Mostrar próximo compromisso",d.events) { if(it) permission.launch(Manifest.permission.READ_CALENDAR) else edit=d.copy(events=false) }
                            if(d.kind=="Clima") {
                                Field("Cidade (nome exibido)",d.city) {edit=d.copy(city=it)}
                                OutlinedButton(onClick={
                                    message="Buscando cidades…"
                                    Thread { val result=runCatching {Weather.findCity(d.city)}; runOnUiThread {
                                        cityResults=result.getOrDefault(emptyList());message=if(result.isFailure) "Falha ao buscar cidade. Verifique a conexão ou informe coordenadas." else if(cityResults.isEmpty()) "Nenhuma cidade encontrada." else ""
                                    } }.start()
                                }) {Text("Buscar cidade pelo nome")}
                                Field("Latitude",d.lat) {edit=d.copy(lat=it)}; Field("Longitude",d.lon) {edit=d.copy(lon=it)}
                                Text("Open-Meteo · previsão real por coordenadas. Uso não comercial, sujeito aos termos do provedor.",fontSize=12.sp)
                                Button(onClick={ message="Carregando clima…"; Thread { val ok=Weather.fetch(this@MainActivity,d); runOnUiThread { message=if(ok) "Clima atualizado." else "Falha ao obter clima. Verifique conexão e coordenadas."; edit=d.copy(); StudioProvider.all(this@MainActivity) } }.start() }) {Text("Atualizar clima")}
                            }
                            if(d.kind in listOf("Atalhos","Pesquisa")) {
                                Field("Site HTTPS ou destino geo:",d.url) {edit=d.copy(url=it)}
                                if(d.kind=="Atalhos") {
                                    OutlinedButton(onClick={chooseApp=true}) {Text(if(d.app.isBlank()) "Escolher aplicativo" else "Trocar aplicativo")}
                                    if(d.app.isNotBlank()) TextButton(onClick={edit=d.copy(app="")}) {Text("Usar site ou mapa")}
                                }
                                Text("Música, mapas e serviços são atalhos; não exibem dados de contas.",fontSize=12.sp)
                            }
                            Text("Aparência",fontSize=20.sp,fontWeight=FontWeight.Bold)
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Claro","Escuro","Lavanda").forEach { s -> OutlinedButton(onClick={edit=when(s) {"Claro" -> d.copy(bg="#FAF9F5",fg="#24232A"); "Escuro" -> d.copy(bg="#24232A",fg="#FAF9F5"); else -> d.copy(bg="#E8DDF7",fg="#262136") }}) {Text(s)} } }
                            Field("Cor de fundo (#RRGGBB)",d.bg) { if(validColor(it)) edit=d.copy(bg=it) }
                            Field("Cor do texto (#RRGGBB)",d.fg) { if(validColor(it)) edit=d.copy(fg=it) }
                            Toggle("Degradê",d.gradient) {edit=d.copy(gradient=it)}
                            if(d.gradient) Field("Segunda cor",d.end) {if(validColor(it)) edit=d.copy(end=it)}
                            Toggle("Centralizar texto",d.center) {edit=d.copy(center=it)}
                            Text("Texto: ${d.font.toInt()}"); Slider(d.font,onValueChange={edit=d.copy(font=it)},valueRange=12f..40f)
                            Text("Margens"); Slider(d.margin,onValueChange={edit=d.copy(margin=it)},valueRange=8f..32f)
                            Text("Cantos"); Slider(d.radius,onValueChange={edit=d.copy(radius=it)},valueRange=0f..40f)
                            Sizes(d.size) {edit=d.copy(size=it)}
                            Text("O tamanho final é definido pelo launcher. Relógio digital usa TextClock; sua tipografia pode variar. Fotos e textos não têm animações.",fontSize=12.sp)
                            Button(onClick={save(d)},modifier=Modifier.fillMaxWidth()) {Text("Salvar widget")}
                        } else when(tab) {
                            "Galeria" -> {
                                Text("Crie um espaço que é seu.",fontSize=20.sp,fontWeight=FontWeight.Bold)
                                Text("Escolha um modelo, personalize e leve para sua tela inicial.")
                                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { (listOf("Todos")+kinds).forEach { k -> FilterChip(category==k,{category=k},label={Text(k)}) } }
                                Sizes(filterSize) {filterSize=it}
                                kinds.filter { category=="Todos" || category==it }.forEachIndexed { n,k ->
                                    val model=Design(kind=k,name=when(k) {"Notas" -> "Para lembrar"; "Atalhos" -> "Meu destino"; else -> k},size=filterSize,bg=listOf("#E8DDF7","#D8E7DD","#F4E4C8","#DCE6F7")[n%4])
                                    Card(onClick={edit=model},shape=RoundedCornerShape(28.dp)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { Preview(model); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(k,fontWeight=FontWeight.Bold); Text("Personalizar ↗",color=MaterialTheme.colorScheme.primary) } } }
                                }
                            }
                            "Meus widgets" -> {
                                Text("Na tela inicial",fontSize=22.sp,fontWeight=FontWeight.Bold)
                                StudioProvider.ids(this@MainActivity).forEach { id -> store.bound(id)?.let { instance ->
                                    OutlinedButton(onClick={installed=id;edit=instance}) {Text("Editar ${instance.name} · #$id")}
                                } }
                                Text("Sua coleção",fontSize=22.sp,fontWeight=FontWeight.Bold)
                                if(items.isEmpty()) Text("Ainda não há widgets. Escolha um modelo na galeria.")
                                items.forEach { saved -> Card(shape=RoundedCornerShape(24.dp)) { Column(Modifier.padding(16.dp)) {
                                    Text(saved.name,fontSize=20.sp,fontWeight=FontWeight.Bold); Text(saved.kind)
                                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                                        TextButton(onClick={installed=-1;edit=saved}) {Text("Editar / renomear")}
                                        TextButton(onClick={ val copy=saved.copy(id=UUID.randomUUID().toString(),name=saved.name+" (cópia)"); store.save(copy); items=store.all() }) {Text("Duplicar")}
                                        TextButton(onClick={confirmation=saved}) {Text("Adicionar")}
                                        TextButton(onClick={store.remove(saved); items=store.all()}) {Text("Excluir")}
                                    }
                                } } }
                                Text("Cada instalação tem uma cópia independente. Para alterar um widget instalado, toque nele na tela inicial; apagar um modelo da coleção preserva suas instalações.",fontSize=13.sp)
                            }
                            else -> {
                                Text("Do seu jeito",fontSize=22.sp,fontWeight=FontWeight.Bold)
                                Text("Atualizações do aplicativo",fontSize=18.sp,fontWeight=FontWeight.Bold)
                                Text("Versão $appVersion · GitHub Releases")
                                Button(enabled=!checkingUpdate,onClick={
                                    checkingUpdate=true
                                    Thread {val status=AppUpdates.check(appVersion);runOnUiThread {
                                        checkingUpdate=false
                                        when(status) {
                                            is AppUpdates.Status.Available -> availableUpdate=status.release
                                            AppUpdates.Status.Current -> message="Você já está usando a versão mais recente do Widget Studio."
                                            AppUpdates.Status.NoRelease -> message="Ainda não há uma versão pública disponível para baixar."
                                            is AppUpdates.Status.Failed -> message=status.reason
                                        }
                                    }}.start()
                                }) {if(checkingUpdate) {CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))};Text(if(checkingUpdate) "Verificando…" else "Verificar atualizações")}
                                Text("Se houver uma nova versão, você poderá baixar o APK oficial e confirmar a instalação pelo Android. Seus widgets e fotos serão preservados ao atualizar.",fontSize=13.sp)
                                listOf("Sistema","Claro","Escuro").forEach { t -> FilterChip(theme==t,{theme=t;store.prefs.edit().putString("theme",t).apply()},label={Text(t)}) }
                                Text("Atualização em segundo plano: aproximadamente 30 minutos, sujeita à economia de bateria. Relógio digital acompanha a hora pelo sistema.")
                                var refresh by remember {mutableStateOf(store.prefs.getInt("refreshMinutes",30))}
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(30,60,180).forEach { minutes -> FilterChip(refresh==minutes,{refresh=minutes;store.prefs.edit().putInt("refreshMinutes",minutes).apply();StudioProvider.schedule(this@MainActivity)},label={Text("$minutes min")})}}
                                var base by remember {mutableStateOf(store.prefs.getString("weatherBase","https://api.open-meteo.com")!!)}
                                Field("Servidor compatível Open-Meteo (HTTPS)",base) {base=it}
                                OutlinedButton(onClick={ if(runCatching { java.net.URI(base).let { it.scheme=="https"&&it.host!=null } }.getOrDefault(false)) { store.prefs.edit().putString("weatherBase",base).apply(); message="Provedor salvo." } else message="Informe um endereço HTTPS válido." }) {Text("Salvar provedor")}
                                Button(onClick={export.launch("widget-studio.json")}) {Text("Exportar configurações")}
                                OutlinedButton(onClick={import.launch(arrayOf("application/json","text/plain"))}) {Text("Importar configurações")}
                                Text("Fotos não são incluídas na exportação. Sem rastreadores, sem servidor próprio. Fonte do sistema Android. Clima: Open-Meteo, dados CC BY 4.0.",fontSize=13.sp)
                                TextButton(onClick={message="Na tela inicial, mantenha pressionado um espaço vazio → Widgets → Widget Studio. Arraste, escolha um modelo e salve. Redimensione mantendo o widget pressionado."}) {Text("Como adicionar manualmente")}
                            }
                        }
                    }
                }
                if(welcome) AlertDialog(onDismissRequest={welcome=false;store.prefs.edit().putBoolean("welcomed",true).apply()},title={Text("Bem-vindo ao seu Studio")},text={Text("1. Escolha um modelo\n2. Personalize e salve\n3. Adicione à tela inicial\n\nVocê também pode adicionar pelo menu Widgets do Android.")},confirmButton={TextButton(onClick={welcome=false;store.prefs.edit().putBoolean("welcomed",true).apply()}) {Text("Começar")}})
                confirmation?.let { d -> AlertDialog(onDismissRequest={confirmation=null},title={Text("Pronto para sua tela")},text={Text("${d.name} foi salvo. Após adicionar, mantenha o widget pressionado para ajustar seu tamanho. Se a solicitação não aparecer, use: tela inicial → Widgets → Widget Studio.")},confirmButton={TextButton(onClick={
                    val manager=AppWidgetManager.getInstance(this@MainActivity)
                    if(manager.isRequestPinAppWidgetSupported) {
                        val callback=Intent(this@MainActivity,PinReceiver::class.java).putExtra("design",d.copy(id=UUID.randomUUID().toString()).json().toString())
                        val pending=PendingIntent.getBroadcast(this@MainActivity,UUID.randomUUID().hashCode(),callback,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                        manager.requestPinAppWidget(ComponentName(this@MainActivity,StudioProvider::class.java),null,pending)
                    } else message="Mantenha um espaço vazio da tela inicial pressionado → Widgets → Widget Studio."
                    confirmation=null
                }) {Text("Adicionar à tela inicial")}},dismissButton={TextButton(onClick={confirmation=null}) {Text("Depois")}}) }
                if(message.isNotBlank()) AlertDialog(onDismissRequest={message=""},text={Text(message)},confirmButton={TextButton(onClick={message=""}) {Text("OK")}})
                availableUpdate?.let { release -> AlertDialog(onDismissRequest={availableUpdate=null},title={Text("Atualização disponível")},text={Text("Widget Studio ${release.version}\n\nO APK será aberto no navegador. Após o download, abra o arquivo e confirme a atualização no Android.")},confirmButton={TextButton(onClick={
                    runCatching {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(release.downloadUrl)))}.onFailure {message="Não foi possível abrir o navegador. Acesse github.com/jvsleiman/widget-studio/releases."}
                    availableUpdate=null
                }) {Text("Baixar atualização")}},dismissButton={TextButton(onClick={availableUpdate=null}) {Text("Depois")}}) }
                if(chooseApp) AlertDialog(onDismissRequest={chooseApp=false},title={Text("Escolha um aplicativo")},text={
                    val apps=remember { packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).sortedBy {it.loadLabel(packageManager).toString()} }
                    Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {apps.forEach { app -> TextButton(onClick={edit=edit?.copy(app=app.activityInfo.packageName,name=app.loadLabel(packageManager).toString());chooseApp=false}) {Text(app.loadLabel(packageManager).toString())} } }
                },confirmButton={TextButton(onClick={chooseApp=false}) {Text("Fechar")}})
                if(cityResults.isNotEmpty()) AlertDialog(onDismissRequest={cityResults=emptyList()},title={Text("Escolha sua cidade")},text={Column(Modifier.verticalScroll(rememberScrollState())) {cityResults.forEach {place -> TextButton(onClick={edit=edit?.copy(city=place.name,lat=place.lat,lon=place.lon);cityResults=emptyList()}) {Text(place.name)} }}},confirmButton={TextButton(onClick={cityResults=emptyList()}) {Text("Cancelar")}})
            }
        }
    }
    @Composable fun Preview(d:Design) {
        // Same bounded bitmap renderer used by RemoteViews, so padding and truncation match.
        val width=if(d.size==0) 160 else 320
        val height=if(d.size==2) 320 else 160
        val bitmap=Rendering.render(this,d,width*2,height*2)
        Image(bitmap.asImageBitmap(),"Prévia de ${d.kind}",Modifier.fillMaxWidth().aspectRatio(width.toFloat()/height))
    }
    @Composable fun Field(label:String,value:String,onChange:(String)->Unit) {
        // Local draft permits typing a full color value before validation.
        var draft by remember(label,value) {mutableStateOf(value)}
        OutlinedTextField(draft,{draft=it;onChange(it)},label={Text(label)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
    }
    @Composable fun Toggle(label:String,value:Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f)); Switch(value,onChange)} }
    @Composable fun Sizes(size:Int,onChange:(Int)->Unit) {Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("Pequeno","Médio","Grande").forEachIndexed { i,s -> FilterChip(size==i,{onChange(i)},label={Text(s)})}} }
    fun validColor(s:String)=runCatching {android.graphics.Color.parseColor(s);true}.getOrDefault(false)
}
