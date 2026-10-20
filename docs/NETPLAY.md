# Multiplayer (código experimental)

## Atualização v0.9 — MZNP v5

Relato do desenvolvedor em teste Android + VM: conexão pela Internet funcionando,
mas jogo reduz a ~2 FPS. O código antigo tinha dependência de um RTT completo
por quadro (`enviar input -> bloquear aguardando input remoto -> emular`).
Em redes com RTT~500ms isso explica ~2 FPS.

**Solução testada em CI:** novo protocolo v5 envia comandos de
`frame + atraso` antecipadamente, de 2 a 48 quadros, e só aguarda a entrada
correspondente ao quadro atual. O RTT é medido três vezes no handshake,
e o host negocia o buffer. Há três perfis selecionáveis:
Resposta rápida, Equilibrado e Conexão instável. Após receber as entradas,
o Snes9x roda as duas portas em lockstep determinístico. Checkpoints de
estado seguem anexados aos pacotes de entrada, sem bloquear em troca
serial de checksums. Teste de integração simula 200ms RTT em TCP e valida
vazão muito superior ao limite antigo de 5 FPS.

**Limitações:** input delay pelo menos metade RTT + jitter; o buffer não
corrige perda de pacotes/resincronização automaticamente. Ainda não há
predição/rollback; o relay TCP não é P2P direto e não adiciona criptografia.
Instale a mesma versão (MZNP v5) nos dois aparelhos. Não alegar 60 FPS real
sem teste físico em Android + VM/Android + Android.


## Atualização de protocolo (0.7)

**Esta versão usa MZNP v3** e rejeita clientes 0.6/v2 incompatíveis. Durante a negociação, após conferir ROM e opções, o host envia até 64 cheats sanitizados; o segundo jogador precisa ter ativado consentimento prévio. Ambos aplicam os mesmos códigos enquanto o jogo está parado. Qualquer rejeição impede o primeiro quadro. Sem autorização, o host deve desativar cheats antes de criar a sala. Trocas de cheats durante frames permanecem bloqueadas. O fingerprint de estado a cada 120 quadros continua sendo usado.

Salas online agora podem anunciar prefixo de 64 bits do SHA-256 da ROM já modificada (IPS/BPS) e etiqueta de cheats para evitar tentativas incompatíveis. O handshake TCP continua conferindo o hash SHA-256 integral dos bytes reais, nunca apenas o prefixo da lista pública.

Modo **Automático** combina descoberta LAN UDP existente e diretórios em `server/registry.json`. A lista de servidores inicial não tem nenhum endpoint: o operador precisa implantar servidores reais, adicionar URL HTTPS e permitir a porta TCP do relay. **Não há P2P direto** nem servidores públicos implantados por esta revisão; refs: `docs/MULTIPLAYER_PREMIUM_PLAN.md`, `docs/INTERNET_RELAY.md`.

## Situação verificada

Esta revisão implementa **código** para partidas locais com dois controles físicos e para dois aparelhos por TCP, na LAN ou através de um endereço IP TCP alcançável. O aplicativo **ainda exige testes reais em dois Androids**, inclusive de determinismo, troca de ROM, latência, entrada/saída de app e Wi-Fi instável. Nunca confundir CI verde/teste de loopback com multiplayer validado em dispositivos.

### Modo local offline

- Ative porta 2 na aba Multiplayer.
- Dois gamepads conectados: menor ID de dispositivo = P1; segundo = P2.
- Um gamepad + tela tátil: touch = P1; gamepad = P2.
- Jogos individuais não viram multiplayer; depende do suporte nativo do cartucho.
- Ainda falta UI de remapeamento por dispositivo, testes de hot-plug e multitap 3–5 jogadores.

### Descoberta automática LAN

- O anfitrião anuncia sua sala na porta **UDP 55436** enquanto espera um jogador.
- O cliente procura salas assim que entra na aba Multiplayer; o botão **BUSCAR** repete a consulta. O endereço e a porta encontrados aparecem na lista; tocar conecta.
- Se o roteador tiver isolamento entre clientes, hotspot com bloqueios ou filtro de broadcast, use **IP/porta manual**; não exige internet.
- Descoberta não funciona entre redes diferentes. O transporte real é TCP, padrão **55435**, porta ajustável.

### LAN e online por endereço

