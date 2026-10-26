# Como testar MaizoSNES em dois celulares

## Instalação

1. Abra [GitHub Actions](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions), selecione a **última execução verde** da branch `work/android-foundation` e baixe o artefato `app-debug`.
2. Extraia e instale **o mesmo APK nos dois aparelhos**. Se o Android solicitar, permita instalação desse APK de teste somente pela fonte que você confia.
3. Prepare uma ROM SNES homebrew que possa ser legalmente distribuída. Nenhum jogo está embutido no APK.
4. No anfitrião A, conecte Wi-Fi. No convidado B, use 4G/5G ou Wi-Fi de outro provedor.

## Partida online (Rollback quando possível)

1. Abra a ROM em A e entre em **Multiplayer**. Para selecionar Rollback, expanda **MOTOR → AJUSTES** e escolha **Rollback (Recomendado)**; em seguida crie uma sala online. As configurações avançadas são recolhidas por padrão para dar prioridade às salas.
2. Se a ROM for redistribuível, confirme a autorização por sessão. O cliente só receberá bytes depois de aceitar a oferta.
3. No B, selecione Multiplayer → Atualizar salas → Sala de A. Caso não possua a ROM, aceite a oferta autorizada e aguarde o progresso 0–100%, hash SHA-256 e estado inicial.
4. Verifique os estados ICE. No menu durante o jogo (GUIDE/PS/Xbox central, não Start SNES), abra STATUS DE CONEXÃO / DEBUG.
5. Se aparecer **Rollback (GekkoNet)** + **WebRTC DataChannel**, o canal P2P foi estabelecido. Se aparecer **Lockstep (MZNP TCP)** + **TCP via Railway (WSS)**, a rede exigiu fallback, mas o jogo deve continuar normalmente.
6. Jogue por 10 minutos com entradas de ambos, compare sincronização, jitter e correções. Repita com aparelho ARMv7 se possível.

## LAN / IP manual

1. Conecte os dois ao mesmo Wi-Fi; crie sala LAN no A, descubra ou digite IP:porta no B.
2. Confirme **Rollback + UDP direto** no overlay. O MZNP v7 negocia **portas UDP dinâmicas/efêmeras** após sincronizar ROM autorizada e estado inicial por TCP; não presuma TCP+1 nem a porta de descoberta 55436.
3. Simule indisponibilidade UDP (bloqueio no roteador) e confirme retorno para Lockstep TCP antes do primeiro frame.

## Diagnóstico e relatório de bug

1. Abra **STATUS / LOGS** na aba Multiplayer, **DIAGNÓSTICO DE REDE** em Ajustes ou o diagnóstico no menu do jogo.
2. Toque COMPARTILHAR DIAGNÓSTICO para enviar motor, transporte, estado ICE, RTT, jitter, correções, delay e desyncs via Android Sharesheet.
3. Faça uma captura de tela do overlay e relate: dois modelos, Android, ROM homebrew, passos, data/hora, resultado esperado e observado.
4. Opcional em computador ADB: `adb logcat -v time -s MaizoSNES:D AndroidRuntime:E *:S > maizosnes.log`.
5. Antes de compartilhar, remova dados pessoais, endereços IP públicos e códigos de acesso. Nunca envie ROMs ou tokens.

**Nota:** se NAT simétrico, operadora ou firewall bloquear WebRTC e não houver TURN configurado, somente Lockstep é garantido. CI verde não comprova sucesso de WebRTC em dispositivos físicos.


## Verificação extra: ICE checking e TURN

1. Abra Ajustes → Rede → TESTAR COLETA STUN/TURN (ICE). O teste mostra
   candidatos locais, não a conectividade P2P. Candidato 'relay/TURN' indica
   somente alocação TURN local.
2. Em duas redes diferentes, crie a sala e aguarde a conexão. Abra Status de
   conexão / Debug durante o jogo. WebRTC só está ativo quando tanto motor
   Rollback GekkoNet quanto transporte WebRTC DataChannel forem mostrados.
3. Se continuar ICE checking ou cair para TCP, inspecione logs pelo botão
   COMPARTILHAR DIAGNÓSTICO. Uma segunda tentativa relay-only pode ocorrer
   se os dois aparelhos tiverem TURN. Caso falhe: Lockstep TCP via Railway.
4. Nunca trate os dados de perda RTP como perda SCTP de DataChannel.
5. Em caso de erro, registre Android/CPU em cada aparelho, tipo de rede,
   duração, ICE state e modo efetivo. Não envie tokens nem ROMs.

Instalar/configurar Open Relay ou credenciais Metered: docs/TURN_SETUP.md.


### NOVO — testar LAN Rollback e troca de aplicativos

