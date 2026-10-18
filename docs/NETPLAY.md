# Estado e desenho do Netplay LAN

## Estado atual

Não existe partida LAN funcional nesta revisão. A tela Multiplayer não abre sockets nem afirma falsamente que uma sala está conectada. R8 fica explicitamente adiado por dependências técnicas que afetam determinismo.

## Bloqueios concretos identificados no código atual

1. O callback de input aceita apenas a porta libretro 0; retorna zero para portas diferentes. O segundo jogador ainda não chega ao núcleo.
2. O loop executa retro_run por temporização local e não possui contador de frame exposto nem barreira que aguarde o input remoto do mesmo frame.
3. Não há handshake de ROM, versão do núcleo, configuração de região/opções ou instante inicial da sessão.
4. Save states existem, mas não há hash periódico de estado em runtime nem procedimento de desync/re-sincronização.
5. Transmitir somente a máscara de entrada mais recente não garante que os dois dispositivos apliquem cada comando no mesmo frame. Isso não satisfaz o requisito de determinismo bit-a-bit.

## Arquitetura necessária antes de habilitar LAN

- Protocolo versionado por UDP, com ID de sessão, jogador, contador monotônico de frame, máscara de controle, acknowledgements e timeouts limitados.
- Handshake: SHA-256 das bytes da ROM, SHA da revisão Snes9x, versão de protocolo, opções do core, região e tamanho do save state.
- Começar por lockstep: nenhum dispositivo confirma o frame n até receber input do peer para n. Jitter pode parar momentaneamente a emulação; rollback fica fora da primeira fase.
- Separar input local/remoto nas portas libretro 0 e 1 sem regredir single-player.
- Comparar hashes de estado em intervalos definidos; em mismatch, interromper e explicar o desync em vez de seguir silenciosamente.
- Tratar descoberta LAN, host/join, ready/start/leave, desconexão, timeout e incompatibilidade.
- Testes automatizados para perda, duplicação e reordenação de datagramas e replay determinístico da mesma sequência de inputs.
- Validar com dois devices reais na mesma WLAN, vários jogos e regiões. CI verde não prova latência nem determinismo.

## Próximos passos técnicos

1. Expor input de duas portas e contador de frame no bridge.
2. Construir handshake e canal de datagramas com limites de tamanho.
3. Implementar lockstep e timeout.
4. Adicionar hash de estado e diagnóstico de desync.
5. Só então disponibilizar LAN como recurso experimental e coletar resultados em dois devices.
