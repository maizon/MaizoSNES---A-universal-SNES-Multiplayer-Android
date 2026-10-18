# Servidor de salas pela Internet — MaizoSNES

## Como iniciar

Instale Node.js 20+ no servidor com IP público e execute:

```bash
node server/relay.js
```

Por padrão o painel de descoberta fica em **http://IP-DO-SERVIDOR:8787/api/rooms**
e o túnel TCP em **IP-DO-SERVIDOR:8788**. Libere ambas as portas no firewall.
Para alterar as portas, use `MAIZO_HTTP_PORT` e `MAIZO_TCP_PORT`.
Nenhuma dependência npm adicional é necessária.

No Android: **LAN → Servidor de salas (relay) → Endereço do servidor**,
informe `http://IP-DO-SERVIDOR:8787`, toque em **Atualizar salas**,
depois em **Criar sala pela internet**, ou toque em uma sala da lista.

Host e convidado iniciam **conexões de saída** para a porta TCP do servidor;
portanto funcionam mesmo por trás de NAT/CGNAT, desde que tenham acesso ao
servidor. **Não é P2P direto, é relay TCP**, com o protocolo MZNP v2 preservado.
A sessão continua limitada a duas pessoas e o código do jogo deve coincidir.

## Segurança e limites

**Transporte TCP não criptografado:** não use servidor público não confiável;
para um ambiente seguro conecte Android e servidor por VPN privada confiável.
HTTPS no painel não criptografa automaticamente o tráfego do túnel TCP.
A lista de salas é pública a qualquer pessoa capaz de acessar o servidor e
não possui contas/autenticação. Há limite de 100 salas pendentes e 5 minutos
por sala aguardando segundo jogador; implemente autenticação, TLS/controle de
abuso antes de abrir o serviço a desconhecidos.

Não há transferência de ROMs pelo relay: o mecanismo de transferência
existente exige conexão estritamente privada e consentimento de ambos.
Não insira ROMs comerciais no servidor. O servidor repassa bytes e não os grava.

Teste rápido: `node --test server/relay.test.js`.

Esta é uma primeira implementação funcional de **diretório + encaminhamento**,
não previsão de rollback, sincronização sem latência, WebRTC ou NAT punching.
