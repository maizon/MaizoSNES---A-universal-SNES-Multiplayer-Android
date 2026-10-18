# MaizoSNES — emulador SNES Android

Aplicativo **experimental** de emulação SNES para Android ARM64 com Snes9x 1.63 via libretro, feito em Kotlin/Compose + C++/NDK. Não inclui ROM, BIOS, capa, dados de jogo nem conteúdo proprietário.

## Atualização 0.5 Preview — Internet / GPU / MODs / controles

- **Descoberta mundial de salas por servidor próprio:** escolha o modo `Servidor de salas (NAT/CGNAT)`, informe apenas o IP ou URL HTTP do servidor, busque salas e entre com um toque. Um servidor Node.js sem dependências está em `server/relay.js`. Tanto P1 como P2 se conectam de saída ao relay TCP, evitando portas no roteador. **É relay, não P2P direto**. Instruções: `docs/INTERNET_RELAY.md`.
- **Vídeo com duas pipelines:** CPU RGB565 (padrão) ou OpenGL ES 2 (experimental, ideal para teste em Mali). GPU opcional com escala nearest, bilinear, suavização de vizinhos, CRT curvo, LCD, nitidez, preto e branco e saturação. CPU opcional com saída framebuffer 1×–4× (duplicação de pixels), scanlines, suavização, grade, monocromático, sharpen e triad RGB. Não aumenta resolução interna/visão lateral do jogo.
- **Mods fornecidos pelo usuário:** aplica arquivos IPS ou BPS a uma ROM da biblioteca, validando CRC do BPS, salvando **cópia** com sufixo `_mod`. A ROM original e seus saves são preservados. Não inclui patches nem alterações comerciais. Mods widescreen são **específicos de cada jogo**; ajustar proporção não é widescreen hack.
- **Controles visuais distintos:** quatro skins vetoriais originais desenhadas em tempo real (Maizo, CTR-like, ARMS-like, PPSSPP-like) e prévias no menu; troca de formas, contornos, transparência e posições. **Não são cópias dos arquivos gráficos originais**.
- **Busca de ROMs por pasta do aparelho**, subpastas permitidas via SAF, ou varredura opcional do armazenamento compartilhado com autorização especial do Android (MANAGE_EXTERNAL_STORAGE no Android 11+, sem acesso a pastas privadas).
- **Créditos para Maicon Nascimento** e botão Instagram, com atribuição aos demais projetos.

O modo de rede pública é experimental: sem TLS no túnel TCP, contas ou controle de abuso. Para uso com pessoas não confiáveis configure um relay com VPN privada e autenticação adicionais. O build CI testa o servidor em loopback, mas não confirma estabilidade de GPU ou internet em smartphones reais. Não fornecemos APK comercial ou ROM.

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

Não existe "delay zero" pela internet: lockstep aguarda entradas remotas. Para uma experiência próxima de resposta local online é necessário implementar e validar predição/rollback. Também não existe matchmaking, NAT traversal, criptografia nem segurança para sala aberta à internet pública.

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
- **Pacing:** loop offline usa próximo deadline acumulado e se recupera de atrasos sem acelerar relógio do emulador. O modo Internet mantém o protocolo determinístico MZNP v2, usa TCP_NODELAY, best-effort prioridade de baixa latência e timeout tolerante a jitter; não inclui rollback ou servidor relay.
- **Filtros opcionais RGB565:** Original (fast path), scanlines, suavização horizontal e combinação leve. Aplicados na saída de vídeo, sem inventar suporte Vulkan/OpenGL ou forçar filtros que prejudiquem FPS.
- **Cheats com checksum:** primeiro procura identificação No-Intro pelo tamanho + CRC32 + SHA-1 (com suporte a cabeçalho SNES de 512 bytes), usando base pública fixada; se não reconhecer, mantém a busca por nome e informa que é heurística.
- **Capas opcionais automáticas:** consulta a biblioteca de thumbnails do Libretro por nome padronizado e guarda PNGs no cache interno. Não empacota capas de terceiros no APK. A disponibilidade e as condições de uso das artes dependem do respectivo titular.
- **Backup de segurança multiplayer:** estado salvo antes e depois da sessão sem sobrescrever os três slots manuais; é possível restaurar explicitamente a última cópia do jogo pelo menu Estados.
- **Menu DEV:** pode ser desbloqueado com PIN local de conveniência `9090`, habilitando configurações avançadas de compartilhamento de ROMs *cuja redistribuição seja autorizada*. O PIN não muda direitos autorais nem criptografa a rede. O app não inclui e não distribui ROMs comerciais por padrão.

Referências e limites: [docs/FINAL_UPDATE.md](docs/FINAL_UPDATE.md).
