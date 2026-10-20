# MaizoSNES v1 task status

Implemented: dual ABI configuration and CI checks; offline SNES title/region
reader and tests; launcher 2/3/4 columns and list, favorites and recents,
ROM details with patch and delete; gamepad A/B/D-pad routing outside game;
GUIDE/MODE pause/menu input routing; START reserved for the actual SNES game input; private-LAN opt-in ownership prompt for
default-enabled ROM transfer; secure signed-release workflow definition.

Existing preserved: Snes9x, native audio/video, SAF/ZIP, manual LAN, TCP
relay, core settings handshake, SHA-256, IPS/BPS, state slots and touch
editor. All changes need CI and hardware smoke tests to confirm regression
safety. The source updates themselves are not proof of on-device success.

Pending: xdelta/VCDIFF, TheGamesDB authorized API, exact supplied icon,
per-control independent sizing/opacity and full-screen drag constraints,
hardware gamepad focus validation across all Settings, 30-second clip capture, per-slot
thumbnail/resume, fast-forward/rewind, haptics, final Material 3 Expressive
UI, verified real WebRTC NAT traversal, production key secrets, signed
artifact and GitHub official stable release. See release workflow for gate.

## October 19 gamepad remediation

Activity now routes the physical START key to SNES bit 3 and reserves GUIDE/MODE for the overlay. Compose outside gameplay uses A/Enter and B/Back, with L1/R1 for app tabs and D-pad/analog stick for focus movement. FocusRequester is attached to initial targets in multiplayer, settings and credits. Modal dialogs directly handle gamepad A/B. Focus indicators are applied to game cards and selected option cards. Build verification and physical controller regression tests are required before declaring support complete.


## Conexão remota (19/10/2026)

A flag TLS é agora propagada do diretório HTTPS até a conexão TLS nativa,
inclusive após o diálogo de permissão do host. UI permite cliente sem ROM ou
com hash diferente no relay TLS, mantendo bloqueio de ROM no relay sem TLS.
DEV 9090 ativa avançado por padrão e pode ser desligado separadamente do
compartilhamento LAN. Adicionado teste JVM OnlineRomJoinPolicyTest; requer
CI e teste real de dois aparelhos via relay TLS antes da versão estável.


## Proteção extra de TLS (2026-10-19)

Durante busca automática de servidores, anúncios relayTls=true recebem
handshake SSL com verificação do certificado e hostname. Um servidor
mentindo sobre TLS não deve aparecer como apto a receber ROM. Ao conectar
a sessão repete a checagem TLS. Testes de protocolo também cobrem recusa
pela parte cliente antes da transmissão de bytes. A compatibilidade entre
celulares remotos reais ainda precisa ser demonstrada.

## Varredura WSS e diagnóstico de conexão (19/10/2026)

A busca automática passa a testar diretamente o upgrade WebSocket protegido por
TLS para servidores que anunciam WSS, sem depender de proxy TCP separado.
Servidores TCP antigos mantêm a verificação de certificado/hostname quando
anunciam TLS. Falhas de conexão por servidor aparecem na interface, sem apagar
salas encontradas em outros servidores. O painel de IP manual informa quando
a conexão disponível é HTTPS/WSS.

Um teste de integração usa uma sala efêmera e bytes sintéticos, verificando
`/api/rooms`, upgrade WSS, HOST/JOIN, READY e tráfego bidirecional. O CI
executa também um smoke externo no Railway, com resultado separado do build
Android para que indisponibilidade externa não invalide o APK. **Não há teste
físico de dois Androids nem prova de transferência real de ROM nesta etapa.**


## 2026-10-19 — experimental GekkoNet rollback

Pinned BSD-licensed upstream C++20 SDK (with Asio Boost/zpp MIT notices), JNI
integration, game event save/load/advance, UDP handshake and Gekko stats added.
UI defaults Rollback with explicit Lockstep fallback. Save/load measurement
and UDP binding are viability gates. Manual TCP is preserved; WSS relay
continues to use Lockstep (not falsely labeled UDP rollback). CI validation
and real device test of two simultaneous players are required before any
production readiness claim.


### ROM sharing e métricas (19/10/2026)

Eliminado gate do PIN DEV para compartilhar ROMs de redistribuição autorizada
na LAN e HTTPS/WSS, preservando confirmação host por sessão e aceite client.
Transferência reporta progresso em chunks, com timeout longo próprio e hash.
Novo debug dialog disponível em Multiplayer, Ajustes e menu no jogo; exibe
motor REAL, transporte REAL, RTT min/média/máx, jitter, correções/s, delay,
perda N/D e desyncs. Sem WebRTC inexistente/servidor Fly.io fictício.


## 2026-10-19 — hash da ROM recebida (regressão)

