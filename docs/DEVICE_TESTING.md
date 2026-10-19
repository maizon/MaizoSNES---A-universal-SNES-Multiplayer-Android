# Como testar MaizoSNES em dois celulares

## Instalação

1. Abra [GitHub Actions](https://github.com/maizon/MaizoSNES---A-universal-SNES-Multiplayer-Android/actions), selecione a **última execução verde** da branch `work/android-foundation` e baixe o artefato `app-debug`.
2. Extraia e instale **o mesmo APK nos dois aparelhos**. Se o Android solicitar, permita instalação desse APK de teste somente pela fonte que você confia.
3. Prepare uma ROM SNES homebrew que possa ser legalmente distribuída. Nenhum jogo está embutido no APK.
4. No anfitrião A, conecte Wi-Fi. No convidado B, use 4G/5G ou Wi-Fi de outro provedor.

## Partida online (Rollback quando possível)

1. Abra a ROM em A e selecione Multiplayer → Rollback (Recomendado) → Criar sala online.
2. Se a ROM for redistribuível, confirme a autorização por sessão. O cliente só receberá bytes depois de aceitar a oferta.
3. No B, selecione Multiplayer → Atualizar salas → Sala de A. Caso não possua a ROM, aceite a oferta autorizada e aguarde o progresso 0–100%, hash SHA-256 e estado inicial.
4. Verifique os estados ICE. No menu durante o jogo (GUIDE/PS/Xbox central, não Start SNES), abra STATUS DE CONEXÃO / DEBUG.
5. Se aparecer **Rollback (GekkoNet)** + **WebRTC DataChannel**, o canal P2P foi estabelecido. Se aparecer **Lockstep (MZNP TCP)** + **TCP via Railway (WSS)**, a rede exigiu fallback, mas o jogo deve continuar normalmente.
6. Jogue por 10 minutos com entradas de ambos, compare sincronização, jitter e correções. Repita com aparelho ARMv7 se possível.

## LAN / IP manual

1. Conecte os dois ao mesmo Wi-Fi; crie sala LAN no A, descubra ou digite IP:porta no B.
2. Confirme **Rollback + UDP direto** no overlay. O host usa UDP porta TCP+1.
3. Simule indisponibilidade UDP (bloqueio no roteador) e confirme retorno para Lockstep TCP antes do primeiro frame.

## Diagnóstico e relatório de bug

1. Abra STATUS DE CONEXÃO / DEBUG na aba Multiplayer, Ajustes ou no menu do jogo.
2. Toque COMPARTILHAR DIAGNÓSTICO para enviar motor, transporte, estado ICE, RTT, jitter, correções, delay e desyncs via Android Sharesheet.
3. Faça uma captura de tela do overlay e relate: dois modelos, Android, ROM homebrew, passos, data/hora, resultado esperado e observado.
4. Opcional em computador ADB: `adb logcat -v time -s MaizoSNES:D AndroidRuntime:E *:S > maizosnes.log`.
5. Antes de compartilhar, remova dados pessoais, endereços IP públicos e códigos de acesso. Nunca envie ROMs ou tokens.

**Nota:** se NAT simétrico, operadora ou firewall bloquear WebRTC e não houver TURN configurado, somente Lockstep é garantido. CI verde não comprova sucesso de WebRTC em dispositivos físicos.
