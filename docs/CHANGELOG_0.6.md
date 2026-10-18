# MaizoSNES v0.6.0-preview — changelog

## Novidades

- **9 layouts de controles**: Maizo Original, CTR Racing, ARMSX Compact, PPSSPP Comfort, **PlayStation Modern**, **Xbox Series**, **Nintendo Switch**, **Arcade RGB** e **Steam Deck**. Os cinco novos presets modernos usam botões de ação em losango (acima, direita, abaixo, esquerda) com simbologia e cores apropriadas, gatilhos L/R nas extremidades superiores, posições iniciais distintas e design vetorial feito especificamente para o MaizoSNES. Mantém personalização por jogo, posição, escala e transparência sem modificar as entradas SNES. As marcas são usadas apenas como referências descritivas; os arquivos gráficos originais de outros emuladores NÃO são distribuídos.
- **Servidor informado apenas pelo IP**: `192.0.2.10` é interpretado como `http://192.0.2.10:8787`. URLs completas com portas personalizadas e HTTPS continuam suportadas, assim como salas LAN e conexão manual.
- Novos testes unitários para layouts, mapeamento PS/Xbox e validação de URLs de salas.

## Tudo que permanece disponível da 0.5

- Servidor Node.js de diretório/relay TCP via conexões de saída (NAT/CGNAT) para duas pessoas. Não é P2P direto; o servidor precisa estar hospedado e acessível. O tráfego TCP não é criptografado.
- OpenGL ES 2 opcional para texturas RGB565 e 8 efeitos por fragment shader; CPU RGB565 como padrão, 9 filtros por CPU e multiplicação do framebuffer 1×–4×. Não altera a resolução interna do SNES e não é um widescreen hack universal.
- Mods IPS/BPS fornecidos pelo jogador, sem substituir ROM original e com verificações de checksum. A compatibilidade do mod/widescreen depende do jogo.
- Busca de ROMs na pasta selecionada por SAF ou busca opcional em armazenamento compartilhado mediante permissão especial concedida pelo usuário.
- Créditos a Maicon Nascimento, botão do Instagram, identificação de ROM, capas, cheats e saves/backup de multiplayer.

## Restrições

- O APK precisa ser compilado e testado nos aparelhos reais, especialmente GPU Mali e internet por CGNAT. Testes de CI/Node não são substitutos.
- Sem rolê de rollback ou matchmaking central global automático: o usuário precisa informar um servidor de salas que já esteja rodando.
- O usuário deve fornecer um keystore de distribuição via GitHub Actions Secrets e revisar os requisitos de licença não comercial do Snes9x antes de uma Release de produção; o artefato CI é um APK debug com assinatura de desenvolvimento.
