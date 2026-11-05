# Free My TV · Etapa 9: homologação técnica automatizada

**Status da fase:** submetido a compilação e testes estáticos. Um segundo job usa emulador Android para testar inicialização da Home, comandos D-pad, atalho GUIDE e detectar crashes. Esses testes **não equivalem a usar uma TCL física**.

## O que esta etapa adiciona

- Build isolado `io.freemytv.homep9test`, versão `3.3-smoke-phase9`, código de versão `9`. Não interfere nos APKs anteriores ou no launcher padrão.
- Mantém Home moderna com fallback clássico, EPG compartilhado (6 fontes), menus, filmes, séries e organizador de favoritos.
- Reexecuta os testes de regressão das fases 6, 7 e 8 para impedir perda de funções.
- Novo job de emulador Android API 29, usando `reactivecircus/android-emulator-runner@v2`, que instala **o mesmo APK** criado pelo job de compilação.
- Teste `tests/phase9_emulator.sh` abre `HomeActivity`, envia comandos do controle D-pad, acessa o atalho GUIDE, retorna à Home e verifica se o processo segue vivo e se ocorreram falhas fatais no pacote.
- Os logs e as informações de janela/atividade do emulador são guardados em artefato separado.

## Limites de validação

- Um emulador Android genérico não reproduz o controle remoto, GPU, firmware, resolução e serviços proprietários da TCL. Um job verde não confirma a qualidade visual nem a navegação física.
- O emulador não possui UniTVnet, TizenTube ou chaves TMDB do usuário, então não pode confirmar pesquisa/colagem no UniTVnet, reprodução, apps externos nem posterização real do TMDB com credencial.
- O EPG externo é mutável e pode estar incompleto; o teste de interface não verifica cobertura do guia.
- O uso da palavra **homologação** no nome da fase significa execução de testes e coleta de evidências; a aprovação real na TCL continua pendente.
- A assinatura permanente ainda não foi provisionada. O aplicativo usa o certificado provisório de CI se os segredos de assinatura não tiverem sido configurados.

## Na TCL: checklist de aprovação física

1. Mantenha o launcher atual instalado e configurado como padrão; não desative pacotes de sistema.
2. Instale o APK de teste da Fase 9 ao lado das versões anteriores.
3. Abra a Fase 9 e confirme que a Home mostra menu, banner e faixas de TV/apps sem tela preta.
4. Teste setas/OK e Voltar em todas as abas.
5. Abra **Agora na TV**, fixe canais segurando OK e confira se aparecem na Home.
6. Confira títulos/horários de EPG, sem presumir que todos os canais existem no UniTVnet.
7. Teste a busca por filmes e séries, rolagem de várias páginas e cópia de títulos antes de abrir o UniTVnet.
8. Reordene canais/apps e restaure um backup exportado de Fase 6, 7 ou 8; nunca importe código de origem não confiável.
9. Reinicie a TCL e retorne ao launcher atual para confirmar que não houve alteração no padrão.
10. Se acontecer algum erro, execute `tools/tcl-diagnostico.ps1` no Windows com ADB conectado e revise os logs antes de compartilhar.

## Preparação da versão definitiva

A **Etapa 10** exige escolher um package ID definitivo, criar/configurar uma chave de assinatura privada estável, testar atualização com dados preservados e homologar na TCL antes de tornar a versão o launcher padrão. Não existe uma chave privada definitiva criada ou entregue nesta fase.
