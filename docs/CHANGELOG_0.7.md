# MaizoSNES 0.7.0-preview — Auto Lobby (em desenvolvimento)

- **Auto Online + LAN:** UI sem IP/URL, pesquisa da rede local e leitura do registro de relays oficiais `server/registry.json`; lista salas de vários nós e indica tempo de resposta do diretório HTTP, que não é necessariamente o ping do jogo.
- Preferência Automático para novas instalações; modos anteriores preservados para usuários existentes.
- Registro possui apenas endpoints efetivamente administrados: a lista inicial está vazia até o projeto implantar servidores reais.
- Botões para criar/entrar em salas de um servidor escolhido automaticamente (menor tempo de resposta), sem expor IP ao jogador.
- Planos e limitações documentados em `docs/MULTIPLAYER_PREMIUM_PLAN.md`.
- **Servidores externos de RetroArch/PPSSPP não entram no registro por padrão:** o protocolo não é compatível e não há permissão para aproveitá-los como relay.
- CI compila e testa parsing do manifesto. Não substitui teste em dois dispositivos através de NAT/CGNAT.
