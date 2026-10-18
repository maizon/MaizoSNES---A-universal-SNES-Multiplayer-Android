# Third-party notices

## Gradle Wrapper

Gradle Wrapper 8.11.1 is sourced from the Gradle project and distributed under the Apache License 2.0. See https://github.com/gradle/gradle and https://www.apache.org/licenses/LICENSE-2.0.

## Snes9x 1.63

- Upstream: https://github.com/snes9xgit/snes9x
- Pinned commit: `921f9f7b83660eb44ad263022a57a4a029057c37`
- Full upstream license: `LICENSES/snes9x.txt`
- Source is fetched by `scripts/fetch_snes9x.sh` during local/CI builds and is intentionally not checked into this repository.
- The upstream license permits use and redistribution for non-commercial purposes subject to its notice conditions and disclaims commercial rights. A green build does not establish legal clearance for a particular distribution channel.

## snes_ntsc filter

- Source file in upstream: `filter/snes_ntsc.c`
- Full upstream license: `LICENSES/snes_ntsc-license.txt`
- This component has a separate LGPL license; retain the license and comply with the requirements applicable to the way it is linked and distributed.

No ROMs or copyrighted game assets are bundled.

## Catálogo de cheats baixado sob demanda (libretro-database)

- Origem: https://github.com/libretro/libretro-database
- Pasta consultada: `cht/Nintendo - Super Nintendo Entertainment System`
- Revisão fixada: `12c21cf79f1e20eff6ac68b80875816c717d0a7a`
- Licença do repositório: **Creative Commons Attribution–ShareAlike 4.0 International** (CC BY-SA 4.0): https://creativecommons.org/licenses/by-sa/4.0/ e https://github.com/libretro/libretro-database/blob/master/LICENSE
- Arquivos `.cht` **não são incluídos no APK**. Somente códigos do jogo consultado são baixados pela interface e armazenados em cache interno do aparelho. O MaizoSNES modifica a apresentação dos arquivos (filtra códigos e oferece toggles de habilitação).
- Créditos pela compilação da base: equipe e contribuidores libretro-database. As descrições de cheats são mantidas com atribuição de origem do arquivo para o usuário.