1. Atualize os dois aparelhos usando o **mesmo APK** desta branch. Mantenha
   os dois no mesmo Wi-Fi, de preferência sem isolamento de clientes.
2. No host, abra uma ROM homebrew de redistribuição autorizada, escolha
   Rollback (Recomendado), crie sala LAN e confirme a autorização de envio.
3. No segundo aparelho, escolha a sala mesmo sem ROM, confirme a oferta e
   veja o progresso até 100% e o hash conferido. Após a sincronização, o
   painel deve mostrar **Rollback (GekkoNet) / UDP direto**.
4. Se mostrar Lockstep TCP, registre o texto exato do erro. O aplicativo deve
   continuar a partida sem obrigar a criar outra sala.
5. Com o jogo rodando, pressione Home ou abra WhatsApp/navegador por 1 minuto
   e retorne. Uma notificação persistente 'MaizoSNES — multiplayer ativo'
   deve aparecer durante a sessão. Confirme que a partida continuou e que
   os dois aparelhos permaneceram conectados.
6. Repita a troca de apps por 2 minutos em ambos os aparelhos, observando
   modo de economia de bateria, FPS, áudio e estabilidade. Evite usar a opção
   'Forçar parada' nas configurações do Android: ela pode matar toda sessão.
7. Repita no multiplayer online Wi-Fi/4G. Confirme rollback real no overlay,
   observe atrasos adaptativos e compartilhe o diagnóstico se travar.

Limites: Android pode eliminar o processo sob pressão de memória, mudança de
rede ou fechamento explícito. O serviço melhora a persistência em background,
mas **não é uma garantia de sobrevivência após force-stop ou encerramento do
processo**. O botão PS/Xbox central abre o menu; Start joga como Start SNES.


### Energia, áudio e conexão em segundo plano (2026-10-20)

Com uma partida LAN/online ativa, abra outro app por 90 segundos e volte.
O serviço conectado exibirá uma notificação persistente e manterá locks de
CPU/Wi-Fi enquanto a sessão existir. No retorno, não deve reproduzir áudio
atrasado de segundos anteriores. Compare o quadro atual do host e do guest.

Repita com a tela apagada por 2 minutos. Alguns fabricantes encerram sessões
sob Doze/agressivas restrições de bateria; registre o modelo e a versão do
Android. O app **não pode garantir** a partida depois de `Forçar parada`,
encerramento do processo, reinício do dispositivo ou troca de endereço IP.
A conexão só continua enquanto os sockets e a sessão nativa seguem vivos.


## Teste revisado: UDP 3 tentativas, métricas e segundo plano (20/10/2026)

1. Use o último APK verde em dois aparelhos; host e cliente devem estar na **mesma versão**. Selecione Rollback (Recomendado).
2. Teste LAN Wi-Fi com ROM idêntica: após confirmação da sala, o overlay deve dizer UDP direto. Se aparecer tentativa 2/3 ou 3/3, anote. Se TCP, mande a mensagem de erro e verifique AP Isolation no roteador.
3. Teste LAN com P2 sem ROM: o host confirma que detém direitos para redistribuir sua ROM homebrew; cliente aceita o arquivo, acompanha porcentagem, espera SHA-256 e só depois UDP.
4. Em modo online Wi-Fi/4G, registre por 10 minutos RTT, jitter, rollbacks por segundo, pico de stall, custo save+load, Limited Saving ON/OFF e renderização 1/1 ou 1/2. Se travar, use Status de Conexão → Compartilhar Diagnóstico.
5. Com proteção padrão **Multiplayer em segundo plano** ativada, pressione Home por 60 segundos, abra outro aplicativo e volte. Confirme que notificação persistente aparece e que a sala não mudou. Repita por dois minutos e no segundo celular.
6. Desative proteção na aba Multiplayer apenas se desejar economizar bateria. Essa opção não desconecta imediatamente, mas Android pode suspender rede/cpu; forçar parada ou perder rede por muito tempo pode encerrar a sessão.
7. Antes de enviar relatório, retire chaves, IPs públicos e dados privados. Anote Android, modelos, arquiteturas, conexão LAN ou online e tempo exato de travada.

Veja docs/BACKGROUND.md para limites Android. O código não fornece reconexão transparente quando o sistema mata o processo, nem ressincronização cruzada de saves após desync grave.


## Console Performance & Stability — validação adicional (2026-10-24)

- [ ] Primeira biblioteca com 500 ROMs legais/homebrew: gerar índice local;
      reabrir o aplicativo e comparar tempo; apenas ROMs novas/alteradas
      devem ter cabeçalho relido. Inspecionar ROM renomeada/removida.
- [ ] No landscape, alternar chips **SNES** e **NES**, favoritos, recentes,
      busca e modo coleção por gamepad; interface continua acessível em 640×360 dp.
