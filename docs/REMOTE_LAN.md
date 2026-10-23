# MaizoSNES — Controle Remoto LAN (Android, experimental)

## O que faz

Um celular executa a ROM na própria tela e anuncia uma sala local de **controle remoto**.
Outro celular com o mesmo APK encontra essa sala em **Multiplayer → Salas na rede Wi-Fi**.
Ao tocar, o cliente vê a escolha **Jogar no dispositivo / Usar como controle**.

A tela do cliente reaproveita o editor, os estilos e os botões virtuais do MaizoSNES;
o centro mostra o **título do jogo** e nickname. Eventos de gamepad USB/Bluetooth
também são encaminhados. Nenhum vídeo, áudio, ROM ou avatar trafega pela rede.

## Como testar em dois Android

1. Conecte os celulares à **mesma rede Wi-Fi** ou ao hotspot do host (sem internet).
2. No **host**, abra uma ROM autorizada e inicie a partida normalmente.
3. No jogo: **Menu do emulador → Multiplayer / Controles conectados →
   ATIVAR CONTROLE REMOTO LAN**. Mantenha o jogo aberto.
4. No **cliente**, entre em Multiplayer, selecione LAN e toque em **BUSCAR**.
5. Escolha a sala marcada **Controle remoto**, selecione **Usar como controle**.
6. No host, **Aceitar**, **Rejeitar**, ou não responder: **autoaceita em 10 s**.
7. Teste P2 com botões touch e gamepad físico, START, menu central, editor de layout,
   latência RTT, perda de Wi-Fi, reconexão e liberação de botões.
8. Jogue pelo menos 10 minutos. Observe se o host continua rodando e o P2 não prende
   botões depois de sair/reconectar. Anote modelos, Android e medições reais.

A porta UDP do controle é **55437**; o anúncio usa UDP **55436**, sem Railway.
Roteadores com isolamento entre clientes podem impedir conexão; teste hotspot.

## Compatibilidade e limites intencionais

- **Não modifica** os fluxos MZNP, GekkoNet, TCP fallback, Railway ou
  compartilhamento bilateral de ROM.
- A sala **Controle remoto** é separada da sessão de sincronização de vídeo.
  Uma sala de vídeo existente não anuncia capacidade de controle remoto,
  e uma sala de controle não executa uma segunda ROM no cliente.
  O seletor mostra as opções, mas habilita apenas o modo suportado pelo host.
- Atualmente, o núcleo nativo do aplicativo expõe **somente P1/P2**.
  Esta implementação usa P1 local do host e **P2 remoto**. P3/P4 e
  mistura de clientes de vídeo/controle na mesma partida exigem multitap e
  integração nativa adicionais; **não estão anunciados como funcionais**.
- O fluxo só aceita datagramas de endereços IPv4 privados. Um token aleatório
  por sessão, número de sequência e estados completos com heartbeat reduzem
  spoofing acidental, pacotes fora de ordem e botões presos. **Não substitui
  autenticação forte contra dispositivos maliciosos na mesma Wi-Fi.**
- Aprovação automática após **10 segundos** é comportamento solicitado pelo
  usuário; ao ativar a sala, considere que qualquer aparelho da LAN pode
  solicitar acesso. Encerre a sala quando terminar.
- Metas de latência não são benchmarks. RTT é medido entre os aparelhos;
  o atraso total depende de touchscreen, Bluetooth, Wi-Fi e do frame do jogo.
- APK universal arm64-v8a + armeabi-v7a. **Testes físicos pendentes.**
- Não inclui ROMs ou BIOS. Respeite as licenças, especialmente do Snes9x.

## Versão instalável

O CI continua gerando um APK debug para testes. O workflow publica como
**asset APK direto em GitHub Releases (não ZIP)** somente após CI verde.
Se houver secrets de assinatura, usa assinatura release estável; sem secrets,
publica uma **prévia debug identificada como tal**, cujo ID e certificado
são diferentes da versão release. Para atualizações de produção, configure
uma chave estável e preserve-a. Nunca publique ROMs/chaves.
