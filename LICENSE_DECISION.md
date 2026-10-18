# License decision log

Status: **upstream revision verified; license/distribution review remains open; no Snes9x code is vendored or linked yet**.

This project must not assume that being free of charge or omitting ROM files automatically resolves every licensing question. Before integrating or redistributing a Snes9x version, record the exact upstream repository/tag/commit, preserve its original source headers and copyright notices, include the complete applicable license text in `LICENSES/snes9x.txt`, and review the conditions with qualified counsel if distribution terms remain unclear.

Decision deadline: select and record the exact upstream Snes9x tag/commit and review its applicable license **before Phase 2 is marked complete and before any core source is copied or linked**. Until that decision is recorded, core integration is blocked.

Planned safeguards:
- No bundled commercial ROMs, BIOS files, game assets, or copyrighted game data.
- No ads, in-app purchases, or monetization in the initial release.
- Include `THIRD_PARTY_NOTICES.md` and all required upstream notices before release.
- Do not claim Google Play, F-Droid, or any other distribution channel is legally cleared without reviewing its terms and the core's exact license.
- Keep the core's license and copyright headers unchanged; document any local patches.

This document records project precautions, not legal advice or a final license determination. Revisit it after selecting the exact upstream core revision.


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
- Fontes vendorizadas: nenhuma até a revisão da licença completa e definição do conjunto mínimo.
- Modificações: nenhuma.
- Próximo passo: inventariar a árvore e os Makefiles da revisão confirmada; selecionar fontes comprovadamente necessárias, preservando cabeçalhos e o texto integral da licença em `LICENSES/snes9x.txt`.

Esta nota documenta cautela técnica do projeto, não constitui aconselhamento jurídico nem conclui que um canal de distribuição específico esteja liberado.
