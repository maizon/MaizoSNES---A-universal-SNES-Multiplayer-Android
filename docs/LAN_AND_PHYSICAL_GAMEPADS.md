# Hotfix multiplayer LAN + remapeamento físico P1/P2 (2026-10-26)

## Bugs de código corrigidos

1. **Crash ao abrir Multiplayer no landscape**:
   `MultiplayerScreen` chamava `firstModeFocus.requestFocus()` em um
   `LaunchedEffect(Unit)` independente da orientação; no landscape a
   interface utiliza botões horizontais e **não instala** o modifier
   `focusRequester(firstModeFocus)`. O Compose lança
   `IllegalStateException` quando um `FocusRequester` sem elemento
   associado recebe `requestFocus()`. A chamada agora só ocorre no
   ramo portrait onde o requester existe.
2. **Criar sala LAN abria gameplay e não criava uma sala útil**:
   `hostMultiplayer(55435)` chamava `continueGame()`, mudando para
   `AppScreen.GAME`, e os helpers de host aceitavam somente GAME.
   Agora cria `LanGameJoinListener`/anúncio e permanece no lobby com
   estado visível, botão FECHAR SALA e mensagens de erro se falhar.
   O servidor aceita a primeira conexão enquanto o lobby é exibido.
   Gameplay só é exibido depois de confirmação de sessão pelo fluxo
   de rede existente. Carregar/retomar ROM offline não anuncia uma sala
   automaticamente, evitando conflito de portas e hospedagem inesperada.
3. **Estado visual da sala:** o lobby e IP manual mostram sala aberta,
   o botão fica desativado durante hospedagem e o usuário pode
   encerrar a sala sem sair do aplicativo.

## Controles físicos P1/P2

- Novo editor em Configurações → Controles e menu do jogo.
- Atribua controladores USB/Bluetooth a P1 ou P2, ou use automático.
- Perfis de 16 botões independentes por jogador, com captura do evento
  físico, troca de mapeamentos quando a tecla já está usada, restauração
  e persistência via `SharedPreferences`.
- Os dispositivos são vinculados por `InputDevice.descriptor` quando
  selecionados para sobreviver à mudança de deviceId na reconexão.
- Direções analógicas continuam com a configuração nativa; as teclas
  digitais e botões físicos são remapeáveis.
- Guide/Home/Voltar não podem ser atribuídos a um botão do jogo,
  mantendo o acesso ao menu e ao Android.
- As máscaras finais continuam bits Libretro 0..15, sem alteração de
  SNES9x, FCEUmm, UDP/GekkoNet, WebRTC, TCP, handshake ou buffers.

## Validação

Compilação e testes de regras de remapeamento em CI; testes físicos
com dois controles e dois aparelhos continuam necessários. O fluxo
LAN continua sujeito a permissões de rede, roteador e disponibilidade
de portas; abrir um listener não significa que a sala foi conectada.
