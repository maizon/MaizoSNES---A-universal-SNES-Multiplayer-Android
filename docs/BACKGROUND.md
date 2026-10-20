# Multiplayer LAN/online em segundo plano — MaizoSNES

O aplicativo mantém a sessão multiplayer ativa durante uma troca normal de aplicativo (Home, navegador, WhatsApp) **enquanto o processo Android continuar vivo**.

## Implementação

- O fluxo de emulação Snes9x de netplay continua no thread de rede mesmo quando a Activity chama `onPause` e o Surface/GL fica ausente.
- Uma **Foreground Service** Android `connectedDevice` mostra a notificação permanente `MaizoSNES — multiplayer ativo`. É iniciada quando a sala começa e encerrada quando a sessão termina.
- Durante a sessão, a service adquire uma **PARTIAL_WAKE_LOCK** e, se permitido, uma **WIFI_MODE_FULL_HIGH_PERF Wi-Fi lock**. Ambas são liberadas ao encerrar a service.
- `onPause` solta os botões físicos/touch, zerando o input local. A conexão permanece com inputs neutros enquanto o usuário usa outro app.
- O `AudioTrack` é interrompido quando app perde foco, mas o SNES continua emulando. A fila PCM atrasada é descartada ao retornar, impedindo áudio antigo de tocar rapidamente.
- O vídeo não precisa de Surface enquanto em segundo plano; o retorno reconecta a tela sem recriar a partida se o processo sobreviveu.
- A opção **Multiplayer em segundo plano** fica **ligada por padrão** em Multiplayer → Motor de multiplayer. Desligá-la desativa o serviço e wake locks de proteção, mas **não força o encerramento imediato** da partida.

## Bateria, permissões e políticas Android

O serviço usa `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `CHANGE_WIFI_STATE` e `WAKE_LOCK`. A CPU/Wi-Fi podem consumir bateria adicional quando o app estiver em segundo plano; alguns fabricantes impõem economia de energia severa mesmo com foreground service. A permissão de notificação depende das regras do Android.

A proteção não garante continuidade depois de **Forçar parada**, reinicialização, encerramento do processo pelo sistema, perda prolongada de Internet, mudança de IP ou desconexão de rede. O GekkoNet tolera interrupções menores com timeout de 30 segundos; isso **não é protocolo de reconexão ou migração de sessão entre redes**. Para manter uma sala após troca Wi-Fi/4G seria necessário implementar renegociação ICE/reconexão autenticada do estado e dos peers — ainda não validado.

A service **não armazena ROMs nem credenciais**. Apenas mantém uma sessão já autorizada.

## Testes em dois dispositivos Android

1. Instale o mesmo APK universal ARMv7/ARM64 nos dois aparelhos. Crie uma sala LAN e confirme rollback/UDP real no overlay.
2. Em ambos, pressione Home e abra outro app por 1 minuto. A notificação persistente deverá aparecer. Volte e confirme a mesma sala e o progresso de quadros.
3. Repita a troca no multiplayer online Wi-Fi/4G e verifique WebRTC/ICE efetivamente ativo ou fallback TCP. Meça travadas, áudio e perda de sessão.
4. Desative **Multiplayer em segundo plano**, confirme que a notificação some. Evite trocar de app por longos períodos nessa opção.
5. Com a opção ligada, teste 2 minutos com tela apagada, após economia de bateria; anote modelo do aparelho e se a sessão sofreu encerramento.
6. Faça teste adicional de chamada telefônica/AudioFocus; gameplay segue e áudio externo deve manter sua prioridade.

**Só um dispositivo real pode comprovar o funcionamento do serviço sob as políticas de energia específicas de cada fabricante.** Testes JVM cobrem a política de pausa do emulador; CI não executa serviços Android reais.
