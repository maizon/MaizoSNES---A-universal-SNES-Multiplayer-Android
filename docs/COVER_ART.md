# Capas automáticas e correspondência por título

**Origem opcional:** [libretro thumbnails, plataforma Super Nintendo](https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/), HTTPS. Nenhuma imagem de jogos, ROMs ou BIOS é distribuída com o APK.

## Fluxo de identificação

1. Verifica primeiro o cache privado do app (PNG válido).
2. Constrói aliases: título conhecido pelo banco de ROMs, header SNES interno, nome do arquivo e nome sem tags.
3. Faz o download do catálogo público de nomes `Named_Boxarts` (índice limitado a 2,5 MiB; cache válido 7 dias) e escolhe o título idêntico ou prefixo com subtítulo, preferindo a capa da região USA/Europe.
4. O nome encontrado forma uma URL HTTPS na mesma origem do catálogo. Se indisponível, faz poucas tentativas diretas por nome/região.
5. Só aceita resposta 200, `image/png`, assinatura PNG, dimensões válidas e tamanho até 2,5 MiB; redimensiona a imagem para reduzir memória.
6. Guarda a capa dentro de `filesDir/covers`. Não altera a ROM, checksum, save states ou multiplayer.

Capas são opt-in pelo ajuste existente `Capas automáticas`. A busca é feita somente em `Dispatchers.IO`, não na thread de jogo. Quando um cartão entra na tela, pode solicitar sua capa em segundo plano; o número de transferências simultâneas é limitado. Sem internet/sem correspondência, usa placeholder original.

## Limitações e direitos

Nome não confirma o conteúdo binário da ROM. A correspondência é conservadora; não garante acerto para traduções muito renomeadas, homebrew ou títulos com múltiplas versões. As miniaturas pertencem a seus respectivos titulares; não distribuir dentro do APK sem licença. Nenhuma API key, conta nem servidor pago é necessário. Teste de rede real em aparelho continua pendente.
