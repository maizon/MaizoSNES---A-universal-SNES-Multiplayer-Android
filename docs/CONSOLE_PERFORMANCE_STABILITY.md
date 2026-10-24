# MaizoSNES — Console Performance & Stability

## O que entra neste commit único

1. **Indexação incremental de biblioteca:** metadados region/título são
   armazenados localmente por assinatura de tamanho+mtime. A leitura
   de cabeçalhos é feita em `Dispatchers.IO`, com geração de resultados
   para impedir uma varredura antiga de substituir uma biblioteca recente.
   Sem enviar hashes/nomes de ROMs para servidores ou alterar arquivos ROM.
2. **Capas por consentimento:** SNES/NES compartilham arte apenas por
   título normalizado e console, com arquivo de cache unificado para tags
   de região/revisão. Cache inválido é descartado. Download é sempre
   manual, serial, cancelável e com progresso; abrir partida o interrompe.
3. **Diagnóstico de determinismo NES:** botão no menu rápido do jogo,
   exclusivamente offline. O núcleo FCEUmm é pausado, 36 quadros de
   entrada neutra são reexecutados duas vezes a partir da mesma snapshot,
   resultados são comparados e o estado é sempre restaurado. O teste
   não prova determinismo entre aparelhos ARM diferentes ou estabilidade
   de transporte; é um diagnóstico local, não uma nova condição de rede.
4. **Experiência landscape:** barra rápida rolável com FPS, perfil
   `⚡ RÁPIDO` (somente apresentação original/1x, nunca clocks),
   visibilidade TOUCH e TESTE NES; biblioteca unificada com chips NES/SNES,
   histórico/favoritos e status do download.
5. **Perfis conservadores:** NES usa prefixo de configuração próprio,
   sem alterar/chavear nenhuma preferência histórica de jogos SNES.
   Amostragem de FPS é suspensa quando HUD fica oculto.
6. **Desempenho de ordenação:** recentes indexados uma vez por lista
   em vez de pesquisar a lista inteira para cada item da biblioteca.

## Preservação

Nenhuma modificação do código-fonte do Snes9x 1.63 ou dos protocolos
GekkoNet/TCP/WebRTC/WSS, ROM sharing, Railway, state transfer, handshake,
delay/jitter de rede, ABI, compile flags ou serviço foreground.
O diagnóstico NES chama apenas a função Libretro FCEUmm no modo offline
sob a mutex existente e restaura o estado anterior, sem transmitir frames.

## Testes

JVM: RomMetadataIndex, BoxArtIdentity, NES diagnóstico (mensagens),
ordenação de recentes e testes de compatibilidade existentes.
CI: mesmo build dual ABI armv7+arm64 e testes de rede originais.
Físico: executar docs/VALIDATION.md e docs/DEVICE_TESTING.md.
A qualidade não pode ser garantida como perfeita sem logs e testes
de dispositivos reais; desempenho depende de hardware, quantidade
de ROMs e estado de armazenamento.

## Licenças

O FCEUmm continua GPLv2, Snes9x possui limitações comerciais.
A compatibilidade de distribuição de ambos os núcleos no mesmo app
permanece pendente de revisão jurídica. Não adicionar jogos ou BIOS.
