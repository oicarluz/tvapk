# Free My TV — Etapa 10: preparação da versão definitiva

**Status: a versão de teste pode ser compilada. A versão definitiva fica BLOQUEADA até a homologação física na TCL e a configuração da assinatura privada do proprietário.**

## Identidades e atualizações

| Destino | Pacote Android | VersionName | VersionCode |
| --- | --- | --- | --- |
| Pré-lançamento de teste | io.freemytv.homep10test | 3.4-phase10-preview | 10 |
| Produção estável | io.freemytv.tvlauncher | 1.0.0 | 100 |
| Free My TV atual | io.freemytv | Independente | Independente |

A identidade de produção io.freemytv.tvlauncher será preservada em futuras versões. Um APK só poderá atualizar outro sem apagar dados se usar **o mesmo pacote e a mesma assinatura**, com versionCode maior ou compatível.

## Requisitos obrigatórios para produção

1. **Homologar fisicamente na TCL**: Home, menu, OK curto/longo, guia, foco do controle, UniTVnet, TMDB, favoritos, apps, reinicialização e desempenho.
2. **Gerar uma chave privada permanente** em computador do proprietário. Guardar backups protegidos, fora do GitHub e deste chat.
3. **Configurar Secrets diretamente no GitHub**: FREEMYTV_KEYSTORE_BASE64, com o arquivo JKS convertido em base64, e FREEMYTV_KEYSTORE_PASS, com a senha.
4. Configurar a variável de repositório FREEMYTV_RELEASE_CERT_SHA256 com o SHA-256 do certificado, sem dois-pontos. É um fingerprint público, não a chave.
5. Revisar e incluir a branch da Etapa 10 na branch principal. Na tela Actions, iniciar o fluxo **manualmente** com gerar_producao=true, confirmar_tcl=TCL_APROVADA, versionCode e versionName.
6. Conferir e instalar manualmente o APK de produção. O pipeline **não muda o launcher padrão**, não publica releases automaticamente e não envia certificados privados ao artefato.

O código recusa release sem keystore e senha. O workflow também recusa se faltarem os Secrets, a confirmação TCL, o fingerprint esperado ou uma versão válida.

## Gerar a assinatura no Windows

Com Java/JDK instalado, abra o PowerShell numa pasta de backup privada e execute:

    keytool -genkeypair -v -keystore freemytv-producao.jks -alias freemytv-release -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Free My TV"

Use a mesma senha para o keystore e a chave interna. Deixe o programa solicitar a senha interativamente, em vez de expô-la como argumento.

Para conferir o SHA256 público:

    keytool -list -v -keystore freemytv-producao.jks -alias freemytv-release

Para copiar o JKS codificado em base64, exclusivamente para cadastrar no formulário de Secrets do GitHub:

    [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\freemytv-producao.jks))) | Set-Clipboard

**Nunca envie o JKS, a senha ou o texto base64 neste chat.** Apague a área de transferência depois. Guarde cópias de segurança seguras do arquivo; perdê-lo pode impedir futuras atualizações.

## Preservação dos dados na atualização

- Na primeira publicação, usar versionCode 100 e versionName 1.0.0.
- Atualizações: manter o mesmo pacote e certificado, subindo versionCode 101, 102 e assim por diante.
- Teste obrigatório após provisionar a chave: instalar versão 100, criar favoritos, instalar a versão 101 por adb install -r, e confirmar que favoritos, ordem de apps e ajustes continuam.
- **Esse teste ainda não foi executado**, porque não há chave durável configurada.

Favoritos das Fases 6 a 9 podem ser exportados manualmente e restaurados na Fase 10. A chave TMDB não é exportada, e pacotes de teste isolados não compartilham armazenamento privado.

## O que o aplicativo faz e não faz

Implementado: nova Home, EPG com seis fontes XMLTV, até 30 canais fixados, gestão de apps, catálogos paginados de Filmes e Séries, copiar nome de título e abrir UniTVnet, backup manual de favoritos.

Não implementado: sintonizar automaticamente um canal em UniTVnet, pesquisar dentro dele sem suporte oficial e integrar Continue Assistindo do TizenTube. A existência do título no TMDB não significa que exista no UniTVnet.

## Testes e distribuição

- Testes estáticos: etapas 6/7/8 + validação específica da identidade/assinatura.
- Teste automatizado do APK de pré-lançamento no emulador Android: Home, D-pad, GUIDE e verificação de falha fatal.
- Homologação em TCL: obrigatória e pendente, mesmo se CI estiver verde.
- O APK pré-lançamento usa uma assinatura provisória e não deve ser escolhido como launcher padrão.
- A versão definitiva só poderá ser gerada depois da configuração real da chave privada, da confirmação da TCL e do gate manual do workflow.

## Atribuição

O projeto é derivado do Free My TV v1.3.0, sob licença MIT, Copyright (c) 2026 Free My TV contributors. O aviso e a licença MIT original devem acompanhar qualquer redistribuição de cópias substanciais do software.
