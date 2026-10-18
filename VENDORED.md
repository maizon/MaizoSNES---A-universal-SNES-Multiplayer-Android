# VENDORED / origens de build

Última auditoria: 2026-10-18.

## Snes9x

- Upstream: https://github.com/snes9xgit/snes9x
- Tag: 1.63
- SHA fixado: 921f9f7b83660eb44ad263022a57a4a029057c37
- Método: scripts/fetch_snes9x.sh inicializa o checkout em app/src/main/cpp/third_party/snes9x, faz fetch da tag com depth 1 e recusa SHA diferente.
- Código-fonte no repositório app: não é commitado; é obtido na preparação/build.
- Verificação: compara a licença upstream com LICENSES/snes9x.txt e a licença do filtro com LICENSES/snes_ntsc-license.txt antes de copiar os textos para assets.
- Exclusões: nenhuma ROM, BIOS, capa ou asset de jogo é copiado/empacotado.
- Patches locais no core: nenhum patch direto no checkout upstream; a ponte Android está em native-lib.cpp.
- Data e método de auditoria: GET da API GitHub em 2026-10-18; SHA configurado no script.
  
## Libretro API e NTSC filter

- libretro.cpp, libretro.h e os helpers libretro vêm da mesma revisão upstream do Snes9x.
- filter/snes_ntsc.c acompanha o código upstream e sua licença separada é preservada.
- Nenhum código de outros emuladores foi copiado nesta fase.
