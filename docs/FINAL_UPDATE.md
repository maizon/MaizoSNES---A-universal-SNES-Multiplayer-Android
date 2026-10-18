# MaizoSNES 0.3 — consolidação técnica da branch de desenvolvimento

**Escopo:** single-player, multiplayer local, LAN e TCP pela internet, configurações de controle, catalogação de jogos e diagnóstico. Somente código do APK; não há ROMs, firmwares, capas ou assets de jogo distribuídos.

## Preservação do multiplayer

- A versão do protocolo continua **MZNP v2**; não houve mudança no formato dos pacotes de input, hash, save state inicial ou descoberta de LAN.
- Os quatro modos de conexão são **configurações de frontend**: Offline local, LAN automática, LAN manual e Internet/VPN. Cada modo tem IP/porta próprios, preservados na memória persistente.
- Switch de modo só é permitido **fora de sessão ativa**; a aplicação não tenta converter uma partida TCP em andamento para outro transporte.
- O modo Internet mantém lockstep e adiciona prioridade IP de baixa latência como dica para o sistema (não é garantia) e timeout de frame mais tolerante (12s vs. 5s LAN), mas **não remove RTT**.
- A rede emite métricas de FPS efetivo e espera pelo peer. A medida é a janela entre enviar o input e recebê-lo no lockstep; **não representa um ping/RTT puro**. Picos e quadros lentos são mostrados no status da sessão.
- Backups automáticos opcionais `before` e `after` guardam os estados de jogo sem alterar os slots 1–3. O jogador pode restaurá-los após confirmação em menu Estados.

## Vídeo e FPS

- O relógio de emulação offline usa deadlines acumulados e recuperação de atrasos, evitando a deriva do agendamento relativo anterior.
- Backend de vídeo permanece SurfaceView + ANativeWindow RGB565 em CPU, **sem OpenGL ES/Vulkan ou GPU shader**.
- Original: `memcpy` otimizado. Scanlines: escurecimento de linhas alternadas. Soft: mistura horizontal. CRT leve: ambos. Todo o processamento adicional é opcional.
- Contadores atômicos expostos por JNI permitem medir frames executados, tempo de `retro_run`, frames apresentados e custo da cópia/post, período nominal da ROM e áudio em buffer.
- HUD opcional; medidas são produzidas **em cada aparelho real**. Sem aparelho/teste não se pode afirmar que um filtro aumenta FPS.

## Cheats mais seguros por checksum

- Quando há dados, ROM é associada ao catálogo **No-Intro** por **tamanho em bytes + CRC32 + SHA-1** (ignora cabeçalho de cópia de 512 bytes quando presente). É uma verificação exata da revisão incluída no banco, independentemente do nome do arquivo.
- O título e região retornados são usados para selecionar grupos compatíveis do catálogo `.cht`. Para ROMs desconhecidas, o sistema mantém a busca heurística por nome/título e alerta que **não foi verificada**.
- Atenção: a própria base de cheats não associa cada código a um checksum; um jogo identificado exatamente ainda pode ter códigos inválidos ou variantes. Ao aplicar cheats, o JNI restringe tamanho, caracteres e quantidade para impedir passagem insegura de strings ao núcleo nativo.

## Presets de controles

O MaizoSNES tem quatro layouts **desenhados originalmente**, com ideias de posicionamento e personalização de:

- CTR Native Android: controles de corrida compactos.
- ARMSX1: controles discretos e interface focada no jogo.
- PPSSPP: controles confortáveis e amplamente configuráveis.
- Maizo Original: controle tradicional próprio.

Todos utilizam o **mesmo D-pad multitouch original**. O botão EDITAR no gameplay alterna entre GRUPOS e BOTÕES; cada ABXY/L/R/Start/Select tem deslocamento individual persistente em porcentagem da tela. O jogador pode ajustar tamanho, opacidade e posição pelo painel; revisitar um preset retoma suas alterações particulares (perfil do jogo ou global). Nenhum código, skin, logotipo ou asset dos três projetos foi importado. Ver THIRD_PARTY_NOTICES.md.

## Biblioteca inspirada em frontends

Grade adaptável, capas carregadas em cache, pesquisa por título, badge de jogo em memória e cards compactos. Inspirada nos princípios de navegação do Daijishou, **não copia sua UI original nem assets**.

As capas podem conter obras protegidas de terceiros: o APK não as distribui. Downloads opcionais vão diretamente ao aparelho via servidor público de thumbnails Libretro e são cacheados na pasta privada. Não existe garantia de cobertura completa, direito de reprodução em outros produtos ou compatibilidade com todas as ROM hacks.

## DEV, PIN e compartilhamento

O painel DEV pede o PIN local de conveniência **9090** para expor o seletor avançado. O código é um controle de interface, não autenticação criptográfica. A troca de ROM funciona somente em rede privada, mediante consentimento bilateral, com SHA-256 e limite de 64 MiB. **Só habilite compartilhamento de arquivos cuja redistribuição seja autorizada**, incluindo homebrew, domínio público ou obras comerciais especificamente licenciadas. Uma ROM comercial adquirida regularmente não concede, por si só, direito de compartilhá-la.

## Compatibilidade e testes pendentes

- Testes unitários cobrem handshake TCP, bloqueio de ROM divergente, transferência autorizada em rede privada e parser da lista LAN; base No-Intro tem testes adicionais de CRC/SHA e cabeçalho.
- GitHub Actions compila ARM64 e publica APK debug. **CI verde comprova compilação/testes automatizados, não teste em dois Androids.**
- O teste em hardware deve medir latência e FPS, confirmar layouts por toque, baixar capas, selecionar cheats, usar hotspots diferentes, validar NTSC/PAL, buscar e restaurar saves após uma partida em LAN/VPN.
- A implementação de rollback/predição de inputs, autenticação/TLS, traversal NAT e relay de partidas remotas exigiria testes de determinismo e um desenvolvimento próprio. **Não foi incorporada** para não quebrar o protocolo lockstep que existe hoje.

Ver docs/DEVICE_SMOKE_TEST.md, docs/NETPLAY.md e LICENSE_DECISION.md.
