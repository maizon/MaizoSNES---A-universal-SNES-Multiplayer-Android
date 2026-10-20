# Como testar MaizoSNES em dois celulares

## Instalação

1. Abra [GitHub Actions](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions), selecione a **última execução verde** da branch `work/android-foundation` e baixe o artefato `app-debug`.
2. Extraia e instale **o mesmo APK nos dois aparelhos**. Se o Android solicitar, permita instalação desse APK de teste somente pela fonte que você confia.
3. Prepare uma ROM SNES homebrew que possa ser legalmente distribuída. Nenhum jogo está embutido no APK.
4. No anfitrião A, conecte Wi-Fi. No convidado B, use 4G/5G ou Wi-Fi de outro provedor.

## Partida online (Rollback quando possível)

1. Abra a ROM em A e selecione Multiplayer → Rollback (Recomendado) → Criar sala online.
2. Se a ROM for redistribuível, confirme a autorização por sessão. O cliente só receberá bytes depois de aceitar a oferta.
3. No B, selecione Multiplayer → Atualizar salas → Sala de A. Caso não possua a ROM, aceite a oferta autorizada e aguarde o progresso 0–100%, hash SHA-256 e estado inicial.
4. Verifique os estados ICE. No menu durante o jogo (GUIDE/PS/Xbox central, não Start SNES), abra STATUS DE CONEXÃO / DEBUG.
5. Se aparecer **Rollback (GekkoNet)** + **WebRTC DataChannel**, o canal P2P foi estabelecido. Se aparecer **Lockstep (MZNP TCP)** + **TCP via Railway (WSS)**, a rede exigiu fallback, mas o jogo deve continuar normalmente.
6. Jogue por 10 minutos com entradas de ambos, compare sincronização, jitter e correções. Repita com aparelho ARMv7 se possível.

## LAN / IP manual

1. Conecte os dois ao mesmo Wi-Fi; crie sala LAN no A, descubra ou digite IP:porta no B.
2. Confirme **Rollback + UDP direto** no overlay. O host usa UDP porta TCP+1.
3. Simule indisponibilidade UDP (bloqueio no roteador) e confirme retorno para Lockstep TCP antes do primeiro frame.

## Diagnóstico e relatório de bug

1. Abra STATUS DE CONEXÃO / DEBUG na aba Multiplayer, Ajustes ou no menu do jogo.
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
