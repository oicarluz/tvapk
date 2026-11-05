# Free My TV — Etapa 5: Série e catálogo compartilhado

**Status:** APK compilado pelo GitHub Actions. Validação real na TCL ainda pendente.

## Funcionalidades implementadas

A aba **Séries** agora tem interface Android TV própria, com o mesmo padrão visual e navegação da aba Filmes. As duas utilizam uma única classe de interface (`MediaCatalogActivity.java`), minimizando código duplicado e facilitando manutenção.

- **Filmes**: `MoviesActivity` herda o catálogo compartilhado, usando `/discover/movie` e `/search/movie` do TMDB.
- **Séries**: `SeriesActivity` herda o mesmo catálogo, usando `/discover/tv` e `/search/tv`, com extração dos campos `name`, `first_air_date` e `poster_path` apropriados.
- **Gêneros de séries** separados dos de filmes: Todos, Ação e aventura, Animação, Comédia, Crime, Documentário, Drama, Família, Infantil, Mistério, Notícias, Reality, Ficção e fantasia, Novelas, Talk Show, Guerra e política e Faroeste.
- **Carregamento progressivo** do catálogo por página ao se aproximar do fim da grade; cartões são reciclados pelo GridView.
- **Ordenação** por popularidade, avaliação ou estreia mais recente; **busca por título** em português.
- **Pôsteres** baixados em background, com cache local e cache de memória limitado. Foco visual para o controle remoto.
- **OK sobre uma série** copia o título para a área de transferência e abre o aplicativo UniTVnet instalado. A pesquisa dentro do UniTVnet ainda é manual.
- **Menu superior** abre a aba Séries nativa tanto a partir da Home como do guia Agora na TV e da aba Filmes.
- O menu **Filmes** permanece funcional, agora reutilizando a mesma lógica.

## Componentes

- `extensions/MediaCatalogActivity.java`: UI e comportamento compartilhados.
- `extensions/MoviesActivity.java`: Activity de Filmes.
- `extensions/SeriesActivity.java`: Activity de Séries.
- `extensions/HomeDashboardView.java` e `extensions/EpgActivity.java`: rotas atualizadas.
- `personalizar.py`: inclui as novas Activities e seu registro no manifesto com processo `:home`.
- `.github/workflows/gerar-apk.yml`: valida a rota, o pacote de teste, a categoria Android HOME e as duas Activities.

## Segurança e limites

- APK separado: `io.freemytv.homep5test`; **não substitui as instalações anteriores**.
- A chave TMDB deve ser configurada no aplicativo de teste, já que pacotes distintos não compartilham configurações.
- O app apenas copia títulos e abre o UniTVnet. Não lê credenciais, não automatiza interfaces privadas e não exige acessibilidade.
- A existência de um título no TMDB **não garante disponibilidade de reprodução no UniTVnet**.
- Apesar de visualmente contínua, a listagem respeita o número de páginas disponibilizadas pelo TMDB, com limite técnico de até 500 páginas.
- Fontes EPG externas podem ficar indisponíveis; a Home apresenta informação ausente quando não houver dados.
- Não definir esta versão como launcher padrão antes dos testes físicos.

## Checklist de homologação na TCL

- [ ] O APK instala ao lado da Etapa 4 e abre a Home com menu superior.
- [ ] **Filmes** continua mostrando conteúdo correto após o refatoramento.
- [ ] **Séries** apresenta só séries e seus próprios gêneros.
- [ ] A busca retorna séries por título e não filmes homônimos.
- [ ] Ordenação e filtros de gênero funcionam.
- [ ] Ao alcançar o fim da grade, novas páginas chegam sem duplicatas nem congelamento.
- [ ] Trocar rapidamente de gênero durante carregamento não mistura resultados antigos.
- [ ] Pressionar OK em uma série copia o título e abre UniTVnet.
- [ ] Voltar do UniTVnet retorna ao launcher de modo estável.
- [ ] Menus Início, Agora na TV, Filmes, Séries e Apps permanecem navegáveis pelas setas do controle.
- [ ] Os canais fixados continuam aparecendo na Home.
- [ ] Sem conexão ou sem chave TMDB surge mensagem útil, sem fechar o launcher.

## Próxima etapa

A próxima etapa prevista é o **polimento e a homologação**: correção de foco, desempenho e comportamento da TCL com base nos testes de campo, preservação dos favoritos entre atualizações e preparação de assinatura persistente para uma instalação principal estável. A integração de TizenTube / Watch Next exige validação específica separada.