1. Instale **exatamente a mesma versão do APK** em ambos os aparelhos.
2. Importe a **mesma ROM legítima**, abra-a uma vez em cada aparelho e volte à aba Multiplayer.
3. Confira que as opções do núcleo são idênticas, incluindo região e hacks.
4. No anfitrião (P1), escolha porta TCP (padrão 55435) e **Criar sala**.
5. No cliente (P2), digite o IPv4 local indicado no anfitrião e pressione **Entrar na sala**.
6. Após handshake e sincronização de estado, ambos entram na tela de jogo. Para sair, feche a partida pela navegação do app; sair do app/desconectar também encerra a sessão.

Online remoto: exige IP/porta TCP realmente acessível, tipicamente por uma VPN privada confiável ou encaminhamento de porta; roteadores com CGNAT impedem conexão direta. O APK oferece busca automática **LAN por broadcast UDP 55436**, mas não oferece relay, descoberta internet, matchmaking, STUN/TURN ou TLS. **Não recomendamos expor a porta diretamente à internet aberta**, pois o protocolo experimental não tem autenticação nem criptografia.

## Contrato de sincronização (MZNP v2)

- TCP em ordem, `TCP_NODELAY`, timeout de 10 s ao conectar e até 20 s para negociação (5 s por leitura de frame).
- Host envia magic `MZNP`, versão 2, SHA-256 exata do arquivo ROM e SHA-256 do identificador fixo de núcleo + opções ordenadas; cliente compara e devolve os próprios dados.
- Opções divergentes impedem a partida. Quando a ROM diverge, o protocolo **não** a envia por padrão: exige opt-in simultâneo do host e do cliente, conexão entre IPs privados, tamanho máximo de 64 MiB e confirmação SHA-256 da ROM antes de importar. Destino: biblioteca interna do aplicativo. A licença da ROM precisa permitir redistribuição (homebrew/domínio público/autorização); arquivos comerciais não são compartilháveis por este recurso sem direitos. Se qualquer condição falhar, a sessão recusa o jogo.
- Host transfere estado serializado **limitado a 64 MiB + cabeçalho**; cliente restaura e confirma.
- Host joga na porta libretro 0, cliente na porta 1. Em cada quadro ambos enviam sequência monotônica e máscara local (16 bits), recebem a remota e executam **um único** `retro_run` após a troca, sob mutex do núcleo.
- Periodicamente, a cada 120 frames, compara-se um fingerprint de 64 bits do save state; divergência é falha fatal informada, não é escondida.
- Ao desconectar, sockets são fechados, loop termina, thread offline é restaurada. Reset, estados e alterações de opções são bloqueados durante sessão ativa.
- Não inclui pausas sincronizadas, rejoin, espectadores, salas públicas nem rollback.

## Latência: limite físico e arquitetura futura

Lockstep é determinístico, mas **não consegue delay zero**: precisa aguardar o input remoto do quadro. TCP_NODELAY reduz espera artificial do Nagle; não elimina RTT, jitter, perda e distância. Latência WAN normalmente é perceptível, mesmo quando LAN funciona bem.

O caminho para respostas locais muito rápidas é **predição + rollback** com histórico de estados e inputs. Referência conceitual: https://docs.libretro.com/development/retroarch/netplay/ . A documentação RetroArch menciona replay/rollback com frames sincronizados. O código do RetroArch **não foi copiado** aqui, e sua arquitetura/obrigações de licença devem ser estudadas separadamente. Além do rollback, seria necessário silenciar áudio/vídeo durante replay, limitar histórico, marcar checkpoints, controlar SRAM e comprovar determinismo em aparelhos diferentes.

## Testes

- `./gradlew testDebugUnitTest` contém testes TCP loopback, recusa de ROM divergente, transferência autorizada em rede privada (quando o runner tem IPv4 privado) e parsing de cheats/descoberta, sem ROM comercial.
- CI valida compilação e protocolo básico **somente no JVM**; não mede input-lag, Wi-Fi, NAT, velocidade nem funcionamento de jogos.
- Em hardware real, testar troca de inputs simultâneos, dez minutos por jogo, NTSC/PAL, reconnect, redes com perda/jitter, hashes e término de sessão. Registrar APK SHA, Android, modelo e logs.

## Modos de rede e telemetria (0.3)

Os modos Offline local, LAN automática, LAN por IP e Internet/VPN usam perfis de IP e porta separados na interface (SharedPreferences). Modos não modificam a versão MZNP v2, input player1/player2, hashes periódicos ou estado inicial do host. Trocar de modo durante uma sessão é bloqueado para evitar perder a partida. A transição interrompe apenas a conexão, não remove ROM, estados ou perfis de controles.

