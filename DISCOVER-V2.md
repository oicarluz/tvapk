# Free My TV · Discover v2 (homologação)

Base: Free My TV v1.3.0, personalizado pelo script `personalizar.py`.

## O que está implementado
- Radar de novidades com datas de 7/30/90 dias, separado para filmes e séries.
- Descobrir por gênero, nota, tipo e busca por título.
- Para você: recomendações por gêneros dos favoritos locais, excluindo itens já salvos.
- Minha Lista: Quero assistir / Assistindo / Já assisti, salva na TV.
- Trailers do TMDB com abertura do YouTube.
- Alertas consultados ao abrir a aba, para novas temporadas e próximos episódios das séries salvas.
- Integração com UniTVnet: copiar título e tentar preencher campo de busca focado via serviço de acessibilidade (requer autorização do usuário).
- Atalho da tecla SEARCH ou GUIDE **somente enquanto a tela inicial Free My TV está em primeiro plano**. MENU abre busca dentro de Descobrir; INFO abre Minha Lista do título selecionado.

## Limites honestos
- A compilação não prova funcionamento do UniTVnet nem da navegação em um controle TCL real.
- Alertas são consultados ao abrir a aba; não há notificações em segundo plano.
- O preenchimento automático depende do UniTVnet expor o campo editável via acessibilidade e do serviço estar habilitado.
- O algoritmo Para você usa gêneros, não um modelo de IA.
- O TMDB fornece metadados, não garante disponibilidade no UniTVnet.
- O launcher atual e a role HOME não são alterados pelo novo código. O usuário não deve instalar o APK de homologação antes de testes e plano de assinatura.

## Segurança e atualização
A chave `freemytv.keystore` **não deve ser publicada como artefato público**. A compilação de homologação gera uma nova chave no runner e, portanto, **não é uma atualização instalável sobre a versão já assinada anteriormente**. Para uma atualização sem desinstalar, é necessário assinar com a mesma chave privada da instalação atual, guardada de forma segura e fora do repositório.

## Validação
GitHub Actions: `Compilar Free My TV + UniTVnet` na branch `feature/discover-v2`.
O workflow valida a sintaxe Python, aplica o patch ao código original v1.3.0, compila com Android SDK 34 e publica somente o APK para homologação. Verifique o status da execução e os logs antes de instalar.

## Arquitetura
`extensions/DiscoverData.java`: consultas TMDB, filtros, favoritos e alertas.
`extensions/DiscoverActivity.java`: interface Android TV, navegação e ações.
`personalizar.py`: aplica integração ao código upstream.
