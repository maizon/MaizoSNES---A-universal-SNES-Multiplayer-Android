# MaizoSNES — Android SNES multiplayer

**Estado da branch `work/android-foundation`: candidato à versão 1.0, ainda sem release final publicada.** O usuário confirmou em teste físico que gameplay LAN e online e os recursos usados funcionaram bem; identificou uma falha específica: salas LAN com ROM diferente não permitiam solicitar a transferência autorizada. A interface de salas foi ajustada para permitir entrar quando **Aceitar ROM** está habilitado. Isso não dispensa consentimento explícito do host e cliente, nem a verificação SHA-256 antes da partida. A correção precisa de novo teste físico em dois aparelhos antes da etiqueta 1.0.

## Entrar em sala LAN sem a mesma ROM

1. O anfitrião abre a ROM e cria uma sala LAN; ao iniciar, **autoriza explicitamente** compartilhar uma ROM que tem direito de redistribuir.
2. O cliente habilita **Aceitar ROM** nas opções multiplayer e atualiza as salas LAN.
3. O cliente pode tocar em **Entrar** mesmo quando o aviso mostra **ROM DIFERENTE**; o protocolo verifica a ROM e oferece a transferência autorizada somente em LAN privada.
4. O cliente confirma a oferta. A ROM recebida é conferida por SHA-256 antes da sincronização inicial e da negociação Gekko UDP.
5. Se não houver autorização, a conexão é recusada com uma mensagem explícita; não existe compartilhamento automático de ROM comercial.

O modo manual IP:porta, o rollback GekkoNet e o fallback TCP continuam disponíveis. **Não contém ROMs nem BIOS.** O artefato `app-debug` do CI não deve ser anunciado como release estável assinada. Veja [validação](docs/VALIDATION.md), [testes em aparelhos](docs/DEVICE_TESTING.md) e [licenças](THIRD_PARTY_NOTICES.md).

---

## Preview WebRTC rollback online (experimental, 2026-10-19)

Na branch `work/android-foundation`, o MaizoSNES tenta WebRTC DataChannel
(`ordered=false`, `maxRetransmits=0`) entre dois aparelhos em redes
diferentes, com Railway HTTPS/WSS APENAS para negociação SDP/ICE e fluxo
seguro de ROM/estado inicial. O GekkoNet usa o canal SCTP/DTLS para dados de
rollback; se ICE falhar, o jogo continua com Lockstep pelo WSS original.
LAN/IP manual continuam usando GekkoNet UDP direto e fallback TCP.

SDK Android WebRTC: `io.github.webrtc-sdk:android-prefixed-stripped:150.7871.01`.
STUN de Google/Cloudflare; TURN não configurado sem credenciais. NAT simétrico
e redes restritivas podem manter o Lockstep. As funcionalidades são
**experimentais até teste real**: CI não garante P2P funcional em celulares.
Veja [testes guiados](docs/DEVICE_TESTING.md), [checklist](docs/VALIDATION.md)
e [arquitetura](docs/NETPLAY.md).

# MaizoSNES — emulador SNES Android

Aplicativo **experimental** de emulação SNES para Android ARM64 com Snes9x 1.63 via libretro, feito em Kotlin/Compose + C++/NDK. Não inclui ROM, BIOS, capa, dados de jogo nem conteúdo proprietário.

## v0.8.0 Release Candidate — interface premium e salas online reparadas

**GitHub Release:** `v0.8.0-rc.1` (Android ARM64 experimental, sem ROM).
O registro oficial aponta para o relay HTTPS hospedado no Railway. 
Na aba **Online**, carregue uma ROM antes de criar a sala; a interface mostra
o estado da conexão, um código e a opção **Cancelar sala / Desconectar**
enquanto aguarda o segundo jogador. O botão Criar não parece travado: ele
é desativado ao existir uma sessão ativa.

**Dois celulares:** ambos devem usar APK com protocolo **MZNP v5** e
a mesma ROM/mod (SHA-256). Quando só as opções de núcleo diferirem, o
convidado valida e adota temporariamente os valores do host; seu perfil
pessoal é restaurado ao desconectar. ROMs comerciais não são transferidas.

Novidades: áudio com buffer 25/40/60 ms, presets de imagem leve/GPU
(OpenGL ES experimental), central de ajustes em chips, opção de restaurar
configurações do núcleo, sala LAN persistente até cancelamento e redução do
trabalho com hash de estado durante netplay.

