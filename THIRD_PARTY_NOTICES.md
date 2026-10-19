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

## Referência de controle VirtualGamePad-Mobile — código GPL-3.0 NÃO incorporado

- Projeto: https://github.com/kitswas/VirtualGamePad-Mobile
- Licença declarada: **GNU GPL v3.0** (`LICENCE.TXT` no repositório).
- O layout visual do controle no preset "VirtualGamePad Mobile" foi estudado para
  adaptar o conceito de D-pad inferior, face ABXY superior direita, botões L/R
  e Start/Select à configuração SNES; posições proporcionais às dimensões da tela.
- O projeto original desenha os controles por **Jetpack Compose**, não possui um
  pacote separado de imagens dos botões para simplesmente copiar.
- **Nenhum componente GPL, arte, imagem ou código original do VirtualGamePad-Mobile
  está embutido** no APK MaizoSNES. O desenho e roteamento de input do novo preset
  foram implementados independentemente em Compose para manter os termos atuais
  de distribuição do núcleo Snes9x. Usar ou incorporar os arquivos GPL literalmente
  demandaria revisar compatibilidade de obrigações GPL com a licença não comercial
  do núcleo Snes9x.

## Inspiração de controles e biblioteca (nenhum código/arte importados)

- [CTR Native Android](https://github.com/igawa6/ctr-native-android): referência pública do layout/ergonomia, código sob GPLv3; não copiamos componentes dele.
- [ARMSX1](https://github.com/ARMSX2/ARMSX1): referência de UX, contém partes sob direitos reservados/licenças específicas; nenhuma implementação ou asset do ARMSX1 foi copiada.
- [PPSSPP](https://github.com/hrydgard/ppsspp): referência de editor de controles e experiência de baixo lag; GPLv2+; não incorporamos código.
- [Daijishou](https://github.com/TapiocaFox/Daijishou): referência de navegação, carrosséis e coleção; o repositório público é primordialmente de assets e declara MIT para o próprio conteúdo licenciado. Nenhum asset foi importado.
- [Libretro No-Intro DAT](https://github.com/libretro/libretro-database): checksum de jogo CRC32 + tamanho + SHA-1, baixado sob demanda e armazenado em cache privado. O repositório da base declara CC BY-SA 4.0; revisão fixada `12c21cf79f1e20eff6ac68b80875816c717d0a7a`.
- [Libretro Thumbnails](https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/): serviço de capas requisitadas pela pessoa no aparelho. As artes dos jogos podem possuir direitos distintos; não estão no APK, repositório ou distribuição do MaizoSNES.

## GekkoNet rollback SDK

- Upstream: https://github.com/HeatXD/GekkoNet at pinned revision `0d491a4524e10173fc5d2aae22d042d3ae9f2d44`.
- BSD-2-Clause; copyright (c) 2024–2026 Jamie Meyer, full unmodified text in `LICENSES/gekkonet-bsd-2.txt`, also packaged in the APK.
- Source downloaded by scripts/fetch_gekkonet.sh during the build. No bsnes code copied; HeatXD/bsnes-netplay is used solely as an architectural guide.
- The upstream bundled standalone ASIO implementation has additional notices: preserve its Boost Software License (BSL-1.0) when redistributing binaries.

## GekkoNet bundled dependencies

- Standalone ASIO: copyright 2003–2026 Christopher M. Kohlhoff; Boost Software License v1.0, full notice in LICENSES/asio-boost-1.0.txt and inside APK.
- zpp library: copyright (c) 2021 Eyal Z; MIT, full notice in LICENSES/zpp-mit.txt and inside APK.
- No bsnes-netplay sources or assets were copied.


## WebRTC DataChannel Android SDK (P2P ICE/STUN/DTLS)

- AAR artifact: `io.github.webrtc-sdk:android-prefixed-stripped:150.7871.01` from Maven Central; package namespace `livekit.org.webrtc`.
- WebRTC native library copyright the WebRTC project authors; **BSD-3-Clause** (full unmodified text: `LICENSES/webrtc-bsd-3.txt`, also bundled in APK).
- Android SDK packaging copyright 2023 WebRTC SDKs; **MIT** (full text `LICENSES/webrtc-sdk-mit.txt`, also bundled in APK).
- Upstream source: https://webrtc.googlesource.com/src and https://github.com/webrtc-sdk/android ; binaries use the SDK's official redistributable pre-built AAR.
- TURN is not shipped; STUN uses public servers. No Google proprietary games or BIOS are bundled. Third-party notices and patents of underlying WebRTC releases remain subject to upstream distribution review before signed release.
