# Free My TV — Fase 4: Filmes

**Status:** catálogo de filmes implementado em Activity nativa do Android TV. Compilação CI e teste em TCL são critérios separados; não declarar homologação na TV apenas com compilação.

## Funcionalidades implementadas

- Nova Activity interna **`MoviesActivity`**, acessível pelo menu superior **Filmes** tanto na Home quanto na aba Agora na TV.
- Tela escura no estilo do dashboard da Fase 2, com barra de navegação superior: **Início | Agora na TV | Filmes | Séries | Apps**.
- Grade responsiva de cartazes TMDB: 3 a 7 colunas conforme a largura/densidade da TV, poster, título, ano e avaliação disponível.
- Filtros por gênero: Todos, Ação, Aventura, Animação, Comédia, Crime, Documentário, Drama, Família, Fantasia, História, Terror, Música, Mistério, Romance, Ficção científica, Suspense, Guerra e Faroeste.
- Ordenação: Populares, Bem avaliados (com filtro de quantidade mínima de votos) e Mais recentes (sem lançamentos futuros).
- Busca por título, através de `/search/movie` do TMDB, além da consulta `/discover/movie`.
- **Carregamento progressivo real:** `GridView` recicla os cards e consulta a próxima página da API quando a rolagem/foco chega perto do final. Não cria todos os cartões de uma só vez.
- Deduplicação por ID de filme e proteção contra resposta de busca antiga sobrescrever um filtro novo.
- Poster remoto baixado fora da thread principal; cache de imagens existente `HomeImageCache` e memória limitada a cerca de 45 imagens.
- O usuário pressiona **OK** no filme para **copiar o título para a área de transferência e abrir UniTVnet**. O Free My TV não tenta acionar a busca interna do UniTVnet nem usar acessibilidade.
- Se a chave TMDB não estiver configurada, exibe mensagem e atalho para os ajustes. Em falha de rede, oferece tentar novamente, sem apagar resultados já carregados.

## Limitações explícitas

- Embora a experiência seja de rolagem contínua, a **API do TMDB limita a paginação e os resultados**. O código respeita o máximo de 500 páginas, ou menos conforme `total_pages`; não é acesso ilimitado a todos os filmes existentes.
- O projeto continua dependendo de **chave TMDB fornecida pelo usuário** nas configurações da própria instalação. Nenhuma chave é embutida no APK.
- Filmes só estão disponíveis quando aparecem no catálogo TMDB. Não são garantidos no UniTVnet.
- A aba Séries ainda utiliza a tela Discover anterior e será convertida em tela nativa na **Fase 5**.
- Neste momento, o botão `Explorar novidades` do banner segue abrindo o Discover misto; apenas o menu **Filmes** usa a nova Activity.
- Não existe integração direta com um canal ou reprodução dentro do UniTVnet: só abre o aplicativo após copiar o nome.

## Isolamento e segurança

- Pacote independente de testes `io.freemytv.homep4test`.
- Conserva `HomeActivity` como a única entrada HOME/LEANBACK do aplicativo, preservando `HomeView` clássico como fallback.
- Continua incluindo guia EPG compacto, canais fixados e linha de apps, sem alterar seus respectivos identificadores.
- Acessibilidade do UniTVnet não é necessária.
- Por ser outro pacote, os canais já fixados, a chave TMDB e os aplicativos personalizados em instalações anteriores **não migram automaticamente**.
- Antes de homologação final, utilizar chave de assinatura persistente em local seguro para permitir atualizações sem reinstalação.

## Checklist na TCL

- [ ] Instala ao lado de versões anteriores sem alterar o launcher padrão.
- [ ] Ícone abre a Home nova (e não Discover, nem Ajustes).
- [ ] Filmes no menu superior abre a nova grade de pôsteres.
- [ ] Gêneros alteram resultados, sem duplicar ou misturar páginas de categorias diferentes.
- [ ] Controle remoto: setas, foco visível, OK e Voltar funcionam.
- [ ] Rolar até o fim carrega uma segunda página sem travar a Home.
- [ ] Mudança rápida de gênero durante carregamento não insere resultado da consulta anterior.
- [ ] Ordem Populares/Bem avaliados/Mais recentes muda a consulta.
- [ ] Busca por nome funciona com caracteres portugueses.
- [ ] Selecionar filme copia título e abre UniTVnet; colagem encontra o nome.
- [ ] Ao voltar à Home, menu Agora na TV e apps fixados permanecem funcionais.
- [ ] Com rede desconectada aparece mensagem e botão para tentar de novo.
- [ ] Sem chave TMDB aparece configuração orientativa.

## Próximo passo

**Fase 5: Séries**, idealmente reutilizando o mesmo padrão de UI, cache, paginação, filtros próprios do TMDB TV e ação de copiar título e abrir UniTVnet.
