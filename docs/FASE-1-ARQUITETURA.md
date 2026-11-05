# Free My TV: Fase 1 — Arquitetura e base

Status: **base implementada no código**. Validar compilação no GitHub Actions e comportamento na TCL antes de promover para launcher principal.

## Escopo aprovado

Home final desejada:

1. Barra superior: **Início | Agora na TV | Filmes | Séries | Apps**.
2. **Início**: banner grande de lançamentos, até **30 canais escolhidos pelo usuário** em faixa horizontal, aplicativos fixados em faixa horizontal.
3. **Agora na TV**: EPG compacto por categorias; **OK curto abre UniTVnet**; **OK longo fixa/desafixa** na Home.
4. **Filmes** e **Séries**: catálogos TMDB paginados por gênero, carregamento progressivo; OK copia o nome e abre UniTVnet.
5. **Apps**: aplicativos Android TV instalados; manter recursos existentes de fixação.

## Decisões técnicas da fase 1

- **Base de código:** Free My TV 1.3.0, preservando `HomeActivity`, `HomeView`, navegação e layout do launcher original.
- **Pacote de homologação separado:** `io.freemytv.homep1test`. Não ativa a função HOME automaticamente, nem desinstala a instalação principal.
- **Navegação e telas:** as novas abas da Home **não fazem parte da fase 1**. A tela existente do EPG continua acessível a partir do painel inferior (↓, ↓) ou pela tecla GUIDE. O menu superior será implementado na fase 2.
- **Canais:** `HomeChannelPins.java` guarda somente os canais fixados, separados dos aplicativos. Chave canônica de canal, nome e URL HTTPS opcional de logomarca. Nenhum canal é fixado automaticamente.
- **Limite e ordenação:** até 30 canais, sem duplicados. Inclusão na última posição; `move(...)` disponibiliza alteração de ordem nas próximas fases.
- **Persistência:** SharedPreferences local `home_channels_v1` com JSON versionado (`version:1`); gravação síncrona no processo `:home`, compartilhado por Home e EPG. Nenhuma credencial é armazenada.
- **Gesto de fixação:** a tela atual de EPG já permite segurar OK para fixar/desafixar, com indicador ★ na lista e contagem de canais. **Esses canais ainda não são desenhados na Home**.
- **Programação:** o EPG externo é armazenado em XML por até quatro horas, com fallback para cópia antiga em caso de falha de rede. A fonte pode não conter todos os canais/programas e afiliadas.
- **Logomarcas:** `HomeImageCache.java` mantém cache descartável em disco por sete dias, com limite de 80 arquivos, 16 MiB no total e 384 kB por imagem; imagens são decodificadas em background.
- **Filmes e séries:** permanecem com o fluxo TMDB já existente. A cópia explícita do título para a área de transferência e a abertura do UniTVnet continuam suportadas. Paginação por gênero será feita em fases posteriores.
- **Acessibilidade:** nenhuma automação da interface do UniTVnet é instalada ou solicitada nesta fase. Não há tentativa de abrir Activities privadas, extrair credenciais ou reproduzir fluxos de vídeo.
- **Apps fixados:** a bandeja de aplicativos existente de `HomeApps` continua sendo a referência; a nova lista de canais não interfere nela.

## Testes de aceitação antes de prosseguir

- [ ] O APK instala lado a lado e abre a **Home original** na TCL.
- [ ] O ícone do aplicativo abre `HomeActivity`, não Descobrir ou Configurações.
- [ ] O controle remoto continua funcional; setas e OK abrem aplicativos existentes.
- [ ] Em EPG, segurar OK em um canal mostra o indicador ★.
- [ ] Segurar OK de novo remove o indicador e a fixação.
- [ ] Fechar e reabrir o aplicativo mantém a lista de canais.
- [ ] Após 30 canais, a 31ª tentativa informa o limite.
- [ ] A ordem dos canais fixados permanece estável.
- [ ] EPG e logomarcas funcionam com internet; ausência de rede não trava Home.
- [ ] A lista de apps fixados segue independente da lista de canais.
- [ ] O APK **não exige habilitar acessibilidade**.

## Próxima fase (somente depois da homologação)

**Fase 2: Home e menu superior.** Implementar abas nativas no layout original, banner de lançamentos, linha horizontal com até 30 canais fixados e linha de apps. A lista salva nesta fase será a fonte única dos canais exibidos na Home.

> Atenção: compilação bem-sucedida, por si só, não prova o comportamento no dispositivo. Não tornar a versão de teste o launcher padrão antes de verificar a Home na TCL. Os APKs de CI usam assinaturas de homologação; uma versão de produção precisa de uma chave estável protegida.
