# Plano salvo — MaizoSNES Premium Netplay (2026-10-18)

## Experiência-alvo

**Jogar → Online → Salas → Entrar** (sem IP, sem URL, sem abrir portas, sem conta obrigatória).

- Lobby misto, mas identificado: salas **LAN** e salas **Internet** (Brasil / América do Norte / Europa).
- Botões “Criar sala”, “Entrar”, “Jogo rápido”, “Convidar” e modo “Privada por código”.
- Exibir apenas o nome do jogo, revisão/hash compatível, jogadores 1/2, cheats/mods habilitados, região, qualidade da conexão e estado da sala. Nunca exibir endereço/IP de outro jogador na UI padrão.
- Quando ambos estão na mesma rede, priorizar LAN; caso contrário tentar conexão direta por ICE/STUN se existir backend adequado e fallback confiável em relay quando NAT/CGNAT impedir conexão direta.
- Estados coerentes: buscando, conectando, aguardando P2, verificando ROM, negociando cheats, sincronizando estado, jogando, reconectando, desconectado.
- Progresso local, save states, controles, biblioteca e opções devem sobreviver a mudanças de modo.

## Infraestrutura de verdade

1. **Registro oficial** `server/registry.json` distribuído via HTTPS e controlado pelo projeto, com URLs de servidores MaizoSNES efetivamente autorizados, monitorados e operados. Android lê o registro, pesquisa servidores até um limite, lista salas e prioriza pelo tempo de resposta. Nenhuma URL digitada pelo usuário.
2. **Servidor de salas e relay** próprio em Node.js (`server/relay.js`): conecta dois TCPs de saída, preservando MZNP. É NAT-friendly mesmo em CGNAT, mas **não é P2P direto**. Separar descoberta (HTTPS) de túnel TCP; proteger túnel com TLS e token antes de abrir servidores para pessoas desconhecidas.
3. Para **P2P real**: camada independente de sinalização e transporte WebRTC DataChannel/ICE/STUN com TURN de fallback. STUN público sozinho NÃO mantém a lista de salas, NÃO é servidor TURN e NÃO garante conexão. Chaves efêmeras TURN permanecem no backend e não no APK. Implementar só após protótipo e testes reais.
4. Multirregião: operar nós próprios Brasil/NA/EU (ou parceiros autorizados); atualizar o registro oficial, acrescentar healthcheck, TTL, rate limiting, IP ban, autenticação opcional, métricas agregadas e failover. **Nenhum servidor público compatível foi confirmado neste registro; não inventar nem usar servidores de terceiros sem consentimento/compatibilidade.**
5. A falha de conexão automática oferece alternativas claras: LAN descoberta, servidor manual, VPN ou repetir, sem interromper saves.

## Qualidade LAN

- Descoberta redundante UDP (já existe) + Android NSD/mDNS; lidar com isolamento Wi-Fi, hotspots, mudanças de endereço, IPv4/IPv6 e cancelamento da busca.
- Exibir jogo e versão, verificar checksum antes de entrar, PIN opcional para sala privada, esconder sala, reconexão e indicação de desconexão.
- Detectar disponibilidade e latência, reconectar mantendo estado em checkpoint, com consentimento de ambos.

## Cheats e patches

- O patch IPS/BPS é aplicado **sobre cópia local**. A partida só inicia se o **SHA-256 da ROM já modificada** coincidir, nunca confundir nome de jogo com identidade binária.
- Cheats por host precisam ser negociados e **aplicados aos dois núcleos antes do quadro zero**, com consentimento explícito de ambas as partes e validação de códigos/tamanho. Nunca habilitar cheats locais unilateralmente nem durante partida lockstep; pausas e mudanças devem ser mensagens de protocolo sincronizadas por frame. Sem consentimento, rejeitar **somente a partida com cheats** ou oferecer partida limpa.
- Catálogo automática por checksum exato quando possível; código inválido ou divergência causa bloqueio/retorno a estado limpo; desincronização interrompe a sessão com diagnóstico.
- Considerar etiquetas `SEM MOD`, `MOD SHA-256`, `CHEATS ATIVOS` nos cards de lobby, sem distribuir ROMs comerciais.

## Latência e robustez

- **Fase 1:** TCP lockstep com telemetria atual, heartbeat, timeouts coerentes, ping real e jitter, melhor seleção de relay, proteção de frame e checksum.
- **Fase 2:** isolamento da camada de transporte e snapshots determinísticos, áudio/vídeo suprimidos no replay, buffer de inputs, teste de rollback e resync. Sem alegação de “lag zero”. Rollback pode não ser viável em todos os jogos/hardware.
- Testar em 2 Androids reais, redes diferentes, 4G/5G/CGNAT, Wi-Fi isolado, 30min, SuperFX/PAL/NTSC, 60fps/50fps, save restore, perda de pacotes e reconexão.

## UX premium recomendada

- Salas públicas/privadas com link e QR; amigos e favoritos, apelido opcional, filtros por jogo, região e ping; sala rápida e “Continuar partida”.
- Assistente de compatibilidade ROM/mod/cheat com passos claros, sem arquivos proprietários pré-carregados.
- LED de qualidade de conexão, diagnóstico automático, indicador da rota (LAN/direta/relay) e opção de economizar dados.
- Controles 9 presets vetoriais e remapeamento, opções de acessibilidade e overlay discreto.
- Editor de gráficos por jogo, shaders GPU + CPU seguro por padrão; benchmark Mali e fallback.
- Backups com confirmação e compatibilidade por fingerprint da ROM.

## Milestones e critérios

- M1 — **Registro e Auto Lobby**: código de descoberta no APK, manifest oficial editável e testes de parsing/ranking; CI verde. Sem servidor cadastrado, UI sinaliza honestamente e mantém LAN.
- M2 — **Online Cheats v3**: implementação em revisão/CI, com negociação opt-in antes do quadro zero, validação de códigos e teste de recusa/aceite. Requer validação em 2 aparelhos para marcar concluído.
- M3 — **Servidores públicos reais**: deploy administrado, HTTPS e transporte criptografado/autenticação, pelo menos um nó em produção, healthcheck, política de uso, testes na Internet real. Depende de infraestrutura e credenciais do operador.
- M4 — **P2P ICE + TURN** e matchmaking avançado com engenharia de transporte/testes.
- M5 — **Rollback/reconnect** e otimizações comprovadas por benchmarks.

**Não marcar M3/M4/M5 como entregues sem servidor real implantado e testes comprovados.**
Preservar licenças e autoria de Snes9x/Libretro e não incluir jogos comerciais.
