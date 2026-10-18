# MaizoSNES

Emulador SNES para Android usando Snes9x 1.63. O projeto não inclui ROMs, BIOS, capas nem assets de jogos.

## Implementado nesta fase (código)

- Biblioteca local: importar, listar, renomear e abrir arquivos .sfc/.smc sem repetir o picker depois da importação.
- Gameplay dedicado em landscape, barras do sistema ocultas, pausa e retorno à biblioteca.
- Proporção: Pixel Perfect (escala física inteira de referência 256×224), 4:3 CRT, 16:9, esticar e preencher/crop.
- Menu durante o jogo organizado por Vídeo, Áudio, Controles, Núcleo e Estados.
- Opções reais do Snes9x ligadas ao contrato libretro GET_VARIABLE / GET_VARIABLE_UPDATE.
- Controles semi-transparentes flutuantes sobre o vídeo, D-pad, ABXY, L/R, Start/Select.
- Editor por sliders para posição, tamanho e opacidade, com perfil global e por ROM.
- Três slots de save state privados, além de importar/exportar estados externos.
- A tela LAN deixa claro que o netplay ainda não está habilitado; os bloqueios técnicos e o protocolo proposto estão em docs/NETPLAY.md.

## Build reproduzível

Requisitos: JDK 17, Android SDK Platform 35, NDK 26.3.11579264, CMake 3.22.1 e Git.

```bash
bash scripts/fetch_snes9x.sh
./gradlew --no-daemon assembleDebug
```

O workflow fixa a revisão do Snes9x, compila o APK debug, verifica biblioteca ARM64 e textos de licença e publica o artefato app-debug. Build verde não prova runtime ou teste em device.

## Limites conhecidos

- ABI: arm64-v8a; ARMv7 não está habilitado.
- ROMs ZIP não são suportadas.
- Vídeo usa SurfaceView/ANativeWindow; renderer OpenGL ES e shaders CRT não estão integrados. Modos Hi-Res necessitam validação de escala.
- Netplay LAN foi adiado até existir sincronização lockstep por frame e testes de determinismo em dois devices.
- Validar no aparelho a troca de aspecto, controles multitouch, áudio e opções do núcleo.

## Licenciamento

Snes9x 1.63 tem condições específicas de uso não comercial, e o filtro NTSC possui licença separada. Os avisos integrais ficam em LICENSES/ e são empacotados na aplicação. Não monetizar, incluir anúncios nem publicar em lojas comerciais sem autorização e análise jurídica específica. Ver LICENSE_DECISION.md.

## Teste de dispositivo

Siga docs/DEVICE_SMOKE_TEST.md e anote aparelho, Android, SHA do APK e resultados. Não anexar ROMs a issues. CI verde não é prova de teste em hardware.
