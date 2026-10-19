# MaizoSNES — Validação física de rollback e compartilhamento

Versão experimental; executar **com dois telefones reais**, APK universal
idêntico, ROM homebrew redistribuível. Não usar ROM de terceiros sem direitos.

## Registro básico

1. Na aba Multiplayer selecione Rollback (Recomendado). Anote versão do APK,
   arquitetura de cada aparelho, ROM SHA-256, SO Android, latência e rede.
2. Abra **STATUS DE CONEXÃO / DEBUG** na aba Multiplayer, em Ajustes e pelo
   menu do jogo (botão PS/Xbox central; START deve ir para o jogo).
3. Verifique motor real e transporte, não apenas preferência selecionada.
   Salas Railway HTTPS/WSS devem mostrar **Lockstep (MZNP TCP)**, jamais WebRTC.
4. Observe RTT min/média/máx, jitter, rollbacks/s, input delay e desyncs.
   Perda N/D é comportamento esperado: Gekko não publica esse dado.

## Matriz de testes

- [ ] Dois Android no mesmo Wi-Fi: abrir ROM idêntica, criar/descobrir sala LAN;
  confirmar rollback Gekko/UDP e conexão P1/P2; jogar 10 minutos.
- [ ] Modo manual IP:porta: host TCP 55435, UDP 55436 liberado; rollback
  aparece no overlay. Testar bloqueio UDP -> fallback automático Lockstep.
- [ ] Duas redes diferentes com Railway: sala HTTPS/WSS, status Lockstep,
  partida de 10 minutos, sem afirmar rollback WebRTC.
- [ ] Cliente sem ROM: entrar na mesma sala, oferta de ROM homebrew, aprovar
  host e cliente; progresso de 0% a 100%; calcular SHA-256 no destino.
- [ ] Rejeitar oferta no cliente: nenhum byte de ROM é salvo; a sala fecha
  com uma mensagem clara sem congelar o Android.
- [ ] ROM maior que 8 MiB autorizada: verificar 64 MiB máximo, timeouts e
  atualização de progresso em ambos os aparelhos.
- [ ] ARMv7 x ARM64: jogar 10 minutos, observar divergências de hash,
  sincronização do áudio, rollback, responsividade e estados.
- [ ] Pressionar START no jogo = SNES Start; GUIDE/MODE abre menu;
  controle físico navega botões do overlay e FECHAR.
- [ ] Sair e reabrir app: ROM sharing ON e receber ON; rollback preferido.
- [ ] Confirmar ausência de ROM/BIOS embutidas no APK e licenças intactas.

## ADB (opcional)

```bash
adb logcat -c
adb logcat -v time -s MaizoSNES:D AndroidRuntime:E *:S > maizosnes-test.log
```

Registre a hora, passos, dois modelos e modo real exibido no debug. Evite
compartilhar logcat contendo dados pessoais, IP público ou ROMs.

## Condições de aprovação

CI verde **não** substitui testagem em hardware. Produção requer: zero
travamento, save states e ROM checksum corretos, fallback sem desconexão
inesperada e overlay correspondente ao transporte realmente utilizado.
