# MaizoSNES 0.9.0 RC1 — resposta ao bug de ~2 FPS

**Problema reportado:** app Android conectado a VM online, jogo funcionando
porém ~2 FPS. A causa provável foi encontrada em `NetplaySession` v4:
enviava input de um quadro e aguardava input remoto **a cada quadro**.

## Mudanças implementadas

- MZNP v5: **pipelining de input**. A cada iteração envia quadro
  `frame + inputDelay` e consome input remoto para o quadro atual.
  Duas filas circulares pequenas (uma por aparelho) permitem reter inputs
  2–48 quadros à frente sem alocar estados/ROMs por quadro.
- RTT de três medições antes da partida. Delay de entrada adaptado conforme
  a região/tipo de conexão, RTT máximo amostrado e reserva para jitter.
  O host comunica delay ao cliente para o jogo continuar determinístico.
- LAN direta continua preferida quando alcançável (menos RTT);
  relay TCP permanece opção para redes com NAT/CGNAT.
- Checksum de estado piggybackado nos pacotes antecipados a cada 300 quadros; não intercala bytes soltos no fluxo TCP. Isso evita corromper os pacotes já enfileirados e mantém a verificação determinística.
- Três perfis visíveis: **Resposta rápida** (menos input delay),
  **Equilibrado** (padrão) e **Conexão instável** (maior tolerância a jitter).
  O anfitrião decide o buffer; o convidado recebe a mesma configuração.
- HUD de FPS e status detalhado, incluindo **buffer em quadros** e
  métricas de espera remota.
- Métricas de quadros/s, espera remota, frames atrasados e **buffer em frames**
  incluídas no status multiplayer/HUD.
- Preservados: Snes9x 1.63, ROM SHA-256, cheat consent/sync,
  configurações temporárias do host, salvamento inicial, hashes periódicos,
  ROM compartilhada apenas com autorização em rede privada.
- Teste unitário da política de jitter + teste de integração com
  **200ms RTT simulado** (limiar de FPS estável após handshake).

## Interface e controles

- Novo preset **VirtualGamePad Mobile** como padrão para novos usuários, com
  dimensões proporcionais às da tela e layout próximo à referência visual:
  D-pad na parte inferior esquerda, ABXY coloridos na parte superior direita,
  L/R no topo e Start/Select abaixo. Entrada mapeada aos bits SNES corretos.
- Controles permanecem multitoque, com editor de grupos/botões e ajustes
  persistentes por jogo/preset. Os nove presets anteriores são mantidos.
- O projeto VirtualGamePad-Mobile distribui **código GPL-3.0** que não foi
  copiado por possíveis obrigações incompatíveis com o Snes9x não comercial.
  O preset usa desenho vetorial próprio e atribui a inspiração de interface.

## Limitações e próximos passos

- Pipelining evita serializar um RTT para cada quadro, mas **não é rollback**,
  predição ou correção de dessincronia automática; RTT alto aumenta o
  atraso dos botões e jitter além do buffer ainda causa engasgos.
- Relay através de NAT funciona sem encaminhamento de portas, mas não vira
  P2P direto. Conexão direta via internet requer IP alcançável, VPN
  ou arquitetura STUN/ICE/TURN com teste/segurança.
- Conexão de VM e aparelho exige teste real de desempenho, áudio
  sincronizado, perda de pacotes, velocidade do núcleo e mudança de tela.
- O protocolo v5 **não conecta** com APKs v4 anteriores: instale a mesma
  versão atualizada nos dois aparelhos antes de testar.
- Não há garantia de 60 FPS sem medição em hardware real. 