Corrigido o estado `currentRomHashPrefix` ao instalar uma ROM autorizada
recebida pela rede: antes o núcleo carregava a ROM, mas a tela Multiplayer
podia continuar exibindo o hash da ROM anterior ou um hash vazio, impedindo
a descoberta/entrada posterior em salas compatíveis. Agora carregamento local
e importação remota usam a mesma função de SHA-256 (prefixo de 8 bytes).
Testes JVM cobrem vetor SHA-256 conhecido e igualdade com arquivo local.
A validação da sincronização de UI e do multiplayer em dois aparelhos físicos
permanece pendente; este patch não equivale a prova de hardware.


## 2026-10-19 — configuração TURN e ICE em desenvolvimento

Cliente Android já compila WebRTC/GekkoNet; esta rodada adicionou troca
trickle ICE via WSS, STUN redundante, retry TURN relay-only, timeout de
negociação e fallback TCP, diretrizes de teste ICE no app e consulta
HTTPS de credenciais Metered no Railway (variáveis opcionais, sem chave APK).
Credenciais compartilhadas demonstrativas do Open Relay são tentadas sem
SLA. Documentação docs/TURN_SETUP.md e testes do servidor foram incluídos.
Hardware P2P em duas operadoras e ARMv7 real NÃO foi validado; não publicar
como funcional em todos os NATs até confirmação física.

## 2026-10-19 — WebSocket RFC 6455 frame boundary

Fixed relay framing for the 65,536-byte boundary: outgoing frames now use
the 64-bit extended-length header, and masked incoming frames parse that
header with an explicit 64 KiB cap. A 65,537-byte client frame is rejected.
Added real loopback WebSocket integration coverage at 126, 65,535 and 65,536
bytes in both directions. The normal 32 KiB ROM chunks are unchanged.
This is a protocol regression test, **not** evidence of two-device NAT/ICE
connectivity or permissioned ROM transfer on hardware.

## 2026-10-19 — signaling and WebSocket control-frame hardening

Added local relay integration coverage for a hidden GekkoSignaling room, binary
TURN-capability exchange, segmented SDP, trickle candidate and bilateral
Lockstep fallback acknowledgment. The WSS parser now rejects RFC 6455 control
frames longer than 125 bytes and one-byte close payloads rather than emitting
an invalid pong. These are synthetic protocol tests only: they do not validate
Android WebRTC, ICE/NAT traversal or two-device ROM transfer. Production Railway
may remain pinned to an older commit until an independently verified deployment.


## 2026-10-20 — correção relatada por usuário (LAN, ROM, background)

- Investigado: a integração direta Gekko anterior negociava UDP antes de
  executar a transferência de ROM MZNP; faltava completar autorização,
  envio SHA-256 e sincronização antes do engine. Corrigido no caminho LAN
  MZNP v7 com troca dinâmica de portas UDP e fallback simétrico TCP.
- Eliminado conflito entre UDP 55436 de descoberta e porta do jogo usando
  porta UDP efêmera anunciada no mesmo canal TCP.
- Novas verificações de não regressão: host Rollback + cliente Lockstep,
  cliente sem ROM autorizada com fallback após preflight. Testes JVM não
  usam JNI Gekko nem representam teste físico de UDP.
- Foreground connectedDevice service e wake lock mantêm rede/emulação ativa
  durante troca de aplicativo; Snes9x netplay não depende de Surface.
- Alívio de travadas: limpar estado especulativo no fim de cada Gekko tick,
  estender tolerância de desconexão, ajustar delay de input após jitter,
  conservando compatibilidade e sem ativar -ffast-math.
- LAN Gekko, ROM real e background em dois Androids ainda necessitam de
  validação humana; não prometer zero falhas até esses testes passarem.


## 2026-10-20 — second-pass hardening (build/device status)

- Added Wi-Fi high-performance lock to foreground multiplayer service and
  discard-on-return PCM JNI entrypoint, preserving live netplay clock on
  Activity/surface pause. Device behavior still unverified.
- Restored the exact negotiated Snes9x state after failed speculative Gekko
  LAN/RTC preflight, BEFORE resuming unmodified MZNP TCP lockstep.
- Tested compile of foreground service and APK universal in GitHub Actions;
  regression test coverage for MZNP v7 mixed netcode mode and authorized LAN
  ROM transfer is implemented.
- Do not claim successful hardware two-phone netplay or uninterrupted game
  after Android process death; these require user testing/protocol redesign.


## 2026-10-20 — continuidade autônoma LAN/ROM/stutter/background (experimental)

Adicionada negociação direta MZNP v7 com até três trocas de porta UDP, barreira bilateral e retorno ao Lockstep pela mesma conexão depois do SHA-256/transporte de ROM autorizado. Testes JVM com driver Gekko falso exercitam tentativas fracassadas e sucesso subsequente, fallback completo e ROM compartilhada antes do rollback. Monitor de save/load, stalls >500ms, Limited Saving quando >8ms, ajuste de delay e apresentação de vídeo 1/2 em RTT sustentado >150ms. Foreground service de segundo plano é controlável e ativada por padrão. Nenhuma das quatro situações de dois celulares deve ser declarada 100% validada sem executar docs/VALIDATION.md no hardware.
