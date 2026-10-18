# Arquitetura MaizoSNES

## UI e navegação

- Biblioteca importa ROMs .sfc/.smc para filesDir/roms. O picker SAF é utilizado apenas na importação inicial.
- Gameplay é uma rota separada em landscape e modo imersivo; os controles ficam sobrepostos à superfície.
- Menu in-game separa proporção, áudio, controles, opções do Snes9x e save states.
- Preferências de aspecto/áudio/controles têm perfil global e perfil por ROM; opções do núcleo ficam no perfil global.
- Controles touch/teclas compartilham máscara de input atômica.

## Core/JNI

- Núcleo Snes9x 1.63, revisão fixada. O source upstream vem de scripts/fetch_snes9x.sh.
- retro_run executa em thread nativa; vídeo via callback ANativeWindow; áudio por callback para ring buffer e AudioTrack.
- As opções do core são strings no mapa nativo, com as chaves da revisão upstream. nativeSetCoreOption usa o mutex do core e GET_VARIABLE_UPDATE notifica o núcleo.
- Save states seguem serialização libretro e fingerprint da ROM; manter o contrato de rejeição de estado incompatível.

## Vídeo

- Layout Compose decide a área e a proporção visível. Pixel Perfect calcula múltiplo inteiro de 256×224; as outras opções são 4:3, 16:9, stretch e fill/crop.
- A saída atual usa cópia de CPU para ANativeWindow. OpenGL ES/shaders ainda não estão implementados.
- Não usar flags de build que comprometam determinismo.

## LAN

Consulte docs/NETPLAY.md. Antes de habilitar LAN, implementar input porta 1, lockstep por frame, handshake de ROM/opções e detecção de dessincronização.
