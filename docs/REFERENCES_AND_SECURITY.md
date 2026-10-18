# Referências de UX, controles e netplay

Consultadas como **inspiração de arquitetura**, não como arquivos copiados:

- RetroTouch (MIT), editor por toque com controles posicionáveis, layouts normalizados, multitouch com pointer-ID: https://github.com/Andiweli/RetroTouch
- RetroArch/libretro Netplay, estados serializados + frame IDs + prediction/rollback: https://docs.libretro.com/development/retroarch/netplay/
- Documentação libretro Snes9x, portas de controle, netplay, compatibilidade de core e formatos: https://docs.libretro.com/library/snes9x/

O controle direcional contínuo, o editor em Compose e o protocolo MZNP foram escritos especificamente para o MaizoSNES. Não há incorporação do código de implementação do RetroTouch ou do RetroArch. Snes9x permanece sob seus termos **não comerciais**, sem reinterpretação de licença.

## Superfície de risco do modo online

- TCP direto aceita conexão sem autenticação. A ROM e as opções só são validadas depois da conexão. Estado serializado recebido é limitado em tamanho, porém é conteúdo remoto processado pelo núcleo nativo.
- Evite expor portas de teste a toda a internet. Prefira LAN confiável ou VPN privada com controles de acesso.
- Não há criptografia TLS nem proteção contra ataques na rede; o aplicativo não coleta contas, não oferece relay e não transmite ROM, apenas hash e estado serializado.
- Pares incompatíveis são rejeitados. Mesmo APK, core, ROM e opções devem coincidir; alterar hacks no meio da sessão está bloqueado.
- Deploy de netplay público requer autenticação, criptografia, fuzz tests do parser/estado e testes de compatibilidade.

## Performance

- Renderizador atual é SurfaceView RGB565; não confundir com Vulkan/OpenGL.
- AudioTrack tenta modo de baixa latência quando suportado; é dependente do hardware e do tamanho mínimo do buffer.
- TCP_NODELAY reduz espera artificial de pequenos pacotes, mas **não elimina latência de rede**.