**Importante:** MZNP v5 é lockstep com entrada antecipada, sem RTT obrigatório por quadro. Jitter acima do buffer pode reduzir FPS; não há rollback ou garantia de latência
zero. Os testes CI verificam HTTP/TCP e jogo determinístico simulado; a
jogabilidade em dois smartphones precisa de validação física. Relay TCP
sem TLS fim a fim. APK da CI usa assinatura DEBUG não persistente.
Consulte `docs/CHANGELOG_0.8.md` e `docs/SUPER_PROMPT_PREMIUM.md`.

## Atualização 0.7 Preview — Auto Lobby + cheats online sincronizados

- LAN automática identifica prefixo SHA-256 e uso de cheats, além do título; evita entrar em salas com mods diferentes. IP/porta aparecem apenas nos modos avançados.
- **Jogo Rápido**: botão de 1 toque tenta conexão LAN do mesmo jogo, sala online compatível e, sem outras salas, cria uma automaticamente; sem servidor publicado, cria sala LAN.
- Modo **Automático (recomendado)** reúne salas LAN e servidores oficiais cadastrados pelo próprio MaizoSNES. Nenhum IP/URL é solicitado ao jogador comum. O catálogo `server/registry.json` começa vazio até operadores autorizados realmente implantarem instâncias. Listas de terceiros não são servidores compatíveis automaticamente.
- Pesquisa simultânea em relays HTTPS autorizados (até 8 endpoints), lista salas por região, mostra tempo de resposta HTTP, hash parcial da ROM e aviso de cheats; pré-verificação de compatibilidade por SHA-256 da ROM antes de entrar. A versão MZNP v5 ainda valida o SHA-256 **completo**.
- **Cheats online opcionais, sincronizados ANTES do primeiro quadro**: o host seleciona os códigos na biblioteca; ambos precisam autorizar nas opções multiplayer. Os dois núcleos aplicam a mesma lista validada ou encerram a conexão antes de jogar. Mudança de cheats durante a partida permanece bloqueada.
- Patches IPS/BPS existentes funcionam em partidas online **quando ambos têm a mesma ROM já modificada** (SHA-256 idêntico). O programa não envia automaticamente patches/ROMs.
- Modo antigo por IP, LAN automática e relay manual preservados; configurações avançadas recolhíveis.
- Planos e limitações: `docs/MULTIPLAYER_PREMIUM_PLAN.md`. Sem relays implantados/credenciais, não é possível prometer salas públicas funcionando na internet.

## Atualização 0.6 Preview — controle moderno e servidor por IP

- **9 layouts de toque** persistidos por jogo/preset: Maizo, CTR, ARMSX, PPSSPP, **PlayStation (△○✕□), Xbox Series (ABXY), Nintendo Switch, Arcade RGB e Steam Deck**. Os cinco modernos usam botões em losango, L/R no topo e temas vetoriais diferentes; botões continuam mapeados aos mesmos bits SNES. O editor mantém posições individuais, escala e transparência.
- Servidor de salas remotas: informe **apenas o endereço IP**, por exemplo `192.0.2.10` (HTTP porta 8787 automática), ou URL `http://192.0.2.10:8787` / servidor HTTPS. O serviço é auto-hospedado e ambos os jogadores usam conexão de saída; o túnel ainda não é criptografado.
- Testes unitários dos mapeamentos de controles e validação das URLs e portas de servidor.

## Atualização 0.5 Preview — Internet / GPU / MODs / controles

- **Descoberta mundial de salas por servidor próprio:** escolha o modo `Servidor de salas (NAT/CGNAT)`, informe apenas o IP ou URL HTTP do servidor, busque salas e entre com um toque. Um servidor Node.js sem dependências está em `server/relay.js`. Tanto P1 como P2 se conectam de saída ao relay TCP, evitando portas no roteador. **É relay, não P2P direto**. Instruções: `docs/INTERNET_RELAY.md`.
- **Vídeo com duas pipelines:** CPU RGB565 (padrão) ou OpenGL ES 2 (experimental, ideal para teste em Mali). GPU opcional com escala nearest, bilinear, suavização de vizinhos, CRT curvo, LCD, nitidez, preto e branco e saturação. CPU opcional com saída framebuffer 1×–4× (duplicação de pixels), scanlines, suavização, grade, monocromático, sharpen e triad RGB. Não aumenta resolução interna/visão lateral do jogo.
- **Mods fornecidos pelo usuário:** aplica arquivos IPS ou BPS a uma ROM da biblioteca, validando CRC do BPS, salvando **cópia** com sufixo `_mod`. A ROM original e seus saves são preservados. Não inclui patches nem alterações comerciais. Mods widescreen são **específicos de cada jogo**; ajustar proporção não é widescreen hack.
- **Controles visuais distintos:** quatro skins vetoriais originais desenhadas em tempo real (Maizo, CTR-like, ARMS-like, PPSSPP-like) e prévias no menu; troca de formas, contornos, transparência e posições. **Não são cópias dos arquivos gráficos originais**.
- **Busca de ROMs por pasta do aparelho**, subpastas permitidas via SAF, ou varredura opcional do armazenamento compartilhado com autorização especial do Android (MANAGE_EXTERNAL_STORAGE no Android 11+, sem acesso a pastas privadas).
- **Créditos para Maicon Nascimento** e botão Instagram, com atribuição aos demais projetos.