O transporte TCP continua `TCP_NODELAY`. Em Internet/VPN há timeout de 12 segundos por leitura de frame para maior tolerância a jitter; LAN continua com timeout de 5 segundos. `Socket.trafficClass = 0x10` é pedido ao sistema como dica de prioridade de baixa latência, sem garantia de QoS. Métricas são coletadas por sessão: média e pico do **tempo à espera do input remoto** (não é uma medição exata de ping/RTT), FPS efetivo e quantidade de quadros com espera superior ao período nominal.

O tempo de ida e volta na rede não desaparece com ajustes de socket. **Não há implementação nem alegação de rollback/predição neste release.** Um rollback correto exigiria snapshots por frame, replay com áudio/vídeo silenciados, input prediction e testes de duas plataformas por longo período; isso ficou deliberadamente fora da atualização para preservar o multiplayer lockstep funcional.

Backup automático opcional antes/depois da conexão usa arquivos `states/multiplayer_<sha>_before/after.mzstate` guardados pela impressão digital da ROM, sem substituir os slots manuais. Restaure pelo menu Estados somente com a ROM correspondente carregada.


## ROM sharing update: default-enabled LAN transfer with rights check

The host/client switches now default to ON and persist across restarts.
Before first LAN hosting with sharing enabled, the host must confirm it
has redistribution permission. Declining disables sharing but does not
prevent matching-ROM network play.

The existing SHA-256 handshake compares the ROM and validates core options.
A mismatched ROM may be transferred only over a private-address LAN when
both sides allow it. The client verifies SHA-256 before using the bytes.
Relay/internet transport is plain TCP without authentication or encryption;
sharing ROM files across public relay remains blocked. Commercial ROM
rights cannot be established by a matching hash. Manual IP:port is retained.


## MZNP v6: transferência online autenticada

Host confirma autorização de redistribuição em cada sessão. Cliente recebe
oferta antes dos bytes e aceita ou rejeita; cada ROM tem SHA-256 integral e
máximo de 64 MiB. PIN DEV 9090 habilita configuração avançada inicialmente,
mas nunca substitui direitos e consentimento. A UI permite entrar sem ROM ou
com ROM divergente somente se o relay anuncia TLS e a opção de recebimento
está ativa. O socket Android valida certificado e hostname TLS; a API do
servidor deve usar HTTPS. Relay sem TLS permite jogar com ROM igual, sem
transferência. O operador TLS pode ler os dados (não é E2E).

Este código não instala automaticamente serviço TLS nem certificados válidos.


## GekkoNet rollback mode (direct IPv4 UDP; experimental)

The frontend defaults to **Rollback (Recomendado)**; **Lockstep
(Compatibilidade)** is selectable at any time before session start. For
manual IP and direct LAN sessions the peers negotiate over the existing TCP
port, compare full ROM SHA-256 and exact core options, synchronize the host's
initial save state, exchange IPv4 UDP ports, and initialize the pinned
GekkoNet C++20 engine. Snes9x save/load/advance events drive the engine on
a single emulation/network thread; speculative video/audio are suppressed.

**Viability gate:** snapshot <=16 MiB; measured save+load <=10 ms; UDP socket
bind succeeds, and peer UDP connects in the initial synchronization window.
If not, attempt the existing MZNP TCP Lockstep automatically before the first
game frame and display the reason. For NAT/CGNAT online rooms over HTTPS/WSS,
Gekko UDP cannot traverse the existing WSS relay and **Lockstep remains in
use**. The manual IP:port route is preserved without changing its MZNP
wire format.

GekkoNet public stats report ping (RTT) and jitter; the UI reports a count
of state-load rollback corrections. The public API does not provide packet
loss percent, so it must display "N/D" instead of inventing data. Input delay
by latency profile: responsive=0, balanced=1, stable=3 frames. Gekko source is
pinned at 0d491a4524e10173fc5d2aae22d042d3ae9f2d44 and is included in
both ARMv7 and ARM64 native builds, with the BSD, Boost and MIT license texts.

**Limits pending device validation:** IPv6, direct UDP under carrier NAT,
host/client mismatch when different builds are installed, cross-ABI snapshots,
competitive frame pacing, rollback audio smoothness, and multi-minute
determinism. This transport is not authenticated/encrypted and should be used
on trusted LAN/VPN only; web-based room sharing remains encrypted HTTPS/WSS.


