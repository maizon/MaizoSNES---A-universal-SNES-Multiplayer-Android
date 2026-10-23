# MaizoSNES — Sala LAN única: Jogar no dispositivo ou Usar como controle

Atualização de 23/10/2026. Android ARMv7+ARM64; não há port webOS.

## Problema corrigido

A prévia v1.0.0-remote-lan.251 criava uma sala exclusiva de controle UDP
(`C`, porta 55437); portanto **Jogar no dispositivo** aparecia cinza e
não podia oferecer ROM. Isso era uma regressão de UX.

A sala nova anuncia **as duas capacidades (`B`, porta TCP 55435) em
um único cartão**. O menu de entrada oferece **sempre ambos os botões
habilitados** nessa sala:

* **Jogar no dispositivo:** usa o MZNP já existente, com GekkoNet/lockstep
  e o mesmo fluxo de permissão bilateral, envio de ROM autorizada,
  verificação SHA-256 e instalação local. Se o cliente não tiver ROM
  compatível, escolher essa opção ativa a autorização para *solicitar*
  recebimento; o host ainda precisa autorizar o compartilhamento e o
  cliente ainda precisa aceitar a oferta específica. Não transmite
  ROM sem esses consentimentos.
* **Usar como controle virtual:** o cliente não emula, nem recebe ROM,
  vídeo ou áudio; só envia botões UDP para o jogo executado no host.

**As duas formas de entrar são escolhas de conexão, não dois netcodes
simultâneos em uma mesma emulação.** Se um jogador escolher
`Jogar no dispositivo`, a sala de gamepads UDP e os clientes remotos
existentes serão desconectados, e o MZNP/GekkoNet passará a controlar
o jogo no host e cliente, para evitar dessincronias. Não misturar
GekkoNet P2 com P3/P4 remotos nesta revisão.

## Anfitrião

Por padrão, ao abrir uma ROM no host Android, **Controle Remoto LAN está
ativado**, anunciando a sala com ambos os modos enquanto a ROM roda normalmente.
O host NÃO pausa seu jogo enquanto aguarda conexão de outro dispositivo.

A opção fica em **Jogo aberto → Menu do emulador →
Multiplayer / Controles conectados**:

* **Desativar / Ativar Controle remoto LAN** (persiste a opção).
* P1 é o host local; até **três clientes**, P2, P3 e P4, podem usar botões
  virtuais ou gamepads físicos, com nome e status individual.
* O host pode **aceitar/rejeitar** a solicitação; após **10 segundos
  sem resposta**, a solicitação é **automaticamente aceita**.
* Cada cliente recebe slot independente P2/P3/P4; o host pode **trocar de
  posição** (inclusive entre dois slots ocupados) ou **desconectar**.
* O host habilita o dispositivo **multitap do Snes9x 1.63** na segunda porta
  SNES. Apenas jogos compatíveis com multitap respondem a P3/P4.

Se o usuário desativar o controle remoto, **a sala LAN tradicional
permanece anunciada**, com TCP 55435 e ROM autorizada, e somente a opção
de gamepad virtual fica desabilitada. A configuração persiste após reiniciar
o app.

## Cliente

Em **Multiplayer → LAN → BUSCAR**, toque na sala do anfitrião,
escolha **Jogar no dispositivo** ou **Usar como controle virtual**.
A segunda opção mostra a interface touch já existente, permitindo
mudar layout, tamanho, opacidade, posição individual e estilos.
START mantém a função de START, e controle físico USB/Bluetooth também
é aceito. Exibe nome do jogo, nickname, slot do jogador, RTT e conexão.

## Rede e desempenho

* Descoberta: UDP **55436**; sala MZNP de jogo/ROM: TCP **55435**
  (e GekkoNet UDP quando negociado); controle remoto: UDP **55437**.
* 16 bits de botões por pacote, envio sob evento + heartbeat do estado
  completo para recuperar pacotes perdidos. Contador de sequência
  descarta comandos atrasados.
* Watchdog limpa botões sem heartbeat, timeout desliga cliente inativo
  e permite reconexão com a mesma identidade de sessão.
* A troca de modo não modifica o protocolo do Railway ou a lógica
  de direitos autorais do MZNP.
* Evite rede Wi-Fi com isolamento entre clientes; prefira hotspot
  ou mesma rede 5GHz com bom sinal. RTT não mede o atraso total
  da tela, controle físico e emulação.

## Validação

Testes JVM:
- contrato `B` da descoberta e retrocompatibilidade `R/L/C`;
- transferência de ROM no handoff TCP da sala compartilhada,
  enquanto o host não pausa antes da conexão;
- três clientes UDP P2–P4, isolamento dos botões, troca de slot,
  desconexão e limpeza de estado.

**Ainda são necessários testes reais em pelo menos 4 celulares Android**
para validar multitap nos jogos de 3/4 jogadores, reconexão, latência,
gamepad físico e simultaneidade de solicitações. CI verde comprova build
e testes, mas não comportamento físico sem ensaio.

## Distribuição

O GitHub Actions publica **.apk diretamente na Release** (não ZIP).
Sem uma chave estável de produção nos GitHub Secrets, o APK da prévia
usa assinatura Android **debug** identificada como tal. Essa assinatura
não é substituta da chave final de produção. Não inclua ROMs, BIOS ou
chaves privadas na Release; o Snes9x mantém suas próprias licenças.
