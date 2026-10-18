# Multiplayer (código experimental)

## Situação verificada

Esta revisão implementa **código** para partidas locais com dois controles físicos e para dois aparelhos por TCP, na LAN ou através de um endereço IP TCP alcançável. O aplicativo **ainda exige testes reais em dois Androids**, inclusive de determinismo, troca de ROM, latência, entrada/saída de app e Wi-Fi instável. Nunca confundir CI verde/teste de loopback com multiplayer validado em dispositivos.

### Modo local offline

- Ative porta 2 na aba Multiplayer.
- Dois gamepads conectados: menor ID de dispositivo = P1; segundo = P2.
- Um gamepad + tela tátil: touch = P1; gamepad = P2.
- Jogos individuais não viram multiplayer; depende do suporte nativo do cartucho.
- Ainda falta UI de remapeamento por dispositivo, testes de hot-plug e multitap 3–5 jogadores.

### LAN e online por endereço

1. Instale **exatamente a mesma versão do APK** em ambos os aparelhos.
2. Importe a **mesma ROM legítima**, abra-a uma vez em cada aparelho e volte à aba Multiplayer.
3. Confira que as opções do núcleo são idênticas, incluindo região e hacks.
4. No anfitrião (P1), escolha porta TCP (padrão 55435) e **Criar sala**.
5. No cliente (P2), digite o IPv4 local indicado no anfitrião e pressione **Entrar na sala**.
6. Após handshake e sincronização de estado, ambos entram na tela de jogo. Para sair, feche a partida pela navegação do app; sair do app/desconectar também encerra a sessão.

Online remoto: exige IP/porta TCP realmente acessível, tipicamente por uma VPN privada confiável ou encaminhamento de porta; roteadores com CGNAT impedem conexão direta. O APK não oferece relay, descoberta automática, matchmaking, STUN/TURN ou TLS. **Não recomendamos expor a porta diretamente à internet aberta**, pois o protocolo experimental não tem autenticação nem criptografia.

## Contrato de sincronização (MZNP v1)

- TCP em ordem, `TCP_NODELAY`, timeout de 10 s ao conectar e até 20 s para negociação (5 s por leitura de frame).
- Host envia magic `MZNP`, versão 1, SHA-256 exata do arquivo ROM e SHA-256 do identificador fixo de núcleo + opções ordenadas; cliente compara e devolve os próprios dados.
- ROM ou opções divergentes cancelam a sessão antes de um único frame.
- Host transfere estado serializado **limitado a 64 MiB + cabeçalho**; cliente restaura e confirma.
- Host joga na porta libretro 0, cliente na porta 1. Em cada quadro ambos enviam sequência monotônica e máscara local (16 bits), recebem a remota e executam **um único** `retro_run` após a troca, sob mutex do núcleo.
- Periodicamente, a cada 120 frames, compara-se um fingerprint de 64 bits do save state; divergência é falha fatal informada, não é escondida.
- Ao desconectar, sockets são fechados, loop termina, thread offline é restaurada. Reset, estados e alterações de opções são bloqueados durante sessão ativa.
- Não inclui pausas sincronizadas, rejoin, espectadores, salas públicas nem rollback.

## Latência: limite físico e arquitetura futura

Lockstep é determinístico, mas **não consegue delay zero**: precisa aguardar o input remoto do quadro. TCP_NODELAY reduz espera artificial do Nagle; não elimina RTT, jitter, perda e distância. Latência WAN normalmente é perceptível, mesmo quando LAN funciona bem.

O caminho para respostas locais muito rápidas é **predição + rollback** com histórico de estados e inputs. Referência conceitual: https://docs.libretro.com/development/retroarch/netplay/ . A documentação RetroArch menciona replay/rollback com frames sincronizados. O código do RetroArch **não foi copiado** aqui, e sua arquitetura/obrigações de licença devem ser estudadas separadamente. Além do rollback, seria necessário silenciar áudio/vídeo durante replay, limitar histórico, marcar checkpoints, controlar SRAM e comprovar determinismo em aparelhos diferentes.

## Testes

- `./gradlew testDebugUnitTest` contém teste local TCP loopback com dois peers e rejeição de ROM divergente (sem ROM comercial).
- CI valida compilação e protocolo básico **somente no JVM**; não mede input-lag, Wi-Fi, NAT, velocidade nem funcionamento de jogos.
- Em hardware real, testar troca de inputs simultâneos, dez minutos por jogo, NTSC/PAL, reconnect, redes com perda/jitter, hashes e término de sessão. Registrar APK SHA, Android, modelo e logs.
