# License decision log

Status: **open — no Snes9x code is vendored or linked yet**.

This project must not assume that being free of charge or omitting ROM files automatically resolves every licensing question. Before integrating or redistributing a Snes9x version, record the exact upstream repository/tag/commit, preserve its original source headers and copyright notices, include the complete applicable license text in `LICENSES/snes9x.txt`, and review the conditions with qualified counsel if distribution terms remain unclear.

Decision deadline: select and record the exact upstream Snes9x tag/commit and review its applicable license **before Phase 2 is marked complete and before any core source is copied or linked**. Until that decision is recorded, core integration is blocked.

Planned safeguards:
- No bundled commercial ROMs, BIOS files, game assets, or copyrighted game data.
- No ads, in-app purchases, or monetization in the initial release.
- Include `THIRD_PARTY_NOTICES.md` and all required upstream notices before release.
- Do not claim Google Play, F-Droid, or any other distribution channel is legally cleared without reviewing its terms and the core's exact license.
- Keep the core's license and copyright headers unchanged; document any local patches.

This document records project precautions, not legal advice or a final license determination. Revisit it after selecting the exact upstream core revision.
