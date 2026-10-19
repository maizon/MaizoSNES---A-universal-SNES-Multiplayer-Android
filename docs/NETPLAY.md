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