O modo de rede pública é experimental: sem TLS no túnel TCP, contas ou controle de abuso. Para uso com pessoas não confiáveis configure um relay com VPN privada e autenticação adicionais. O build CI testa o servidor em loopback, mas não confirma estabilidade de GPU ou internet em smartphones reais. Não fornecemos APK comercial ou ROM.

## Versão 0.9 RC — correção de FPS no multiplayer (MZNP v5)
 
**Controles visuais de fábrica:** preset VirtualGamePad Mobile. O repositório
de referência https://github.com/kitswas/VirtualGamePad-Mobile contém
controles como composables e código GPLv3, não um pacote de PNGs pronto para
uso em software Snes9x não comercial. O MaizoSNES redesenha o gamepad com
código próprio, mantendo os principais tamanhos/posicionamentos relativos e
mapeando corretamente todas as ações do SNES; os outros nove presets e editor
continuam disponíveis.


**Relato real de teste do desenvolvedor:** Android + VM conectam pelo online,
mas o gameplay reduz a aproximadamente **2 FPS**. Causa arquitetural
identificada: até v4 a thread de jogo envia entrada do quadro e **espera uma
resposta TCP de ida e volta para cada quadro**. Um RTT de 500ms limita
o jogo a cerca de 2 FPS, mesmo quando o Snes9x isoladamente roda a 60.

A versão **MZNP v5** negocia o RTT **antes** da partida e transmite
entradas para quadros futuros com *input delay* limitado de **2 a 48 quadros**.
Assim os dois aparelhos podem continuar em ~60 quadros/s se a rede entregar
as entradas dentro do buffer negociado e se ambos os núcleos forem rápidos.
Existe uma troca: conexões distantes exigem mais atraso no controle.
O cliente recebe o delay selecionado pelo host. O menu Multiplayer permite escolher entre Resposta rápida, Equilibrado e Conexão instável, com tradeoff explícito de latência e jitter. Hash periódico mantém detecção
de dessincronização; não foi implementado rollback/predição de entrada. O
transporte direto LAN continua suportado; o modo Internet/CGNAT continua
através de relay público, sem prometer perfuração de NAT para conexão direta.

CI adiciona um **teste de duas sessões com 200ms de RTT artificial** para
evitar regressão ao gargalo de um roundtrip por quadro. A validação completa
a 60 FPS exige **teste Android+VM e dois Androids reais**.

## Funcionalidades implementadas no código

- Biblioteca privada: importar ROMs `.sfc`, `.smc` ou ZIP com uma ROM, listar, renomear e abrir sem picker repetitivo.
- Tela de jogo própria, landscape imersivo, vídeo SurfaceView/ANativeWindow e menu superior recolhível a um círculo discreto semitransparente.
- Aspectos 4:3, pixel perfect com escala inteira de referência, 16:9, stretch e fill.
- Controles táteis semitransparentes, diagonais e multitouch; **quatro presets originais** (Maizo, inspirado em CTR, inspirado em ARMSX1 e inspirado em PPSSPP), com edição de grupos e de botões individuais.
- **Editar** diretamente na tela de jogo: arrastar grupos D-pad e botões, salvar layout; tamanho/posição/transparência ajustáveis também pelos sliders, com preferências globais e por ROM.
- Input físico por joystick/gamepad Android e teclado; multitarefa do primeiro e segundo gamepads separada, evitando mistura de máscaras.
- Áudio AudioTrack estéreo com buffer reduzido e modo low-latency quando o Android oferece.
- Ajustes por categoria (vídeo, áudio, controles, cheats, rede, núcleo, estados e DEV), opções reais expostas via libretro.
- **Cheats automáticos**: identifica ROM pelo nome ou título interno SNES, encontra códigos na base libretro-database CC BY-SA 4.0 via HTTPS, permite selecionar quais ativar e mantém cache para uso offline posterior. A busca é heurística: checar revisão/região.
- Três slots de save state mais importação/exportação e bloqueio de restore com ROM divergente.
- **Multiplayer local sem internet**: controle tátil P1 + gamepad P2, ou dois gamepads em P1/P2.
- **Multiplayer experimental LAN/online** com **descoberta automática na rede Wi-Fi por UDP**, seleção de sala com um toque ou IP/porta manual por TCP: host P1, cliente P2, SHA-256 da ROM e das opções, sincronização inicial de estado, entradas quadro a quadro e detecção periódica de divergência; conexão direta por IP e porta, sem servidores externos. Opcionalmente envia ROM **apenas quando host e cliente autorizam e a ROM pode ser redistribuída legalmente**, somente entre IPs privados, com limite de 64 MiB e SHA-256. Desativado por padrão.
- Testes JVM de protocolo loopback (dois peers) e rejeição de ROM divergente.

