# Cheats automáticos, salas LAN e transferência opcional de ROM

## Cheat browser no aplicativo

1. Importar e abrir uma ROM SNES `.sfc` ou `.smc` (pode importar ZIP com uma ROM).
2. Abrir o menu pelo círculo translúcido no topo da tela, expandir e selecionar Ajustes → **Cheats**.
3. O app consulta automaticamente uma árvore fixa da [libretro-database](https://github.com/libretro/libretro-database), filtrando pelo **nome do arquivo** e pelo **título interno SNES** no cabeçalho LoROM/HiROM. Baixa sob demanda apenas os arquivos de código `.cht` correspondentes, usando HTTPS.
4. Os cheats aparecem como nomes e códigos, com seleção individual. O jogador **não precisa digitar código**. Há campo de filtro e botão para pesquisar novamente.
5. Cheats e índice baixados são guardados em `filesDir/cheat_catalog`: revisitar o mesmo jogo pode funcionar offline. Seleções são armazenadas por SHA-256 da ROM no `SharedPreferences`; por padrão **nenhum cheat fica habilitado**.
6. Se o jogo não for reconhecido por nome/título, nenhum código duvidoso é aplicado. Ao contrário de bancos com checksums por ROM, esta correspondência é **heurística**: códigos de uma região, revisão ou hack diferente podem não funcionar. Recomenda-se backup do save antes de usar.
7. Selecionar cheats no modo netplay é desativado para reduzir risco de dessincronia. Depois da sessão, as escolhas locais salvas podem ser restauradas.

**Origem e licença:** [libretro/libretro-database](https://github.com/libretro/libretro-database), revisão `12c21cf79f1e20eff6ac68b80875816c717d0a7a`, arquivos de cheats publicados no repositório sob **CC BY-SA 4.0**. [Licença integral](https://creativecommons.org/licenses/by-sa/4.0/) e [LICENSE da base](https://github.com/libretro/libretro-database/blob/master/LICENSE). O frontend modifica apenas a forma de exibição dos dados para seleção pelo jogador. Nenhuma ROM é enviada a terceiros pela busca de cheats.

**Segurança nativa:** a revisão fixada do Snes9x utiliza cópia `strcpy` em buffer de 256 caracteres ao interpretar cheats. O frontend valida tamanho, quantidade e caracteres no JNI antes de delegar ao núcleo, bloqueando códigos longos/malformados.

## Descoberta automática em Wi-Fi sem internet

- O host entra na aba Multiplayer, seleciona porta TCP (padrão **55435**) e cria sala. Enquanto espera, responde a sondagens `MZSCAN2` na porta **UDP 55436** com nome do jogo e porta TCP.
- O convidado entra na aba Multiplayer: a busca é iniciada automaticamente e a lista de salas aparece. Toque na sala para conectar. O botão BUSCAR repete a busca.
- Se não houver resultado, use os campos de **IP e porta manual**. Algumas redes/hotspots isolam clientes ou bloqueiam broadcast mesmo com o Wi-Fi funcionando.
- A descoberta é local e não requer conexão à internet, GPS ou conta de usuário. O modo WAN continua usando IP TCP alcançável (ex.: VPN privada), sem lista global de salas.
- O protocolo de partida ainda é lockstep TCP, portanto **não possui latência zero** ou rollback. Está em fase experimental; requer validação em dois Androids reais.

## Envio de ROM ao convidado: consentimento e direitos

**Desligado por padrão em ambos os aparelhos.** Projetado apenas para homebrew, conteúdo em domínio público ou ROM cujo detentor concede explicitamente direito de redistribuição. **Ter uma ROM no próprio aparelho não significa ter permissão para compartilhá-la.** Nunca use esta função para distribuir jogos comerciais protegidos sem licença.

1. O host abre uma ROM que pode redistribuir e habilita **Compartilhar minha ROM autorizada**.
2. O cliente habilita **Aceitar ROM autorizada do host**. Ele pode não ter o jogo na biblioteca ou ter uma versão diferente.
3. No handshake, ambos verificam versão do protocolo e opções de core, o cliente informa o SHA da ROM instalada ou ausência de ROM. Transferência só ocorre entre **dois endereços IPv4 privados/site-local** e com os dois consentimentos.
4. O host envia a ROM (máximo 64 MiB) no canal TCP, o cliente confirma SHA-256 e cria o arquivo na pasta privada `filesDir/roms` com nome higienizado e sufixo anticolisão. Se o núcleo aceitar, a ROM é aberta automaticamente e recebe o estado inicial do host, antes de começar a partida.
5. Se hashes divergirem e faltar permissão/consentimento, a sala recusa a sessão sem envio.
6. Não existe seleção de pasta externa: o padrão seguro e já implementado é o armazenamento privado do aplicativo. Para backup externo, exporte arquivos próprios por funcionalidades específicas futuras.

### Limites de segurança

- TCP não está criptografado; o protocolo não tem conta, senha, autorização por chave, TLS nem autenticação. **Não abra a porta diretamente na internet pública**. Para internet, use VPN privada com controle de acesso.
- Mesmo uma ROM não executável pode explorar bugs do core/decodificador; o cliente só deve confiar em hosts conhecidos.
- O recebimento utiliza limite de tamanho e SHA para detectar corrupção, não para provar segurança do conteúdo ou titularidade dos direitos.
- A transferência não inclui software proprietário no repositório ou APK; todo conteúdo depende de ação consciente do usuário.
