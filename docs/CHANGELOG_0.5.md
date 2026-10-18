# Changelog — MaizoSNES v0.5.0-preview

## Novidades

- Serviço opcional de **salas online por URL/IP** (`server/relay.js`, HTTP :8787 e TCP :8788): busca salas e transporta MZNP v2 via conexões TCP de saída, com suporte a CGNAT sem encaminhamento de portas nos smartphones. Não é P2P direto, possui até 100 salas à espera de segundo jogador, tempo limite de 5 minutos e comunicação **sem criptografia**, destinada a servidor/VPN confiável.
- Renderizador **OpenGL ES 2** experimental, opt-in: texturas RGB565 do núcleo Snes9x, 8 shaders de escala/processamento na GPU: nearest, bilinear, suavização 4 vizinhos, CRT, LCD, nitidez, cinza, saturação.
- Mantido modo CPU RGB565 com escala de framebuffer 1–4× e **9 filtros** opcionais: original, scanlines, soft, CRT soft, LCD grid, cinza, verde, sharpen, triad RGB. A escala apenas duplica pixels da imagem original; não é resolução interna nova.
- Botão opcional **Procurar em todo o aparelho**: pede autorização especial do sistema no Android 11+ e varre até 30.000 itens do armazenamento compartilhado (máximo 500 ROMs), importando `.sfc`, `.smc` e ZIP, sem varrer pastas privadas de outros apps. O modo SAF por pasta permanece disponível sem permissão especial.
- Biblioteca recebe patches de ROM **IPS e BPS** fornecidos pelo usuário: cópia nova, CRC de origem/destino/patch BPS, limite 64 MiB, ROM original preservada.
- Quatro skins autorais de controles, cada uma com contornos/forma/cores/layout próprios e prévias na seção de controles; editor de posições salvo por preset/ROM. Assets originais de CTR, ARMSX1 e PPSSPP não são incluídos.
- Créditos visíveis para Maicon Nascimento, botão Instagram, atribuições e busca de ROMs em pastas SAF da atualização anterior, além das proporções 8:7, 3:2, 21:9.
- Teste automatizado do servidor de relay com duas conexões TCP e teste de aplicação de patch IPS.

## Atenção

- Widescreen nativo não pode ser ativado genericamente no SNES: mods são específicos de cada jogo. Aplicar IPS/BPS não garante widescreen.
- O modo de internet utiliza **relay público autogerenciado**, sem TLS nativo ou login; em rede aberta os dados podem ser interceptados e as salas listadas por qualquer cliente. Use servidor controlado por você/VPN.
- A pipeline OpenGL ES 2 precisa de testes reais em aparelhos Android com GPUs Mali, Snapdragon e versões diferentes. O pipeline CPU permanece como padrão estável.
- Testes Android Gradle e loopback de relay não substituem validação em dois aparelhos nem prova de 60 FPS.
- A distribuição pública do APK depende de revisar os termos da licença não comercial de Snes9x e licenças dos componentes. APK debug é assinado com chave de desenvolvimento: não é assinatura estável para publicação.
- A importação de ROMs usa SAF ou, após autorização explícita, a permissão especial de acesso a todos os arquivos compartilhados. O Android continua bloqueando dados privados de outros aplicativos; não é uma varredura irrestrita de todo o sistema. Esta permissão pode impedir aprovação em lojas de aplicativos.
