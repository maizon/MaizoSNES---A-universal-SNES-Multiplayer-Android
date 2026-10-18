# Assinar e publicar uma Release de produção

O APK `app-debug.apk` do CI é **instalável e assinado com certificado de
desenvolvimento**, mas esse certificado não é apropriado para publicar versões
de atualização de produção (chave varia entre máquinas/runners).

Para publicar uma **prerelease ARM64 assinada de forma estável**, gere e
guarde com segurança sua própria chave Android, e configure no repositório
GitHub quatro **Secrets de Actions**:

- `MAIZO_KEYSTORE_B64`: bytes do keystore codificados em base64 em UMA linha.
- `MAIZO_STORE_PASSWORD`: senha do keystore.
- `MAIZO_KEY_ALIAS`: alias da chave.
- `MAIZO_KEY_PASSWORD`: senha da chave.

O workflow `.github/workflows/publish-signed.yml` verifica testes e
assinatura antes de criar uma GitHub Release com tag de preview.
Depois de integrar a branch de desenvolvimento à branch padrão (para que
o workflow seja registrado), execute-o manualmente em GitHub → Actions
→ Publish Signed MaizoSNES. Informe, por exemplo, `v0.6.0-preview`,
e confirme que os requisitos de licenciamento e redistribuição foram
avaliados. **A confirmação não altera as licenças dos componentes.**

Não adicione arquivo `.jks`, senha, certificado privado ou ROM ao Git.
Guarde backup da chave de assinatura: sem ela, não é possível atualizar
o mesmo `applicationId` com assinaturas consistentes.

Distribuição não comercial e direitos de terceiros: leia
`LICENSE_DECISION.md` e `THIRD_PARTY_NOTICES.md`. A licença de
Snes9x limita uso comercial. Não há chave de produção configurada
automaticamente; até instalar seus segredos e concluir a revisão
de licenças, somente o APK debug da CI é produzido.
