# Free My TV: Fase 2 — menu superior e nova Home

**Status:** implementação inicial compilada; ainda não homologada na TCL.

## Objetivo implementado

A nova Home é desenhada diretamente por `HomeDashboardView` dentro de **`HomeActivity`**, que mantém o filtro `HOME + DEFAULT` e a entrada `LEANBACK_LAUNCHER`. O `HomeView` clássico permanece no APK como fallback caso o dashboard não consiga inicializar.

### Menu superior

- **Início:** nova Home nativa.
- **Agora na TV:** abre o guia EPG existente. Fixar/desafixar canal continua disponível segurando OK **na aba/guia Agora na TV**.
- **Filmes:** abre a tela de catálogo existente, pré-filtrada para filmes.
- **Séries:** abre a tela de catálogo existente, pré-filtrada para séries.
- **Apps:** abre lista de apps instalados em grade nativa. O botão de ajustes acessa `SettingsActivity`.

**Limite atual:** o cabeçalho ainda não permanece visível em EPG, Filmes e Séries; essas telas serão unificadas visualmente nas fases de catálogo/guia. Não anunciar como navegação final concluída.

### Início

1. Banner de novidades `/trending/all/week` do TMDB, com título, resumo e imagem de fundo (quando há uma chave TMDB válida no aplicativo de teste). Sem chave ou sem rede, mostra fallback local sem inventar filmes.
2. Faixa horizontal de canais **somente dos fixados** (até 30). A linha contém nome, logo disponível, programa atual e progresso, usando `HomeEpgSnapshot` (parser XMLTV no worker). Nenhum canal é fixado por padrão.
3. Faixa horizontal de apps fixados usando `HomeApps.shelfPackages`, respeitando a seleção já suportada pelo Free My TV na **mesma instalação**.
4. Todos os itens são nativos do Android e recebem foco do controle remoto. Selecionar canal na Home abre o app UniTVnet normalmente, **sem sintonização automatizada**. Selecionar app abre o app correspondente.

### EPG e cache

- O EPG é buscado fora da thread da interface. XMLTV armazenado no cache por até quatro horas com fallback para versão anterior se houver falha.
- Chave de canal normalizada; emissoras e afiliadas diferentes não são associadas por suposição.
- Fotos e logos passam pelo `HomeImageCache` existente.
- A interface mostra `Programação não disponível` quando a fonte não fornecer a grade correspondente, em vez de inventar horários.

### Identidade do APK

- **Pacote de teste:** `io.freemytv.homep2test`, instalável ao lado do Free My TV antigo.
- **Importante:** pacotes diferentes **não compartilham preferências**. Os canais fixados em `homep1test` precisam ser selecionados novamente na fase 2. Da mesma forma, a chave TMDB, quando necessária, deve ser cadastrada nos ajustes da nova instalação.
- A chave de assinatura gerada pelo CI é de homologação, não de distribuição permanente.
- Não é necessário nem esperado ativar acessibilidade.
- Nenhuma Activity privada do UniTVnet, conteúdo de reprodução, credenciais ou fluxo DRM é lido.

## Plano de validação na TCL

- [ ] Instalar sem desinstalar a versão já funcional.
- [ ] Abrir a Home pelo ícone do novo APK: menu superior, banner, canais, apps.
- [ ] Testar setas, OK e Voltar no controle TCL, com rolagem horizontal e vertical.
- [ ] Clicar em Apps e retornar para Início.
- [ ] Abrir Filmes e Séries pelo menu superior, verificar seleção de tipo.
- [ ] Abrir Agora na TV, segurar OK para fixar canal, retornar à Home e confirmar que aparece na faixa.
- [ ] Fixar outros canais e testar deslocamento horizontal na Home.
- [ ] Fechar/reabrir e confirmar que a seleção de canais persiste.
- [ ] Verificar que a linha de apps é independente da linha de canais.
- [ ] Testar sem internet: Home deve abrir, exibindo fallbacks.
- [ ] Configurar TMDB nos ajustes desta nova instalação e verificar banner.
- [ ] Abrir UniTVnet ao clicar no canal, sem promessa de sintonização automática.
- [ ] Não definir a versão como launcher padrão antes de concluir os testes.

## Próximas fases

- **Fase 3:** transformar Agora na TV em guia compacto com categorias e navegação uniformizada pelo menu superior.
- **Fase 4/5:** catálogos de Filmes e Séries por gênero com paginação e carregamento progressivo, preservando cópia do título e abertura do UniTVnet.
- **Fase 6:** personalização avançada e gestão de apps.
- **Fase 7:** testes prolongados na TCL, assinatura de release persistente e implantação após aprovação.
