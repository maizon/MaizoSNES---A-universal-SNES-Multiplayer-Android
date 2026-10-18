# License decision log

Status: **upstream revision verified; technical integration is being attempted for non-commercial development; distribution/legal review remains open**.

This project must not assume that being free of charge or omitting ROM files automatically resolves every licensing question. Before integrating or redistributing a Snes9x version, record the exact upstream repository/tag/commit, preserve its original source headers and copyright notices, include the complete applicable license text in `LICENSES/snes9x.txt`, and review the conditions with qualified counsel if distribution terms remain unclear.

The upstream revision is now selected and pinned. Technical CI integration may proceed using the upstream non-commercial license terms, while public APK distribution, monetization, stores, or other release claims remain blocked pending review of the complete license and the intended distribution model. This is a project safeguard, not legal advice.

Planned safeguards:
- No bundled commercial ROMs, BIOS files, game assets, or copyrighted game data.
- No ads, in-app purchases, or monetization in the initial release.
- Include `THIRD_PARTY_NOTICES.md` and all required upstream notices before release.
- Do not claim Google Play, F-Droid, or any other distribution channel is legally cleared without reviewing its terms and the core's exact license.
- Keep the core's license and copyright headers unchanged; document any local patches.

This document records project precautions, not legal advice or a final license determination. Revisit it before any public distribution; the core revision is pinned, but the legal/distribution review remains open.


## Snes9x 1.63 — avaliação preliminar (2026-10-18)

- Repositório upstream: https://github.com/snes9xgit/snes9x
- Tag pretendida: `1.63`
- Commit confirmado pela referência GitHub da tag (`GET /repos/snes9xgit/snes9x/git/ref/tags/1.63`): `921f9f7b83660eb44ad263022a57a4a029057c37` (objeto apontado é um commit, não um objeto tag anotado).
- Verificação reproduzível equivalente: `git ls-remote --tags https://github.com/snes9xgit/snes9x.git 'refs/tags/1.63*'` — confirmar localmente antes da cópia se for exigida essa trilha exata.
- Data da verificação remota: 2026-10-18 (horário UTC não registrado).
- Licença: o texto da tag inclui uma concessão de permissão para uso, cópia, modificação e distribuição para fins não comerciais e a cláusula “Under no circumstances will commercial rights be given”.
- Estado: **avaliação preliminar; não considerar autorização jurídica definitiva**.
- Política de projeto proposta: sem monetização, anúncios ou cobrança; não publicar em loja comercial sem autorização/parecer jurídico específico.
- GitHub Releases e F-Droid não são automaticamente considerados permitidos só por serem gratuitos ou de código aberto; avaliar o modelo de distribuição e os termos aplicáveis antes de publicar.
- Código do núcleo: não é commitado neste repositório; o script de build obtém a revisão exata e verifica o hash e os avisos de licença antes de compilar.
- Modificações: nenhuma.
- Próximos passos: validar carregamento, vídeo, áudio, entrada e ciclo de vida em dispositivo físico; reavaliar a licença e o modelo de distribuição antes de qualquer publicação.

Esta nota documenta cautela técnica do projeto, não constitui aconselhamento jurídico nem conclui que um canal de distribuição específico esteja liberado.


## Technical integration update (2026-10-18)

- The build fetches tag `1.63` and rejects it unless the resulting commit equals `921f9f7b83660eb44ad263022a57a4a029057c37`.
- The native target follows the upstream libretro source list, including `filter/snes_ntsc.c`, and uses the libretro frontend API rather than reimplementing every core platform callback.
- `LICENSES/snes9x.txt` and `LICENSES/snes_ntsc-license.txt` are checked against the fetched upstream license files before compilation. Copies are generated into Android assets for the APK.
- This is not a final legal determination. Do not publish/monetize builds until the planned distribution model is reviewed.


## Decisão de distribuição do aplicativo (2026-10-18)

Política de release deste repositório:

- Não monetizar o app, não exibir anúncios e não oferecer compras dentro do app.
- Não publicar em lojas comerciais (incluindo Google Play) sem autorização expressa/parecer jurídico que cubra o uso pretendido do Snes9x 1.63.
- Qualquer release binária pretendida deve ser gratuita e não comercial, fora de lojas comerciais, preservando o texto integral de Snes9x, a licença do filtro NTSC e os avisos na aplicação e página de release.
- Esta é uma decisão de projeto, não uma conclusão jurídica. Não alegar que qualquer canal de distribuição específico está liberado sem revisão qualificada.