## Estado de validação

A versão anterior do núcleo single-player foi testada pelo desenvolvedor no Android e executou jogos e save states. **O código de netplay, editor por arrasto, ROM ZIP e UI desta revisão não foi validado em dois aparelhos Android**. Apenas a CI e os testes de loopback não demonstram confiabilidade e latência de rede. Por isso o multiplayer continua rotulado **experimental**.

Não existe "delay zero" pela internet: MZNP v5 antecipa entradas, mas espera quando o jitter ultrapassa o buffer. Para uma experiência próxima de resposta local online é necessário implementar e validar predição/rollback. Também não existe matchmaking, NAT traversal, criptografia nem segurança para sala aberta à internet pública.

## Build

Requisitos: JDK 17, Android SDK 35, NDK **26.3.11579264**, CMake **3.22.1** e Git:

```bash
bash scripts/fetch_snes9x.sh
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

A compilação via GitHub Actions fornece o artefato `app-debug` contendo `app-debug.apk` quando verde. Está configurado somente `arm64-v8a`, sem ARMv7. O renderer atual não é OpenGL/Vulkan.

## Usar multiplayer

**Dois controles locais:** importe um jogo compatível com dois jogadores, abra-o, volte à aba Multiplayer, habilite "Ativar porta 2" e conecte um ou dois controles Bluetooth/USB.

**Dois celulares:** abra a mesma ROM em ambos e instale a mesma versão do APK. Um toca em **Criar sala**; o outro vê a sala aparecer automaticamente na rede e toca nela. Se a busca não funcionar, use **IP/porta** manual.

**Importação opcional pelo host:** apenas para ROM homebrew, domínio público ou com licença para redistribuir: o host marca **Compartilhar minha ROM autorizada**, e o cliente marca **Aceitar ROM autorizada**. Quando os SHA-256 diferem, o host transfere via rede privada e o cliente grava a ROM no armazenamento interno e a abre. Sem as duas autorizações, ROMs diferentes são rejeitadas. **Não habilitar esse recurso para ROM comercial sem direito de redistribuição.** É necessário estar na mesma rede Wi-Fi sem isolamento entre clientes. Fora da LAN é necessária uma conexão TCP por IP/porta acessível (ex.: VPN privada). **Não exponha a porta experimental diretamente à internet pública.**

Cheats usam internet somente no primeiro download; o catálogo e arquivos consultados ficam em cache interno. Veja [cheats e descoberta LAN](docs/CHEATS_AND_LAN.md).

Detalhes e riscos: [Netplay](docs/NETPLAY.md), [referências e segurança](docs/REFERENCES_AND_SECURITY.md), [roteiro de testes reais](docs/DEVICE_SMOKE_TEST.md), [arquitetura](docs/ARCHITECTURE.md).

## Licenças

Snes9x possui licença específica de **uso não comercial**; o filtro NTSC inclui avisos separados. Consulte [LICENSE_DECISION.md](LICENSE_DECISION.md), [LICENSES](LICENSES), [VENDORED.md](VENDORED.md) e [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). **Não monetizar ou distribuir jogos protegidos** sem direitos ou autorização. Código de RetroTouch/RetroArch não foi copiado; referências são de design/documentação.

## Consolidação 0.3 — conexão, vídeo, capas e perfis

- **Presets editáveis:** Maizo Original, CTR Racing, ARMSX Compact e PPSSPP Comfort; perfis autorais, sem código ou imagens reproduzidos dos emuladores usados como referência. Posicionamento de ABXY, L/R e Start/Select individual, grupos e D-pad juntos; persistência dos presets por jogo e configurações globais.
- **Modos sem perder progresso:** Offline local, LAN automática, LAN manual e Internet/VPN. Host/IP/porta são memorizados separadamente para cada modo. Mudanças com sessão conectada são recusadas para evitar interromper jogo.
- **Métricas reais do app:** HUD opcional com FPS do núcleo, tempo médio gasto na emulação, tempo médio de atualização do vídeo e áudio enfileirado. Sessões remotas exibem espera pelo peer, picos, FPS efetivo e quadros atrasados. São contadores coletados em runtime, não benchmarks de dispositivos pré-testados.
- **Pacing:** loop offline usa próximo deadline acumulado e se recupera de atrasos sem acelerar relógio do emulador. O modo Internet mantém o protocolo determinístico MZNP v5, usa TCP_NODELAY, best-effort prioridade de baixa latência e timeout tolerante a jitter; não inclui rollback ou servidor relay.
- **Filtros opcionais RGB565:** Original (fast path), scanlines, suavização horizontal e combinação leve. Aplicados na saída de vídeo, sem inventar suporte Vulkan/OpenGL ou forçar filtros que prejudiquem FPS.
- **Cheats com checksum:** primeiro procura identificação No-Intro pelo tamanho + CRC32 + SHA-1 (com suporte a cabeçalho SNES de 512 bytes), usando base pública fixada; se não reconhecer, mantém a busca por nome e informa que é heurística.
- **Capas opcionais automáticas:** consulta a biblioteca de thumbnails do Libretro por nome padronizado e guarda PNGs no cache interno. Não empacota capas de terceiros no APK. A disponibilidade e as condições de uso das artes dependem do respectivo titular.
- **Backup de segurança multiplayer:** estado salvo antes e depois da sessão sem sobrescrever os três slots manuais; é possível restaurar explicitamente a última cópia do jogo pelo menu Estados.
- **Menu DEV:** pode ser desbloqueado com PIN local de conveniência `9090`, habilitando configurações avançadas de compartilhamento de ROMs *cuja redistribuição seja autorizada*. O PIN não muda direitos autorais nem criptografa a rede. O app não inclui e não distribui ROMs comerciais por padrão.

Referências e limites: [docs/FINAL_UPDATE.md](docs/FINAL_UPDATE.md).


### ICE/TURN online (experimental, 19/10/2026)

WebRTC P2P agora usa troca explícita de candidatos por trickle ICE via Railway WSS,
com prazos finitos e nova tentativa relay-only quando TURN for informado.
Google/Cloudflare STUN, Open Relay com credenciais demonstrativas públicas
(best-effort, disponibilidade NÃO garantida) e credenciais Metered pessoais
via GET /api/ice no serviço Railway existente. Ajustes → Rede permite URIs
STUN/TURN próprias e TESTAR COLETA ICE. **Uma coleta bem-sucedida NÃO garante
conexão entre dois celulares**: requer teste físico e validação do overlay.
Em NAT/ICE indisponível, a partida usa Lockstep TCP via Railway sem inventar
modo UDP. Não foi provisionado Fly.io pago nem existe SLA de TURN público.
Configuração: docs/TURN_SETUP.md; teste: docs/DEVICE_TESTING.md.


## MaizoSNES — correções LAN/ROM/rollback e segundo plano (20/10/2026)

Atualização experimental em `work/android-foundation`:

- LAN/IP manual: ROM e direitos verificados pelo TCP **ANTES** do GekkoNet UDP. Convidado sem ROM pode aceitá-la mediante direitos de redistribuição do host e aceite próprio, seguido de hash SHA-256 e estado inicial. Sem PIN 9090.
- Handshake GekkoNet UDP com até **3 tentativas/portas** por sessão, limitadas no tempo, negociadas por TCP; erro de UDP volta para Lockstep na mesma sala sem retransmitir ROM. AP Isolation/firewall não podem ser contornados simplesmente trocando porta.
- Online WebRTC/Gekko: controle adaptativo de atraso, medição de picos de travada >500 ms, estados de save/load no overlay, *Limited Saving* quando medição >8ms e *render skip* em RTT alto sem pular emulação/áudio.
- Troca de aplicativo: foreground service com notificação, CPU/Wi-Fi wake lock e jogo de rede em andamento com áudio suspenso; controle **Multiplayer em segundo plano** ativado por padrão, desligável no app.
- Tests JVM cobrem negociação de dois peers com fake JNI, retries, fallback, ROM compartilhada autorizada, política de vídeo e política de background. **Não equivalem a teste de rede em dois aparelhos reais.**

Veja [Netplay](docs/NETPLAY.md), [Segundo plano](docs/BACKGROUND.md), [Validação](docs/VALIDATION.md) e [Teste guiado](docs/DEVICE_TESTING.md).