- [ ] Importar ROMs isoladas e por lotes, sem capas: exibir diálogo de consentimento
      apenas para capas ausentes. Escolher **AGORA NÃO** e verificar zero tráfego
      para thumbnails.libretro.com.
- [ ] Confirmar download manual, indicador `Capas 1/N`, cancelar e retomar;
      mudar para jogo enquanto baixa — novas buscas devem parar.
- [ ] Reimportar nomes equivalentes (regiões/revisões) em NES/SNES:
      reutilizar capa local compartilhada e não fazer novo HTTP; coleções
      NES e SNES jamais compartilham a mesma chave de arte.
- [ ] Em NES **offline**, abrir toolbar e **TESTE NES**: pausar, executar dois
      replays neutros de 36 quadros, comparar estados e restaurar o jogo.
      Teste inconclusivo não prova bug de GekkoNet; não executá-lo online.
- [ ] NES UDP LAN, Wi-Fi/4G, 4G/4G (10–20 min) com captura de logcat/tombstone
      se fechar; teste offline de determinismo positivo NÃO garante cross-ABI
      nem negociação de rede. Repetir com SNES para detectar regressões.
- [ ] Testar sair/entrar 30 vezes, áudio alternado, botão HOME e troca de app
      e retorno. Validar Quick Resume offline e não salvar estado online.
- [ ] Ativar/desativar HUD FPS e **⚡ RÁPIDO** no menu rápido; não tocar
      nos protocolos, estados, frequência de CPU ou configurações de servidor.
- [ ] Se houver crash, guardar código de falha, arquitetura e stack com
      `adb logcat -b crash -d`; o registro local de interrupção não captura SIGSEGV.

**Limites:** CI cobre a compilação, políticas de cache e regras locais.
Sem dois dispositivos reais e traces de crash não é possível afirmar
estabilidade definitiva do NES com GekkoNet.


## Hotfix LAN e gamepads físicos — 2026-10-26

- [ ] Abra a aba **Multiplayer** no Android **landscape** e em portrait; o app
      não deve fechar quando nenhum `FocusRequester` está registrado.
- [ ] Abra SNES e NES offline, entre na aba Multiplayer e pressione
      **CRIAR SALA LAN SEM INTERNET**. A tela deve permanecer no lobby,
      com **● SALA LAN ABERTA**, instruções e **FECHAR SALA LAN**.
- [ ] Confirme pelo segundo aparelho que a sala aparece na descoberta
      Wi-Fi e que a porta TCP 55435 aceita a conexão.
- [ ] Aceite a conexão P2 e a transferência de ROM apenas quando autorizada.
      A tela do jogo deve abrir **somente após conexão confirmada**.
- [ ] Teste **FECHAR SALA LAN** e criação novamente sem reiniciar o app.
- [ ] Teste modo LAN automático, IP manual e ambiente com porta ocupada.
      O botão não deve abrir gameplay em caso de erro.
- [ ] Abra Configurações → Controles → **CONTROLES FÍSICOS P1/P2**.
      Conecte dois USB/Bluetooth, vincule cada um ao jogador correto.
      Remapeie B/A/Y/X, D-pad, Start/Select e L/R separadamente.
- [ ] Verifique que o controle P2 **não aciona P1** quando o P2 local
      estiver habilitado, e vice-versa. Com um controle e touch, P2
      utiliza o gamepad e P1 utiliza touch.
- [ ] Feche/reabra o app: remapeamentos e escolha de dispositivo persistem.
      Troque IDs de dispositivos ao reconectar: os perfis seguem o
      identificador físico (descriptor) quando disponível.
- [ ] Remapeie uma tecla já usada por outro botão; as duas atribuições
      devem trocar de posição, sem duplicar entradas.
- [ ] Botão central Guide/PS/Xbox permanece reservado ao menu.
      Com remapeamento padrão, START continua START do jogo.
- [ ] Repita SNES e NES multiplayer existente (LAN/4G/Wi-Fi),
      ROM autorizada, estado/saves e mudança de aplicativo.
- [ ] Se houver crash, capture `adb logcat -b crash -d` e remova
      nomes pessoais, endereços e identificadores antes de compartilhar.

**Observação:** CI não substitui testes de LAN em dois aparelhos,
nem garante funcionamento de controles USB/Bluetooth de todos os fabricantes.


## GekkoNet Lowend (perfil opcional)

Ver `docs/GEKKONET_LOWEND.md`. Conferir comportamento padrão OFF, toggle
no rodapé da aba Multiplayer, restore das opções antigas e sessão ativa
bloqueando mudança de modo. Validar que apenas um peer pode ativar,
pois nenhum protocolo/snapshot foi alterado. O caminho direto ainda
necessita teste de performance e estabilidade em ARMv7/ARM64 físicos.
