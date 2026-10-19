# Configuração de STUN e TURN no MaizoSNES

## O que está integrado

O app tenta primeiro STUN (Google e Cloudflare) e WebRTC DataChannel com GekkoNet. Em NAT restritivo, tenta TURN nos servidores públicos da iniciativa Open Relay:
- turn:openrelay.metered.ca:80
- turn:openrelay.metered.ca:443
- turn:openrelay.metered.ca:443?transport=tcp

As credenciais públicas de demonstração openrelayproject / openrelayproject foram publicadas pelo Open Relay. **A validade não é garantida:** podem ficar indisponíveis, bloqueadas ou limitar o consumo. TURN é um serviço com quota, não uma rede própria do app.

O Open Relay anuncia até 20 GB de uso gratuito por mês, sujeito aos termos atuais. O aplicativo **não ativa uma conta paga** e não faz deploy de novos servidores.

## Opção mais estável — Metered gratuito, com Railway existente

1. O titular cria uma conta no serviço https://www.metered.ca/tools/openrelay/ e confirma pessoalmente a franquia e os limites.
2. No painel Metered, obtenha o subdomínio do aplicativo e uma TURN Credential API Key com menor escopo possível. Não use uma chave secreta de administrador da conta.
3. No serviço Railway existente, MaizoSNES-Online-Relay, defina essas variáveis de ambiente:
   * MAIZO_METERED_APP — só o subdomínio do aplicativo, sem https ou .metered.live.
   * MAIZO_METERED_TURN_API_KEY — a chave de API TURN de credenciais.
4. Reimplante o serviço Railway atual. Não é necessário abrir portas UDP no Railway.
5. O app consulta automaticamente o endpoint HTTPS /api/ice durante o ICE checking. O Railway solicita credenciais pela REST API Metered, filtra endereços, usa cache de cinco minutos e aplica limite básico por IP. A chave de API NÃO é enviada ao APK.

**Observação de segurança:** o endpoint /api/ice é público e pode transmitir credenciais TURN temporárias a quem acessá-lo. Para uso de escala, implemente autenticação por sessão, limitação de quotas no provedor e rotatividade de credenciais. Nunca configure como MAIZO_METERED_TURN_API_KEY a chave global/secret da conta.

## Configuração opcional na interface

Em Ajustes → Rede → ICE, adicione uma URI por linha:

    stun:stun.example.net:3478
    turns:relay.example.net:5349|usuario|senha

Os valores são guardados nas preferências privadas do Android (backup desativado, mas sem cofre dedicado nesta versão), nunca incluídos no relatório de debug. Não insira API keys de conta em campos do app.

O botão TESTAR COLETA STUN/TURN (ICE) verifica apenas os candidatos locais: host, srflx (STUN), relay (TURN). **Não prova conectividade entre dois celulares.**

Para confirmar na prática, instale a mesma versão em dois celulares, conecte-os a redes diferentes, crie uma sala online e abra o overlay. O resultado confiável é:
- Rollback (GekkoNet) + WebRTC DataChannel + TURN: Sim — TURN foi escolhido.
- Rollback (GekkoNet) + WebRTC DataChannel + TURN: Não — P2P direto foi escolhido.
- Lockstep (MZNP TCP) + TCP via Railway — WebRTC/ICE falhou; fallback.

## Por que não criar coturn no Fly.io?

Em 2026, o Fly.io não oferece franquia mensal de VMs gratuitas para **novos clientes**, apenas um teste inicial. Hospedar coturn poderia gerar cobrança após o período de avaliação, contrariando o objetivo de não criar custos. Um servidor privado continua opcional, mediante contratação separada e confirmação de gastos.

Fontes: https://www.metered.ca/tools/openrelay/ ; https://www.metered.ca/docs/turn-rest-api/ ; https://fly.io/docs/about/discontinued-plans/ .

## Limites atuais

Sem aparelho físico nesta sessão, a coleta real de candidatos, NAT simétrico, sessões de 10 minutos e estabilidade do fallback não foram comprovados. A compilação CI verde não substitui esses testes. O Open Relay gratuito não é SLA de produção.
