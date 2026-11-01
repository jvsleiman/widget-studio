# Verificação — versão 1.0.1 — 31/10/2026

## Atualizações pelo GitHub Releases

- Versão 1.0.1, código Android 2. `assembleDebug`, `lintDebug`, `testDebugUnitTest` e `connectedDebugAndroidTest`: **BUILD SUCCESSFUL**.
- **6 testes unitários aprovados:** comparação numérica e normalização de versões, detecção de atualização, rejeição de tags não suportadas, aceitação do APK oficial e rejeição de endereços de terceiros.
- **6 testes no emulador Android 16 aprovados:** os quatro testes locais descritos abaixo, presença do botão em Configurações e interpretação das respostas de release (nova versão, versão atual, versão instalada superior, rascunho, APK ausente e endereço inválido).
- Lint: **0 erros, 38 avisos e 3 sugestões**. Sem aparelho físico.
- Captura da tela de atualização conferida em `docs/evidencias/atualizacoes.png`.
- Certificado SHA-256 do novo APK igual ao APK 1.0 entregue anteriormente: `bb480a188175b7d9f7e0d83237c54e7ac94d607511efd0795f2d48f2f69ac315`.
- O download de uma versão futura e a confirmação de instalação são feitos pelo navegador e pelo Android. Não há instalação silenciosa, credencial GitHub embutida ou permissão ampla de instalação no app. A publicação de versões futuras deve manter a assinatura e elevar os números de versão, conforme o README.
- Os testes de interpretação usam respostas controladas, sem depender da disponibilidade da rede. Não houve atualização de ponta a ponta entre duas releases públicas nesta rodada.

## Histórico da versão inicial 1.0

## Ambiente

Windows, Android Emulator 37.2.12, imagem Google APIs x86_64 Android 16 / API 36, aceleração WHPX e renderização SwiftShader, tela 320 × 640. Compilação com JBR/JDK 25, Gradle 9.6.1, AGP 9.3.1 e SDK de compilação 37. Sem aparelho físico conectado.

## Verificações executadas

- `assembleDebug`: APK de teste gerado e instalado no emulador. Aplicativo abriu sem exceção AndroidRuntime observada.
- `lintDebug`: aprovado, **0 erros**, 37 avisos e 3 sugestões. Não foi usada baseline para ocultar erros. Avisos incluem textos em português escritos diretamente no Compose, versões mais novas disponíveis, persistência síncrona de preferências pequenas, atributos de tamanho ignorados antes da API 31 e ausência de regras específicas de transferência de backup. A versão alvo 36 foi mantida; Android 17 ainda não foi testado.
- `connectedDebugAndroidTest`: **4 testes, 0 falhas, 0 erros, 0 ignorados**.
- Repetição direta por AndroidJUnitRunner no APK final: **OK (4 tests)**. Depois, foi encerrado o processo com `am force-stop`, reaberto o aplicativo e comparado o arquivo de configurações antes e depois: conteúdo idêntico, incluindo a nota e o vínculo instalado. Resultado em `restart-test.log`. Essa verificação cobre reinício do aplicativo, não reboot completo do Android.

### Cobertura dos testes

1. **Persistência e independência:** salvar duas notas com textos/cores diferentes, abrir outra instância do armazenamento, verificar recuperação, alterar a configuração vinculada à primeira e confirmar que a segunda permanece intacta.
2. **Renderização:** oito categorias em três dimensões, incluindo textos longos; 24 renderizações sem exceção. Isso verifica o renderizador, não substitui inspeção visual em diferentes launchers.
3. **Fluxo de instalação no launcher:** abrir o aplicativo, entrar em Meus widgets, adicionar uma nota, confirmar o diálogo real do launcher, voltar à tela inicial e verificar o ID instalado e sua configuração persistida. A captura `entrega/evidencias/launcher-note.png` mostra o widget real.
4. **App Widgets oficiais:** alocar e vincular IDs reais de notas, relógio, fotos e bateria por AppWidgetHost e AppWidgetManager; salvar configuração, atualizar RemoteViews, confirmar o vínculo e a resolução da ação de toque. A foto deste teste é um arquivo de imagem sintético local, sem internet. Os IDs temporários desse teste são removidos ao terminar.

As capturas foram conferidas visualmente. Foi corrigida uma distorção na versão inicial usando RemoteViews específicos para orientação retrato e paisagem. Os bitmaps das duas orientações têm orçamento total inferior à tolerância de 1,5 tela do launcher.

## Limites da validação

- Somente a nota passou pelo fluxo completo de confirmação no launcher com captura. Relógio, foto e bateria foram vinculados e atualizados pelas APIs oficiais no teste instrumentado; a seleção de foto pelo usuário e a instalação manual desses três pela interface não foram percorridas de ponta a ponta.
- Não houve teste físico, em outros launchers, em Android 9–15 ou Android 17, nem reinicialização completa do aparelho.
- Não foi feita validação visual individual de todas as combinações de cores, fotos, escalas de fonte ou dimensões. Cores personalizadas podem ter contraste insuficiente.
- As ações são resolvidas para intents reais e há fallback para edição; não foi validado o comportamento de cada aplicativo externo possível.
- Clima e geocodificação estão implementados com endpoints reais, mas a consulta externa dentro do aplicativo não foi validada de ponta a ponta nesta sessão. Permissões e compromissos reais de calendário também não foram testados com uma agenda preenchida.
- Não foi aguardado um ciclo de 30 minutos de WorkManager ou alternância automática de álbum, nem um ciclo de Doze. A atualização imediata de RemoteViews foi exercitada.
- Exportação/importação JSON está implementada e tem limite de tamanho e validação antes de salvar; o seletor de documentos não foi percorrido no emulador.

Os relatórios originais de Lint, JUnit e o registro final de compilação acompanham a pasta de evidências. Consulte o README para instalação, compilação e demais limitações do produto.
