# Free My TV — Etapa 7: homologação TCL e preparação da versão definitiva

**Status: compilação e testes automáticos aprovados. Homologação na TCL real ainda pendente.**

## O que mudou

O HomeActivity continua sendo o receptor real HOME do Android, e todas as outras Activities permanecem internas. A Home clássica original está no APK como fallback para falhas ao inicializar, retomar ou restaurar a nova tela.

O banner TMDB e o EPG agora têm identificadores separados de requisições, evitando que o carregamento de um invalide o outro. Requisições do banner interrompidas ao sair da Home são repetidas ao retornar. O cache local aceita imagens de até 900 KB, mas continua limitado a 80 itens e 16 MiB. O gestor de canais/apps preserva a posição de rolagem ao reorganizar.

## Identidade deste APK

- **Pacote**: io.freemytv.homep7test
- **Nome**: Free My TV Fase 7 (Teste)
- **VersionName**: 3.1-rc-phase7
- **VersionCode**: 7
- **Assinatura**: de testes, caso os Secrets de assinatura persistente não tenham sido configurados
- **TMDB**: requer a chave configurada manualmente nesta instalação
- **Pacotes anteriores**: continuam instalados, sem substituição automática

## Teste seguro com a TCL

Mantenha instalado o launcher que já funciona. Esta versão tem um pacote separado, portanto não precisa substituí-lo.

1. Instale a Fase 7 pela interface da TV ou via ADB. Não altere a escolha do launcher padrão.
2. Abra o app de teste pelo ícone ou pelo comando:

        adb shell am start -W -n io.freemytv.homep7test/io.freemytv.HomeActivity

3. Teste o controle remoto nas cinco abas Início, Agora na TV, Filmes, Séries e Apps.
4. Em Agora na TV, pressione OK rapidamente e depois segure OK em um canal. Verifique que o clique abre UniTVnet e a pressão longa fixa/desafixa o canal.
5. Volte à Home e confira nome, programação e progresso nos canais fixados.
6. Em Organizar, suba/desça canais e apps. Confira foco e posição de rolagem.
7. Carregue mais de uma página dos catálogos. Troque rapidamente de categoria durante a busca.
8. Abra UniTVnet a partir de um filme/série. O nome deve estar copiado, mas a busca interna continua manual.
9. Saia e volte do UniTVnet e confira que o EPG e o banner não ficam permanentemente vazios.
10. Desligue temporariamente o Wi-Fi, volte à Home e confira os estados de fallback.
11. Feche e reabra o app; reinicie a TV; confira a persistência dos favoritos.
12. Confirme que o launcher já instalado permanece funcional e selecionável.

## Diagnóstico pelo Windows

O script de suporte se chama **tools/tcl-diagnostico.ps1** e exige ADB instalado e uma única TV autorizada. Ele não desinstala apps, não limpa dados e não troca o launcher padrão.

Para testar um APK disponível na pasta:

    powershell -ExecutionPolicy Bypass -File .\tcl-diagnostico.ps1 -ApkPath ".\FreeMyTV-Fase-7.apk"

Quando a Fase 7 já estiver instalada:

    powershell -ExecutionPolicy Bypass -File .\tcl-diagnostico.ps1

Para apenas coletar informações sem abrir o aplicativo:

    powershell -ExecutionPolicy Bypass -File .\tcl-diagnostico.ps1 -NoLaunch

O script cria arquivos de texto com modelo da TV, versão do Android, foco e erros recentes. **Revise os logs antes de compartilhar**, porque também podem mencionar outros aplicativos.

## Backup dos favoritos

A Fase 6 é a primeira com o botão **Copiar favoritos**. Na Fase 6, abra Organizar, copie o código de favoritos, instale a Fase 7, abra Organizar e escolha Restaurar favoritos. O formato é validado e exige confirmação antes de substituir canais/apps.

A chave TMDB, senhas, contas e histórico do UniTVnet/TizenTube não fazem parte do código de backup. Elas não migram automaticamente. As Fases 1–5 não conseguem exportar esse código porque ainda não tinham a função.

## Assinatura persistente e atualização

**Não foi criada nem publicada uma chave privada permanente.** O workflow GitHub Actions aceita opcionalmente os Secrets FREEMYTV_KEYSTORE_BASE64 e FREEMYTV_KEYSTORE_PASS.

Antes de uma versão definitiva:
- Escolher um applicationId de produção e mantê-lo nas atualizações.
- Gerar uma chave privada de assinatura em computador confiável e guardá-la com cópia de segurança protegida.
- Configurar os Secrets diretamente no GitHub. Nunca enviar a chave ou senha por conversa, anexos públicos ou commit.
- Preservar a mesma assinatura e mesmo pacote nas futuras versões, aumentando versionCode a cada atualização.
- Fazer testes de instalação e atualização com esse certificado antes da publicação.

**Os APKs anteriores, assinados com uma chave provisória gerada em cada runner, não podem ser atualizados em cima com um certificado diferente.** Para migrar os dados, use o backup manual dos favoritos, quando disponível.

## Critérios para concluir a homologação

- [ ] Nenhuma tela preta ou travamento recorrente na TCL.
- [ ] Navegação por setas e OK funciona em todas as abas.
- [ ] EPG e banner carregam sem interferência entre requisições.
- [ ] Pressão longa do OK funciona somente na lista Agora na TV para fixar/desafixar.
- [ ] Favoritos persistem e podem ser reordenados/restaurados.
- [ ] Catálogos carregam novas páginas sem duplicação.
- [ ] Ausência de crashes frequentes de HomeActivity nos logs.
- [ ] Launcher anterior continua instalado e pode ser escolhido caso necessário.
- [ ] Certificado permanente e package ID de produção definidos antes da primeira release final.

## Pendências futuras

Integração do Continue Assistindo com TizenTube/Watch Next **não está implementada ou validada**. Cobertura de EPG depende das fontes brasileiras externas. Abrir UniTVnet não significa sintonizar ou pesquisar automaticamente dentro do aplicativo.
