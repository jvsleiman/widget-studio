# Widget Studio

Aplicativo Android nativo em Kotlin, com telas Jetpack Compose e widgets reais por AppWidgetProvider/RemoteViews. Android 9 ou superior (API 28). Interface em português do Brasil.

[Baixar a versão mais recente](https://github.com/jvsleiman/widget-studio/releases/latest) · [Repositório](https://github.com/jvsleiman/widget-studio)

## Atualizar o aplicativo

Abra **Configurações → Verificar atualizações**. O aplicativo consulta a última release estável pública deste repositório, compara versões numéricas e, se houver uma versão mais nova, oferece **Baixar atualização**. O navegador baixa o APK oficial e o Android pede a confirmação de instalação. Nenhuma chave GitHub ou permissão para instalação silenciosa é incluída no aplicativo. Falta de internet, limite de consultas e release sem APK possuem mensagens próprias.

### Publicar as próximas versões

1. Aumente `versionCode` e `versionName` em `app/build.gradle`. A versão atual é **1.0.1**, código **2**. Use versões numéricas e tags como `v1.0.2`.
2. Compile e teste. Para atualizar instalações existentes sem perder dados, assine o APK com a **mesma chave** da versão anterior. As releases atuais são builds de teste assinadas com a chave de desenvolvimento desta máquina; preserve seu keystore fora do Git e faça backup seguro. Não publique o keystore ou senhas. Uma compilação feita em outra máquina normalmente gera outra chave e não atualiza a instalação existente.
3. Publique o commit e a tag. Crie uma release **pública, publicada e estável**, com a tag correspondente, e anexe o APK com o nome exato **`Widget-Studio.apk`**.
4. Depois de publicar todos os arquivos, marque essa release como mais recente. O botão passará a encontrá-la pela API oficial `/releases/latest`. Releases em rascunho e pré-lançamentos não são oferecidos.

O fluxo `Android` do GitHub Actions compila e verifica mudanças, mas seus APKs temporários têm assinatura de CI e **não devem ser usados para atualizar as releases existentes**. Para automatizar releases no futuro, configure a mesma chave como segredo do repositório, sem colocá-la no código. Antes de uma distribuição pública definitiva, estabeleça uma chave de lançamento própria; mudar de chave exige uma estratégia de migração Android ou reinstalação.

## Instalar

Baixe **Widget-Studio.apk** na [página da versão mais recente](https://github.com/jvsleiman/widget-studio/releases/latest), transfira para o celular, abra-o e autorize a instalação por essa fonte quando solicitado. O APK é de teste, assinado com chave de desenvolvimento; não é uma versão para publicação na Play Store.

Abra o aplicativo, escolha um modelo, personalize, salve e toque em **Adicionar à tela inicial**. Confirme a solicitação do launcher. Alternativa: mantenha um espaço vazio da tela inicial pressionado → Widgets → Widget Studio → arraste o widget → escolha/personalize o conteúdo e salve. Mantenha pressionado o widget para redimensionar.

## Recursos

- Notas, relógio digital/analógico, bateria real, fotos/álbum local, calendário mensal e próximo compromisso autorizado, clima real, atalhos e pesquisa.
- Galeria por categoria e três proporções; coleção com edição, renomeação pelo campo Nome, duplicação e exclusão.
- Cores, degradê, foto de fundo, texto, alinhamento, margens, arredondamento e prévia pelo mesmo renderizador dos widgets.
- Configuração independente por ID de widget, persistida localmente. Editar um modelo na coleção não altera instalações existentes. Toque em um widget de notas/relógio/foto/bateria/clima para editar aquela instalação; salvar atualiza somente ela.
- Temas claro/escuro/sistema para o aplicativo; aparência personalizada por widget. Fotos copiadas para armazenamento privado com resolução limitada; sem permissão ampla de mídia.
- Exportação/importação JSON pelo seletor de arquivos do sistema. Fotos não são exportadas e precisam ser escolhidas novamente após importar. Importações inválidas não substituem dados existentes.
- Atalhos para aplicativos instalados, sites HTTPS e destinos `geo:`; serviços de música e outros serviços não simulam dados de contas.

## Compilar

Abra esta pasta no Android Studio ou use JDK 17 ou superior compatível com Gradle 9.6.1. Instale Android SDK Platform 37 e Build Tools 36.0.0 e configure `local.properties` com `sdk.dir=C\:/caminho/Android/Sdk`. A versão alvo é Android 36 e a mínima é 28; o SDK 37 é usado para compilar as bibliotecas atuais do Compose.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

APK gerado: `app/build/outputs/apk/debug/app-debug.apk`. O último comando requer aparelho/emulador conectado com depuração autorizada. O Gradle baixa as dependências de Google Maven e Maven Central. Ferramentas, caches e SDK locais não fazem parte do projeto distribuído.

Versões fixadas: Android Gradle Plugin 9.3.1, Gradle 9.6.1, plugin Compose 2.3.21, Compose BOM 2026.09.00, Activity 1.11.0 e WorkManager 2.10.5. O AGP fornece Kotlin integrado.

## Dados externos e permissões

Clima usa a API de previsão Open-Meteo sem chave no endpoint público, para uso não comercial conforme os termos do serviço. Busque a cidade pelo nome e escolha o resultado no editor ou informe latitude e longitude. A busca de nomes usa o serviço de geocodificação do Open-Meteo. Configurações permite trocar o servidor de previsão por outro endpoint HTTPS compatível com `/v1/forecast`. Não há servidor próprio nem credencial no código. Para uma distribuição comercial, providencie acesso/licenciamento adequado e implemente autenticação segura do provedor escolhido; o aplicativo não oferece campo para guardar chaves privadas.

O calendário só solicita READ_CALENDAR ao ativar compromissos. Negar a permissão mantém o calendário mensal disponível. Fotos usam o seletor do sistema/compatibilidade AndroidX. Nenhuma permissão de localização é solicitada.

## Limitações

- Launcher determina dimensões finais e aceitação da solicitação automática. A proporção no editor orienta a composição; redimensione após instalar.
- Atualizações por WorkManager são configuráveis em 30, 60 ou 180 minutos, adiáveis por Doze/economia de bateria. O provedor também recebe atualizações periódicas do Android. Bateria, fotos, calendário e relógio analógico podem ficar defasados até a próxima atualização. Relógio digital usa TextClock do sistema, com variação de fonte/layout em relação à prévia bitmap.
- Sem transparência real sobre o papel de parede, gestos, animações ou atualização contínua de bitmaps.
- Renderização bitmap limita estilos ao que o launcher pode receber e reduz a granularidade de acessibilidade. Há descrição acessível e as notas incluem o conteúdo para leitor de tela. O texto respeita escala do sistema até 200%; navegação detalhada por subáreas e contraste automático ainda precisam de melhoria.
- Cada widget possui uma única ação de toque. Calendário abre o app de agenda; atalhos/pesquisa abrem o destino; demais abrem edição.
- O calendário ajusta a altura das linhas para mostrar o mês; tamanhos baixos reduzem a fonte dos dias. Recomenda-se formato grande para legibilidade e compromissos.
- Álbum pode alternar em intervalo escolhido, mas a mudança só é aplicada quando o Android permite atualização. Fotos removidas da coleção podem permanecer no armazenamento privado; limpar dados do app remove fotos e configurações.
- Pesquisa é um botão visual que abre o site configurado; não é um campo de entrada no launcher.
- Falha de clima preserva dados anteriores identificados com data/hora. Editar apenas o nome da cidade altera seu rótulo: use Buscar cidade ou ajuste as coordenadas para mudar o local da previsão.

## Referências e licenças

- Referência visual analisada (texto e imagens de pesquisa/calendário): https://tecnoblog.net/guias/12-widgets-uteis-para-usar-no-iphone-com-ios-14/ . Nenhuma captura é incorporada ao aplicativo.
- APIs oficiais: https://developer.android.com/develop/ui/views/appwidgets e https://developer.android.com/develop/ui/views/appwidgets/advanced
- Compatibilidade AGP: https://developer.android.com/build/releases/agp-9-3-0-release-notes
- Compose: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Clima: https://open-meteo.com/en/docs e https://open-meteo.com/en/terms . Dados Open-Meteo sob CC BY 4.0, com atribuição no app.
- AndroidX/Compose/WorkManager: Apache License 2.0. Fonte é a padrão do dispositivo; ícone e cartões foram desenhados neste projeto. Fotos são fornecidas pelo usuário.

Consulte `VERIFICACAO.md` para os resultados efetivamente executados; a presença de testes no código não significa que tenham sido aprovados.
