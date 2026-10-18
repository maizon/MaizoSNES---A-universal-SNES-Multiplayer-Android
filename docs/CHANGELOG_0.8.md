# MaizoSNES 0.8.0 RC1 — conectividade e experiência premium (experimental)

## Corrigido
- A tela de Salas Online mostra o **status ativo** do host; botão cinza deixa
  de parecer travamento. **Cancelar sala/desconectar** aparece quando há sessão.
- Status de criação, entrada, espera de P2 e erros de conexão também aparecem
  em modo Automático; botão de criar é desativado apenas com sessão já iniciada.
- Pausas transitórias de Activity não encerram o netplay silenciosamente.
- Host LAN não morre sozinho após 60 segundos de espera.

## Núcleo, áudio e desempenho
- MZNP v4: configurações de núcleo do anfitrião são recebidas e validadas
  pela whitelist antes de iniciar quadros. Alterações do cliente são
  **temporárias**; opções locais preservadas/restauradas ao encerrar.
- Periodicidade de checksum do estado alterada de 120 para 300 quadros
  (~5 segundos NTSC): menos cópias custosas de estado, com detecção de
  divergência preservada.
- Buffer do áudio Android selecionável: 25, 40 (padrão) e 60 ms, respeitando
  mínimo imposto pelo driver; sem falsa promessa de latência zero.
- Buffer nativo de hashing reutilizado por thread; otimizações leves de TCP.
- Presets de vídeo rápido (CPU 1x) e GPU OpenGL experimental.

## Interface
- Paleta Midnight renovada com contraste melhor, navegação com aba **Online**.
- Sala online priorizada visualmente, estado de conexão e ação de cancelar clara.
- Categorias de configurações em chips, redefinição das opções de núcleo.
- Mensagens instrutivas para ausência de ROM ou servidor.

## Limites ainda conhecidos
- Multiplayer é lockstep; RTT alto ainda reduz FPS e causa stalls.
  Jitter buffer/rollback real exigem arquitetura própria e testes em dispositivos.
- Não houve teste físico dos novos fluxos em dois Androids neste CI.
- Relay TCP **não criptografa o tráfego do jogo**; usar rede de confiança.
- Um servidor público de demonstração não deve ser tratado como infraestrutura
  garantida para produção nem plano gratuito ilimitado.
- APK CI (quando publicado) é **DEBUG assinado para testes**: para updates
  estáveis sem perda de dados use keystore de release persistente e privado.
- Snes9x 1.63: licença de uso não comercial. Nenhuma ROM é distribuída.

## Protocolo
MZNP v4 não é compatível com APKs MZNP v3; host e convidado precisam
instalar a mesma versão. Rede Relay lista com `maizo-relay-v1`, inalterado.