## Auditoria de transporte e compartilhamento (outubro 2026)

O GekkoNet oferece um adaptador de transporte com `GekkoNetAdapter.send_data`,
`receive_data` e `free_data`, visto no header oficial. Porém **o app atual
não contém WebRTC DataChannel nem sinalização SDP/ICE real**: WSS sobre
Railway é TCP confiável e não simula UDP/WebRTC. Sem biblioteca WebRTC Android,
sinalização ICE e TURN disponível, a solução sem custo garantido é manter
GekkoNet UDP para LAN/IP direto e Lockstep TCP no WSS online. Não afirmar
rollback nas salas Railway. Não pressupor gratuidade de um provedor de relay.

O compartilhamento de arquivos ROM que o usuário pode redistribuir não requer
código DEV 9090. Host mantém confirmação de autorização por sessão e cliente
mantém confirmação individual antes de receber bytes. HTTPS/WSS autenticado
é exigido para compartilhamento entre redes.


### Fluxo de ROM em rede (telemetria, 19/10/2026)

A leitura/escrita de ROM usa chunks de 32 KiB, status de 512 KiB com percentual
visível, limites de 64 MiB, timeout de aprovação independente (90s) e timeout
de transferência de 180s. O host somente transmite bytes após o aceite remoto;
o cliente confere SHA-256 total antes de instalar ou executar. A UI atualiza
'Enviando/Recebendo ROM: N%' e 'Hash válido, instalando'. A confirmação de
direitos do host e o consentimento do cliente permanecem exigidos; o código
DEV 9090 não é requisito para transferência sobre HTTPS/WSS autenticado.


## WebRTC DataChannel para GekkoNet (fase experimental)

O SDK `io.github.webrtc-sdk:android-prefixed-stripped:150.7871.01`
fornece libwebrtc Android para arm64-v8a e armeabi-v7a, com API Java
`livekit.org.webrtc` e licença BSD-3-Clause (ver avisos completos). Após
transferência WSS de ROM autorizada, validação SHA-256, sincronização de opções
e estado inicial MZNP, um *segundo* WSS ao Railway realiza a troca de SDP.
A sala de sinalização é derivada por SHA-256 do ID da sala e não aparece na
lista de jogos; nenhuma ROM é enviada nela.

WebRTC usa STUN `stun:stun.l.google.com:19302` e
`stun:stun.cloudflare.com:3478`. O DataChannel binário é não ordenado e
não confiável (0 retransmissões); cabeçalho/estado do GekkoNet permanecem
na biblioteca BSD C++20. O adaptador JNI implementa `GekkoNetAdapter`
por callbacks `send_data`, `receive_data` e `free_data` com fila limitada.
SDP/ICE transitam via WSS Railway; pacotes Gekko via DTLS/SCTP WebRTC.

Ambos os pares confirmam o canal aberto antes de iniciar rollback; caso
falhe ICE/SDP, a conexão WSS de jogo original permanece disponível e retorna
para Lockstep, sem alterações no wire format MZNP. Um backend TURN não é
provisionado: Metered oferece tier gratuito condicionado a conta e quota,
sem credenciais públicas permanentes garantidas; a implementação atual usa
STUN e falha para TCP quando NAT impede conexão direta. Não foi criado
servidor UDP Fly.io nem há garantia de custo zero para TURN ilimitado.

O overlay apresenta transporte real: `WebRTC DataChannel` quando Gekko via
RTC foi iniciado, caso contrário `TCP via Railway (WSS)`. A API RTCStats
pode identificar candidato tipo relay (TURN) e estado ICE, mas **perda de
pacotes RTP não equivale a perda de DataChannel/SCTP**; nesse caso, N/D.
NAT simétrico só pode ser inferido com testes mais profundos, portanto N/D.

**Não testado em dois devices reais:** funcionamento entre diferentes
operadoras, consumo de dados, jitter, sincronização 10 minutos, armv7
heterogêneo, detecção/resync de desync e fallback físico. Consulte
`docs/DEVICE_TESTING.md`. A compilação não substitui esses testes.


## Atualização: sinalização trickle ICE e TURN best-effort

