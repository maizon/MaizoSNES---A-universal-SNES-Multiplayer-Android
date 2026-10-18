# MaizoSNES — checklist de teste em dispositivo

CI verde comprova apenas compilação e empacotamento. Este checklist precisa ser preenchido em um Android real.

## Biblioteca e ROM
- [ ] O app abre sem crash.
- [ ] Importar uma ROM própria .sfc e .smc copia para a biblioteca privada.
- [ ] ROM importada abre sem chamar o picker novamente.
- [ ] Renomear altera o nome da biblioteca sem perder o arquivo.
- [ ] ZIP, arquivos vazios/invalidos e arquivos acima de 64 MiB são rejeitados com mensagem.
- [ ] Abrir outra ROM encerra a anterior e não deixa áudio antigo.

## Gameplay e proporção
- [ ] Abrir ROM leva a tela dedicada landscape.
- [ ] A superfície do jogo ocupa a área principal e controles ficam sobre o vídeo.
- [ ] Barras do sistema somem em gameplay e retornam à biblioteca.
- [ ] Back pausa e retorna à biblioteca; Continuar retoma o jogo.
- [ ] Testar Pixel Perfect, 4:3, 16:9, Esticar e Preencher em jogo normal e Hi-Res.
- [ ] Perfil por jogo restaura sua proporção.

## Input e áudio
- [ ] D-pad responde em todas as direções; direção+ação simultâneas funcionam.
- [ ] ABXY, L/R, Select/Start respondem.
- [ ] Controle físico Bluetooth continua funcionando.
- [ ] Pause/resume não prende input.
- [ ] Silenciar e ajustar volume não travam áudio.
- [ ] Áudio mantém sincronização após trocar ROM e restaurar estado.

## Ajustes e save states
- [ ] Editor mantém X/Y, escala e opacidade ao fechar/reabrir.
- [ ] Perfis global e por ROM são independentes.
- [ ] Opções do núcleo alteram o efeito correspondente.
- [ ] Slots 1, 2 e 3 salvam/restauram; estado de outra ROM é recusado.
- [ ] Importar/exportar save state continua disponível.
- [ ] Reiniciar jogo funciona.

## LAN e estabilidade
- [ ] Tela Multiplayer deixa claro que LAN ainda não funciona nesta versão.
- [ ] Home/retorno não deixam áudio/thread de input preso.
- [ ] Jogar por 10 minutos sem crash, slowdown progressivo ou aquecimento anormal.
- [ ] Registrar aparelho, Android, SHA do APK, falhas e logcat; não anexar ROM.
