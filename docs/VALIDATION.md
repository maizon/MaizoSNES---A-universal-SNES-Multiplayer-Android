# MaizoSNES — Validação física de rollback e compartilhamento

Versão experimental; executar **com dois telefones reais**, APK universal
idêntico, ROM homebrew redistribuível. Não usar ROM de terceiros sem direitos.

## Registro básico

1. Na aba Multiplayer selecione Rollback (Recomendado). Anote versão do APK,
   arquitetura de cada aparelho, ROM SHA-256, SO Android, latência e rede.
2. Abra **STATUS DE CONEXÃO / DEBUG** na aba Multiplayer, em Ajustes e pelo
   menu do jogo (botão PS/Xbox central; START deve ir para o jogo).
3. Verifique motor real e transporte, não apenas preferência selecionada.
   Salas Railway HTTPS/WSS devem mostrar **Lockstep (MZNP TCP)**, jamais WebRTC.
4. Observe RTT min/média/máx, jitter, rollbacks/s, input delay e desyncs.
   Perda N/D é comportamento esperado: Gekko não publica esse dado.

## Matriz de testes

- [ ] Dois Android no mesmo Wi-Fi: abrir ROM idêntica, criar/descobrir sala LAN;
  confirmar rollback Gekko/UDP e conexão P1/P2; jogar 10 minutos.
- [ ] Modo manual IP:porta: host TCP 55435, UDP 55436 liberado; rollback
  aparece no overlay. Testar bloqueio UDP -> fallback automático Lockstep.
- [ ] Duas redes diferentes com Railway: sala HTTPS/WSS, status Lockstep,
  partida de 10 minutos, sem afirmar rollback WebRTC.
- [ ] Cliente sem ROM: entrar na mesma sala, oferta de ROM homebrew, aprovar
  host e cliente; progresso de 0% a 100%; calcular SHA-256 no destino.
- [ ] Rejeitar oferta no cliente: nenhum byte de ROM é salvo; a sala fecha
  com uma mensagem clara sem congelar o Android.
- [ ] ROM maior que 8 MiB autorizada: verificar 64 MiB máximo, timeouts e
  atualização de progresso em ambos os aparelhos.
- [ ] ARMv7 x ARM64: jogar 10 minutos, observar divergências de hash,
  sincronização do áudio, rollback, responsividade e estados.
- [ ] Pressionar START no jogo = SNES Start; GUIDE/MODE abre menu;
  controle físico navega botões do overlay e FECHAR.
- [ ] Sair e reabrir app: ROM sharing ON e receber ON; rollback preferido.
- [ ] Confirmar ausência de ROM/BIOS embutidas no APK e licenças intactas.

## ADB (opcional)

```bash
adb logcat -c
adb logcat -v time -s MaizoSNES:D AndroidRuntime:E *:S > maizosnes-test.log
```

Registre a hora, passos, dois modelos e modo real exibido no debug. Evite
compartilhar logcat contendo dados pessoais, IP público ou ROMs.

## Condições de aprovação

CI verde **não** substitui testagem em hardware. Produção requer: zero
travamento, save states e ROM checksum corretos, fallback sem desconexão
inesperada e overlay correspondente ao transporte realmente utilizado.


## Cenários WebRTC (precisam de celulares de verdade)

- [ ] Mesmo APK no host/client. Host Wi-Fi e convidado 4G/5G; criar sala Railway online; selecionar Rollback (Recomendado).
- [ ] Registrar `ICE checking` seguido de `connected`; overlay deve exibir **Rollback + WebRTC DataChannel** SOMENTE quando real.
- [ ] Testar ROM homebrew com cliente sem arquivo: consentimento anfitrião, oferta cliente, transferência % e SHA-256 validados ANTES do RTC.
- [ ] Jogar 10 minutos: medir RTT, correções/s, frames, dessincronização e áudio; salvar print do overlay.
- [ ] Bloquear UDP/STUN / testar CGNAT: o canal WebRTC falha e a mesma sala deve seguir em **Lockstep + TCP via Railway**, sem congelar.
- [ ] Conferir campo ICE/TURN/NAT no overlay e compartilhar relatório usando o menu de compartilhamento do sistema.
- [ ] Validar armv7 com arm64; desync e serialização cross-ABI são especialmente relevantes.
- [ ] Sem credenciais TURN, redes com NAT simétrico podem NÃO completar WebRTC; a fallback TCP é esperado.


## Validação nova do ICE/TURN e regressões (19/10/2026)

- [ ] Em Ajustes → Rede, testar a coleta STUN/TURN; registrar host/srflx/relay.
- [ ] Confirmar que uma coleta local não mostra falsamente 'peer conectado'.
- [ ] Dois telefones em Wi-Fi/4G separados: verificar ICE checking → connected
      e o overlay Rollback + WebRTC DataChannel; repetir por 10 minutos.
- [ ] Restringir UDP/STUN e repetir: observação do retry relay-only TURN.
- [ ] Desativar TURN/bloquear relay: ver Lockstep TCP via Railway, não WebRTC.
- [ ] Rom homebrew redistribuível enviada com consentimento de ambos e SHA-256
      antes de estabelecer rollback; repetir no fallback TCP.