A partir desta revisão, o segundo canal WSS envia explicitamente SDP e
candidatos ICE, com limites para mensagens e prazos finitos. Primeira tentativa:
STUN + TURN com ICE normal, até 17 s para abrir o DataChannel. Quando os dois
participantes anunciam TURN habilitado e o canal não abre, tenta outra sala
de sinalização para ICE relay-only, por até 14 s, antes do fallback Lockstep.
A negociação da ROM e do estado inicial continua no WSS de jogo original.
O GekkoNet usa seu adaptador C++20 para pacotes binários WebRTC SCTP/DTLS.

A configuração do ICE prioriza STUN Google/Cloudflare e aceita opções locais.
O serviço Railway consulta as credenciais de conta Metered em /api/ice caso
MAIZO_METERED_APP e MAIZO_METERED_TURN_API_KEY estejam configuradas.
Sem elas o cliente tenta credenciais públicas demonstrativas do Open Relay,
que NÃO possuem garantia de vigência; nenhuma chave privada fica no APK.
Consultar docs/TURN_SETUP.md. O teste ICE coleta candidatos localmente, não
estabelece sessão sem outro aparelho. TURN e perda SCTP são reportados apenas
quando houver dados; não inferir perda usando estatísticas RTP.

**Atenção:** textos anteriores deste documento descrevem versões históricas
sem WebRTC. O código de DataChannel agora existe, mas não foi validado ainda
entre dois Android reais, nem fornece garantia de ausência de custo/quota TURN.


## 2026-10-20 — LAN ROM + Rollback v7 (experimental)

The previous direct Gekko TCP negotiation ran BEFORE the rights-checked ROM
handshake and could reject a guest with no local ROM. This branch preserves
MZNP v6 Lockstep/WSS unmodified and adds a **separate MZNP v7 direct-IP
negotiation** when the host chooses Rollback. The client may opt out: both
then use v6-compatible Lockstep input pipelining. For two updated peers:

1. Host advertises LAN room using existing UDP discovery on 55436.
2. Joiner connects TCP on the original room port and synchronizes full ROM
   SHA-256, core settings and optional authorized transfer with explicit host
   rights affirmation plus guest approval. **No ROM bytes on UDP.**
3. Both exchange/restore the host's initial Snes9x state over TCP.
4. On identical state size and viable serialize/restore time (<=24ms), peers
   exchange ephemeral UDP ports over the same TCP stream, initialize native
   GekkoNet and confirm that BOTH UDP peers connected within 10s.
5. GekkoNet runs the game directly over UDP; the same TCP stream stays open
   for control. If either UDP preflight/handshake fails BEFORE gameplay, both
   stay on the original TCP Lockstep frame stream with no new socket needed.

Using an ephemeral UDP port on each side avoids a collision with the LAN
broadcast discovery listener on UDP 55436. No router port forwarding is
needed when both phones share a trusted Wi-Fi network without AP isolation.
For cross-internet direct IP, an inbound UDP mapping may still be required.
A host in Rollback mode uses MZNP v7, so guests with the older v6-only APK
should update to the same universal APK or select Lockstep compatibility.

Audio/video speculative replay suppression is cleared at each Gekko tick
boundary. A live adaptive input delay increases slightly during frequent
rollbacks/jitter and decays after sustained stability, minimizing expensive
save-load bursts. Gekko native timeout increased to 30s for transient
background/network scheduling delays. Actual smoothness must be measured on
physical Android devices; this is not proof of zero stutter.

For app switching/background: a connected-device foreground service with
persistent notification and partial CPU wake lock is active ONLY for a live
multiplayer session. Temporary onPause/surface loss does not stop the network
thread. Android force-stop, process termination or connectivity changes can
still end the session; no reconnection protocol is claimed.


### Refinamento de UDP no modo IP manual (20/10/2026)

Na LAN, GekkoNet usa portas UDP efêmeras, negociadas pelo canal TCP depois da
transferência/checagem SHA-256; isso evita a porta UDP 55436 da descoberta.
No modo **Internet/IP manual** com porta TCP explicitamente exposta, o host
preserva a convenção anterior **UDP = TCP + 1** para permitir encaminhamento
de porta no roteador. O cliente continua usando UDP efêmero; se a rota falhar,
ambos tentam permanecer na mesma partida via Lockstep TCP. No modo WebRTC
com DataChannel, a janela máxima de previsão de inputs é 10 quadros (em vez
de 6), para tolerar picos transitórios de jitter de rede móvel; pode haver
mais reprocessamento quando a perda é intensa e isso deve ser avaliado em
dois celulares reais.


### 2026-10-20 — handshake UDP bilateral

