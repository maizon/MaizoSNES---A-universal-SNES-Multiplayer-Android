# ⚡ GekkoNet Lowend — perfil opcional de desempenho local

## Uso

Na parte **inferior da aba Multiplayer**, ative `GekkoNet Lowend` **antes**
de criar ou entrar na sala. A preferência fica salva no aparelho.
A opção fica bloqueada durante uma sessão ativa para impedir mudança
de alocação/estado em meio à execução.

**Desligado (padrão):** o MaizoSNES utiliza o motor anterior sem mudanças.
O valor previamente selecionado de Motor (Rollback/Lockstep) e perfil de
latência nunca é sobrescrito pelo Lowend. Desligar restaura esses valores
**a partir da próxima sessão**.

**Ligado:** aplica uma configuração SOMENTE ao dispositivo local:
1. Prioriza GekkoNet/UDP na negociação, mesmo quando a seleção normal
   salva é Lockstep (o motor escolhido permanece armazenado).
   Lockstep continua como fallback quando UDP/RTC não são viáveis.
2. Substitui as passagens JNI que criavam e copiavam ByteArray para cada
   snapshot **no loop do Gekko** por ByteBuffers diretos temporários sobre
   os buffers de estado já alocados pelo Gekko. A serialização do núcleo
   escreve diretamente nesses buffers, e o carregamento os lê diretamente.
   Saves offline, salvamentos iniciais/transferidos, hash da ROM, metadados,
   frames, TCP e o Snes9x/FCEUmm original permanecem intocados.
3. Mantém **exatamente** o container binário MZSSTATE v1 (28 bytes de cabeçalho,
   fingerprint e payload do núcleo). A outra ponta não precisa ativar Lowend.
   Em falha no acesso ao buffer ou incompatibilidade no callback, a
   implementação tenta o caminho ByteArray legado na **mesma sessão**,
   antes de um encerramento. A validação de ROM/estado continua igual.
4. Começa com no mínimo 2 quadros de atraso LOCAL, ajustando o atraso
   mais cedo quando ocorrem múltiplas correções ou jitter alto. O modo
   normal continua com seus limites, e o Lowend não modifica o atraso
   do outro aparelho.
5. Pode aliviar a apresentação do vídeo quando o tempo de processamento
   local estoura o orçamento repetidamente, mantendo o core, audio e
   a rede em full cadence. O custo visual pode ser reduzir para 30
   apresentações/s temporariamente, sem reduzir a taxa de simulação.
6. Mantém Limited Saving para situações custosas com limiar Lowend de
   6 ms (normal: 8 ms). Isso preserva CPU, mas Limited Saving pode
   suprimir a detecção de desync do Gekko; é necessário testar
   sincronização por períodos longos.

## Compatibilidade

**Sem aumento de versão dos protocolos MZNP v6/v7, GekkoNet ou RTC**,
e sem alteração dos snapshots transmitidos. O modo é local, logo
pode ser ligado em apenas um jogador; seu desempenho só melhora nele.

Entretanto, compatibilidade entre APKs antigos não é garantida por
esse perfil: os aparelhos ainda precisam de core, ROM, protocolo e
configurações compatíveis. Em redes não compatíveis com UDP/WebRTC, o
fallback TCP já existente continua disponível.

## Medição honesta

A otimização elimina alocações/cópias **fora** do núcleo, mas não acelera
necessariamente o `retro_serialize` ou `retro_unserialize` do próprio núcleo.
Partindo de 12 ms de save+load, **6 ms é uma meta, não um resultado medido**.
O valor reportado no preflight ainda mede o caminho LEGADO, que também
é usado para save inicial/salvamentos offline. Durante as correções de
rollback Lowend a via direta reduz a quantidade de cópias JNI.

A vantagem depende de ROM, modelo Allwinner, distribuição de custo
interno/JNI, frequência de rollback e velocidade da RAM. Se aumentar
travas, desligue: a sessão seguinte usa o caminho anterior.

## Testes mínimos

- [ ] No primeiro uso OFF; criação de sala, save/load e rollback antigo iguais.
- [ ] Ligar Lowend, desligar, reiniciar app: estado salvo e estados do Motor/
      perfil de latência **não** sobrescritos.
- [ ] Repetir com somente host Lowend, somente convidado Lowend, ambos,
      e nenhum. Testes automatizados em loopback comprovam formato do
      handshake; dois aparelhos físicos ainda precisam validar.
- [ ] Testar Gekko UDP LAN, Wi-Fi/4G e 4G/4G com ROM idêntica; observar
      contador de correções, atraso, FPS e desync por pelo menos 20 minutos.
- [ ] Testar Lowend com SNES e NES em ARMv7 e ARM64; testar saídas rápidas,
      troca de app, reconexão e switch de núcleo, e verificar crash.
- [ ] Comparar `save+load` de preflight e picos reais, sabendo que a
      medição de preflight NÃO usa a otimização de snapshots.
- [ ] Se necessário, comparar CPU/memória com perfetto ou Simpleperf
      (JNI GetByteArrayRegion, `retro_serialize`, `retro_unserialize`).
- [ ] Conferir no CI as bibliotecas nativas de ambas ABIs e seus novos
      símbolos JNI, os testes de políticas e os testes MZNP de loopback.

Nenhuma ROM nem arquivo BIOS é embarcado. Questões de licenciamento de
Snes9x/FCEUmm continuam exigindo revisão antes de distribuição comercial.