- [ ] Verificar START (jogo) e GUIDE (menu) e navegar os ajustes por gamepad.
- [ ] Não divulgar senha de TURN, chave API, IP público ou ROM no relatório.
- [ ] Testar ARMv7 e ARM64, 10 minutos sem desync e sem stall excessivo.


## LAN UDP + ROM + background regression tests (2026-10-20)

- [ ] Host Rollback, guest Rollback, both on same Wi-Fi: verify direct UDP
      rollback in debug with dynamic UDP port, not the discovery port 55436.
- [ ] Guest WITHOUT homebrew ROM: after host authorizes redistribution and
      guest accepts, observe 100% TCP transfer, SHA-256, state sync then rollback.
- [ ] Guest rejects transfer: no ROM installed; visible message, no freeze.
- [ ] Restrict UDP locally: both peers must return to Lockstep on the SAME TCP
      connection after the 10s preflight, without recreating a room.
- [ ] Host Rollback, guest selects Lockstep: MZNP v7 opt-out must run TCP.
- [ ] Switch to another app for 1 minute on both devices and return: persistent
      multiplayer notification visible, active session still in same frame
      continuity; no dialog replacing the game.
- [ ] Turn the screen off/on; verify battery restrictions, wake lock and
      reconnection behavior; distinguish normal app switching from force-stop.
- [ ] Wi-Fi ↔ 4G online WebRTC: test for 10 minutes, log RTT, delay changes,
      corrections per second and any full video/audio stall.
- [ ] Run after 30s uninterrupted and 2min in background on both ARM64/ARMv7.


### Additional 2026-10-20 background / fallback verification

- [ ] Android 10, 12, 14 and ARMv7/ARM64, if available: create LAN room and
      background host/client for 90 seconds. Confirm ongoing FGS notification.
- [ ] On Wi-Fi with screen off, ensure peer network frames keep advancing;
      check battery optimizer/Doze restrictions, record manufacturer and model.
- [ ] Check that resuming from Home shows CURRENT frame, not prior freeze,
      and audio does not play several seconds of stale buffered PCM.
- [ ] Resume and end session: confirm the notification disappears and
      CPU/Wi-Fi locks are released (Android battery stats/system tools).
- [ ] Force UDP preflight failure after several handshake polls: both peers
      restore initial state and continue on synchronized Lockstep frame zero.
- [ ] Force WebRTC/ICE to fail after Gekko negotiated but before first connected
      message: same state restoration and TCP fallback.
- [ ] Leave Wi-Fi to cellular: this does NOT promise connection migration;
      current sockets may disconnect; capture logs and actual overlay state.


## Matriz de aceitação — correções de 20/10/2026

- [ ] **LAN rollback:** dois telefones na mesma Wi-Fi, mesma versão universal, verificar tentativa UDP 1/3 (ou 2/3, 3/3) e overlay `Rollback GekkoNet / UDP direto` durante 10 minutos.
- [ ] **LAN com P2 sem ROM:** host com ROM homebrew cuja redistribuição é autorizada, cliente sem arquivo; confirmar aceite do host, aceite cliente, progresso de 0 a 100%, SHA-256 e jogo via rollback sem retransferir ROM.
- [ ] **ROM diferente:** cliente com arquivo distinto recebe oferta, aceita, verifica SHA-256 e depois usa rollback.
- [ ] **ROM idêntica:** nenhum upload é necessário; ambos entram por UDP.
- [ ] **Falha UDP:** bloquear UDP entre celulares; observar até três tentativas e fallback Lockstep **na mesma sala**, sem congelar tela.
- [ ] **AP Isolation:** ativar isolamento de clientes no roteador: UDP pode não conectar, e TCP também pode falhar se o isolamento bloquear unicast; não prometer fallback quando TCP for bloqueado.
- [ ] **Online Wi-Fi/4G:** verificar overlay WebRTC real, jogar 10 minutos, medir picos >500 ms e input delay adaptativo, registrar Limited Saving, modo de apresentação, RTT e rollbacks/s.
- [ ] **Troca de apps:** mandar A e B ao Home por 1–2 min e voltar, repetindo LAN e online; sessão e notificação devem continuar se processo permanecer vivo.
- [ ] **Bateria/áudio:** desligar opção de proteção em segundo plano, testar economia de bateria e áudio ao voltar sem fila atrasada.
- [ ] **Desync:** confirmar gameplay estável; em Limited Saving ON verificar em dois devices por mais tempo, pois a checagem nativa de desync fica desabilitada nesse modo.
- [ ] **ARMv7 e ARM64:** repetir em arquiteturas diferentes; confirmar nenhuma ROM/BIOS incluída no APK.

**Automatizado ≠ validado em aparelho:** CI executa testes JVM de protocolo e builds para dois ABIs. LAN unicast UDP, Wi-Fi lock, WebRTC via CGNAT e ROM instalada por SAF exigem teste físico.
