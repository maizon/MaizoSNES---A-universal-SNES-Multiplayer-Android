# MaizoSNES ARMv7 — Android 32-bit (0.8.1-preview)

## Objetivo
Disponibilizar **o mesmo código de recursos do MaizoSNES 0.8** para aparelhos
Android **ARMv7 (armeabi-v7a, 32 bits)**, sem afetar a branch `main`
e o APK ARM64.

## Funcionalidades preservadas
- Emulação Snes9x 1.63, biblioteca e importação de arquivos locais.
- Vídeo CPU RGB565 / OpenGL ES 2, escalas, aspect ratios e filtros.
- Áudio e buffers 25/40/60 ms, touch/gamepads, perfis e cheats.
- Save states, backup multiplayer, opções nativas do núcleo.
- Multiplayer local, dois controles, LAN por descoberta/IP e salas online
  públicas via o mesmo relay HTTPS + TCP, com MZNP v4 e ROM SHA-256.
- Sincronização temporária das opções permitidas do anfitrião com o convidado.

## Diferenças técnicas da variante
- ABI incluída: **somente** `armeabi-v7a` (não arm64-v8a).
- Mínimo: Android 6.0 (API 23) segundo as configurações Gradle do app.
- Não exigimos instruções ARM NEON; suporta CPUs ARMv7 compatíveis com o NDK.
- `versionCode 9` / `versionName 0.8.1-armv7-preview`.
- Instalação de testes separada: `com.maizon.maizosnes.debug.armv7`;
  não remove/substitui instalações ARM64 com pacote `.debug`.
  A biblioteca de ROMs e estados é separada por sandbox Android.
- Mantém NDK 26.3, build CMake C++17, `c++_static` e recursos sem ROM.
- O conteúdo do jogo, configurações e protocolo é o mesmo da base ARM64
  no commit de ramificação; builds futuras da main exigem cherry-pick/merge.

## Verificações obrigatórias
Workflow `Android ARMv7 CI` executa testes Node.js e testes JVM Android,
compila via NDK/CMake, checa que `libnative-lib.so` foi incluída em
`lib/armeabi-v7a/`, inspeciona ELF32 / Machine ARM, verifica licenças
e rejeita objetos nativos arm64, x86 e runtime C++ compartilhado.

O workflow de publicação gera um APK debug assinado e SHA-256 num GitHub
**prerelease**. O certificado debug do runner pode mudar, portanto não
preserve dados contando com atualizações OTA. Nunca desinstale outros APKs
sem backup das ROMs/saves próprios.

## Limites e segurança
- Compatibilidade binária + testes automatizados **não comprovam FPS,
  vídeo/audio reais nem dois celulares ARMv7 rodando**. É preciso testar
  aparelho ARMv7 com jogo homebrew e registrar FPS/temperatura/latência.
- Não é possível prometer 60 FPS constantes: ARMv7/GPU Mali antigos variam.
- Internet continua em lockstep TCP. RTT/jitter alto pode causar travadas;
  não foi introduzido rollback fictício.
- APK Android ARM64 e ARMv7 podem participar da mesma partida com ROM,
  configuração de núcleo e protocolo MZNP v4 compatíveis, sujeito a testes
  entre arquiteturas; cross-ABI determinismo não foi verificado em hardware.
- Tráfego do jogo no relay não possui criptografia ponta a ponta.
- Sem ROMs comerciais, sem BIOS. Consulte as licenças do Snes9x
  (uso não comercial) e componentes terceiros no repositório.

## Build local
```sh
git checkout feature/armv7-32bit
bash scripts/fetch_snes9x.sh
./gradlew --no-daemon testDebugUnitTest assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`
