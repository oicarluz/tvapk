# Free My TV — Fase 3: Guia compacto de TV ao vivo

**Status:** implementação Android compilada. Exige validação de controle remoto e EPG diretamente na TCL antes de liberar como launcher principal.

## O que foi feito

- O menu superior do guia agora segue as mesmas seções da nova Home: **Início, Agora na TV, Filmes, Séries e Apps**.
- O EPG deixou de apresentar cartões verticais altos e passou a mostrar **linhas compactas**, com emissora, programa atual, horário/barra de progresso e próxima atração.
- Categorias: **Todos, Abertos, Fechados, Esportes, Filmes, Infantil, Notícias e Fixados**.
- Os nomes nas categorias temáticas são classificados por palavras-chave da emissora, **não por uma lista oficial certificada**. Alguns canais podem ficar na categoria incorreta; a aba Todos sempre conserva as entradas que as fontes publicam.
- A lista é paginada em blocos de **64 canais**, com opção para carregar mais. As fontes externas são lidas em background e seus dados ficam em cache.
- **OK curto**: abre o aplicativo UniTVnet normalmente, sem promessa de entrar diretamente no canal.
- **OK pressionado por aproximadamente 625 ms**: fixa ou desafixa o canal, com feedback de status. Esse comportamento está implementado diretamente nos eventos do controle, sem serviço de acessibilidade.
- A aba **Fixados** apresenta os canais salvos na Home na ordem escolhida. Se a fonte EPG estiver offline, seus nomes ainda estarão disponíveis para remover da lista.
- A faixa de até **30 canais fixados na Home** continua lendo a mesma lista `HomeChannelPins` dentro da mesma instalação.
- Canais fechados como canais de notícias e esportes não devem mais ser classificados como abertos apenas por começarem com o nome da rede principal.
- Uma fonte de EPG indisponível não impede as outras fontes de carregarem; se todas falharem, é exibida mensagem de erro.

## Dados e limitações

- EPG externo: `limaalef/BrazilTVEPG`, usando arquivos XMLTV públicos; cobertura, horários, canais e logomarcas dependem da disponibilidade da fonte. Não há retransmissão de sinal de TV.
- O guia trata aproximadamente **6 arquivos XMLTV** (sujeitos a mudança externa), usa cache por até quatro horas e faz o download fora da thread de interface.
- O app **não lê dados privados, credenciais ou histórico do UniTVnet**. Selecionar um canal simplesmente inicia o aplicativo.
- O menu superior em Filmes e Séries ainda abre o catálogo Discover existente. As fases 4 e 5 criarão a navegação de gêneros/paginação prevista.
- A Home mantém seu banner TMDB, faixa dos canais fixados, faixa dos aplicativos, e código de fallback clássico.

## Instalador

- APK de teste independente: `io.freemytv.homep3test`
- A instalação não substitui as versões das Fases 1 e 2. **Como o pacote de teste é diferente, os canais fixados e a configuração do TMDB não migram automaticamente.**
- Não selecionar a versão como launcher padrão até que a TCL confirme estabilidade; o app original fica intacto.

## Checklist de homologação na TCL

- [ ] Instalar ao lado do Free My TV atual, sem desinstalar nada.
- [ ] Abrir pela lista de aplicativos; verificar a nova Home e o menu superior.
- [ ] Selecionar **Agora na TV** e conferir as oito categorias.
- [ ] Usar as setas do controle para percorrer linhas e filtros.
- [ ] Pressionar OK brevemente em um canal: abrir o UniTVnet.
- [ ] Voltar, segurar OK no canal: surgir marcação ★.
- [ ] Segurar novamente: canal sair dos fixados.
- [ ] Voltar para Início: canal escolhido deve surgir na faixa horizontal.
- [ ] Fechar e reabrir: fixação precisa persistir.
- [ ] Testar com internet e sem internet, sem travamentos.
- [ ] Testar paginação depois do 64º canal, quando a fonte apresentar dados suficientes.
- [ ] Testar a guia **Fixados** com EPG indisponível, sem perder o canal.
- [ ] Confirmar que Filmes, Séries e Apps ainda abrem corretamente.

## Próximos passos

**Fases 4 e 5:** catálogos de Filmes e Séries por gênero, carregamento progressivo, imagens e abertura do UniTVnet com título na área de transferência. O guia já está funcional no código, mas sua homologação na TCL ainda é pendente.
