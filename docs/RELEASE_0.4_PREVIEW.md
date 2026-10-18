# MaizoSNES 0.4 — prévia / changelog

## Implementado nesta prévia

- Créditos próprios ao **Maicon Nascimento**, botão Instagram, atribuições a Snes9x/Libretro e referências de interface PPSSPP, CTR Native Android, ARMSX1 e Daijishou.
- Importação de ROMs por **pasta escolhida no Android Storage Access Framework**. O usuário seleciona a pasta raiz; o app percorre subpastas autorizadas (até 8 níveis, 3000 entradas e 250 ROMs) e importa arquivos .sfc/.smc de até 64 MiB. Duplicados são ignorados. O app não solicita acesso indiscriminado ao armazenamento nem contorna as permissões do Android 11+.
- Novos modos de proporção de vídeo 8:7, 3:2 e 21:9, além de 4:3, 16:9, pixel-perfect, esticar e preencher. **Não é renderização 4K, resolução interna maior ou widescreen hack**: é somente escala/viewport da superfície.
- Preparação da versão 0.4.0-preview para validação CI, preservando netplay MZNP v2 e perfis anteriores.

## Não implementado nesta prévia

- **Skins idênticas** dos emuladores externos: é necessário identificar o asset exato, confirmar licença e condições de redistribuição, atribuição e compatibilidade com Snes9x. As licenças dos repositórios não provam que toda imagem ou skin incluída seja livre para copiar. Não incorporamos arte de terceiros sem essa verificação.
- **Upscale de resolução interna, shaders GPU, FXAA, xBRZ, widescreen real**: Snes9x permanece renderizando a resolução original via RGB565/SurfaceView. Os filtros leves existentes permanecem.
- **Lista pública de salas pela Internet, NAT traversal e P2P sem IP**: não existe infraestrutura de sinalização/relay/TURN autenticada neste projeto. Isso requer backend, manutenção, abuso/segurança e testes de NAT. O modo Internet/VPN por IP continua disponível, sem afirmar que é matchmaking público.
- **Release final assinada**: a chave de assinatura de distribuição deve ser fornecida pelo proprietário por secrets seguros, não incluída no Git. APK debug assinado com chave de desenvolvimento NÃO é APK final de produção. Não publicar uma release definitiva sem essa etapa e teste em aparelhos.

## Critérios de aceite

1. CI deve compilar Kotlin/C++ e executar testes unitários.
2. Instalar no Android, testar seleção de pasta SAF, importação, ROMs duplicadas, orientação e botão Instagram.
3. Validar em dois aparelhos LAN e Internet/VPN, incluindo ROM mismatch, saves e desempenho.
4. Auditar licenças e assets externos, implementar backend de descoberta P2P e GPU renderer em alterações separadas, com testes antes de publicar como estável.

A licença do Snes9x é **não comercial** e deve continuar respeitada.