A confirmação `GekkoPlayerConnected` pode chegar em horários diferentes para
host e cliente. Agora o primeiro lado que vê a conexão **não bloqueia o
thread da rede** esperando um byte TCP; continua executando o polling Gekko
pela porta UDP dinâmica enquanto aguarda o ACK bilateral, dentro do tempo
limite de 14 segundos. Uma espera TCP bloqueante poderia impedir que o
segundo jogador terminasse o handshake UDP e provocar fallback indevido.
A decisão de fallback continua compartilhada pelos dois peers antes dos
primeiros quadros visíveis de jogo. Teste em dois aparelhos ainda necessário.


### 2026-10-20 — persistence and deterministic fallback hardening

A foreground `connectedDevice` service owns only a **live-session lifetime
notification** (not the network socket itself). It holds a partial CPU wake
lock plus a full high-performance Wi-Fi lock until session shutdown, even
while the Activity surface is detached. Both resources are released on normal
end. This may consume more battery; Android can still kill/force-stop the app.
While backgrounded, emulation keeps advancing as the existing netplay worker
owns its clock, video surface may detach, and AudioTrack is stopped. On
return, obsolete PCM queued during the absence is discarded before audio is
re-enabled. This avoids stale audio playback without rewinding core state.

If a direct LAN/IP Gekko or online WebRTC handshake fails **after speculative
native frames**, the negotiated initial Snes9x state is restored before the
unmodified Lockstep TCP path begins. A failed state restoration aborts visibly
rather than continuing a desynchronized session.

This is compile/CI verified but **not device verified**. Screens off, battery
optimizers, partial network outages and vendor background task restrictions
may behave differently. MZNP v7 is used only for direct Rollback-capable
host sessions; older v6-only APK guests should update or select Lockstep.


## 2026-10-20 — robustez direta LAN e instrumentação de travadas

A negociação MZNP v7 pela LAN executa **antes de qualquer UDP**: SHA-256 das ROMs, confirmação bilateral de redistribuição, transferência TCP autorizada (blocos 32 KiB, progresso, hash integral), opções Snes9x e estado inicial confirmado. Host e convidado, ambos atualizados, então tentam Gekko UDP direto. A porta UDP de LAN é efêmera, não a 55436 da descoberta. No modo IP manual INTERNET, a primeira tentativa preserva o encaminhamento explícito **UDP=TCP+1**, inclusive quando a descoberta 55436 já foi fechada após aceitar TCP.

Até **três tentativas**: cada par troca a porta candidata pelo mesmo TCP, confirma o resultado do bind nativo, mantém polling Gekko durante a barreira de prontidão bilateral e impõe prazos explícitos por tentativa. Portas alternativas ajudam em conflitos/bind; **não conseguem atravessar AP/client isolation ou filtros UDP do roteador**. O overlay exibe tentativa N/3, erro e recomendação de verificar AP Isolation. Se todas falharem antes da jogatina, restaura o estado inicial e retorna ao **Lockstep na mesma conexão TCP**, sem repetir transferência da ROM. Protocolo WSS online v6 permanece intacto.

**Desempenho:** o benchmark de save+load ocorre antes de iniciar a sessão. Acima de 8 ms, o Gekko ativa `limited_saving` para reduzir frequência de snapshots. A versão pinned upstream não garante a detecção nativa de desync nesse modo e, por isso, `desync_detection` é desligada enquanto `limited_saving` está ativa. O overlay apresenta Limited Saving ON/OFF e a medição de save/load; não afirmar proteção de desync que não foi executada. O Gekko inclui ajuste conservador de input delay após janelas de jitter/rollbacks excessivos. Se o RTT ficar acima de 150ms por três amostras consecutivas, só a **apresentação de vídeo** cai para 1/2; `retro_run()`, áudio, entradas, save states e checksums continuam normalmente. Volta a apresentar todos os quadros após cinco amostras abaixo de 110ms. Picos de processamento de Gekko e quadros com >500 ms são registrados no overlay/diagnóstico.

**Limites:** nenhuma métrica isolada comprova causalidade das travadas. Capture o overlay antes/depois da oscilação para distinguir rede (RTT/jitter) de serialização/CPU. Recuperação de desync completa por troca de estado assinado não está pronta; não seria seguro simplesmente carregar save state em um único peer. Um pacote perdido por dezenas de segundos ou mudança de rede pode terminar a sessão. Ver docs/BACKGROUND.md e docs/DEVICE_TESTING.md.
