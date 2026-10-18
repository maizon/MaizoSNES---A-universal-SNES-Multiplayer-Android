# MaizoSNES 0.7.0-preview — Auto Lobby (em desenvolvimento)

- **Auto Online + LAN:** UI sem IP/URL, pesquisa da rede local e leitura do registro de relays oficiais `server/registry.json`; lista salas de vários nós e indica tempo de resposta do diretório HTTP, que não é necessariamente o ping do jogo.
- Preferência Automático para novas instalações; modos anteriores preservados para usuários existentes.
- Registro possui apenas endpoints efetivamente administrados: a lista inicial está vazia até o projeto implantar servidores reais.
- Botões para criar/entrar em salas de um servidor escolhido automaticamente (menor tempo de resposta), sem expor IP ao jogador.
- LAN agora anuncia prefixo SHA-256 da ROM e se a sala utiliza cheats; o Auto Lobby sinaliza incompatibilidade antes da conexão sem mostrar IP na interface padrão. O handshake continua validando SHA-256 integral.
- **JOGO RÁPIDO**: 1 toque tenta LAN com nome de jogo correspondente, depois sala online de ROM compatível/cheats autorizados; se não há sala, cria nova sala online, ou LAN quando nenhum servidor público estiver disponível.
- A seleção automática também verifica se a porta TCP de jogo está alcançável, não apenas o diretório HTTP. O tempo exibido é de verificação da rede, não ping do gameplay.
- **MZNP v3**: cheats online pré-partida, somente com autorização explícita dos dois jogadores. O host comunica até 64 códigos validados; a sessão aborta se houver recusa ou erro. Sem edição unilateral de cheats durante gameplay.
- Salas do relay publicam prefixo SHA-256 da ROM e se os cheats estão ativos; app sinaliza ROM/mod incompatível antes de conectar e ainda confere o hash completo no handshake.
- Patches IPS/BPS continuam aplicados a cópias locais; quem for jogar online deve carregar ROM já modificada idêntica em ambos os aparelhos, sem transmissão de arquivos de jogo.
- Planos e limitações documentados em `docs/MULTIPLAYER_PREMIUM_PLAN.md`.
- **Servidores externos de RetroArch/PPSSPP não entram no registro por padrão:** o protocolo não é compatível e não há permissão para aproveitá-los como relay.
- CI compila e testa parsing do manifesto. Não substitui teste em dois dispositivos através de NAT/CGNAT.
