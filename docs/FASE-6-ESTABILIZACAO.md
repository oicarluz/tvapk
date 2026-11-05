# Free My TV — Etapa 6: Estabilização, favoritos e atualizações

**Status:** código desenvolvido e compilado em GitHub Actions; testes de navegação e desempenho **na TCL real ainda pendentes**. Não tornar esta versão o launcher padrão sem teste.

## Mudanças desta etapa

### 1. Correção da programação da Home

Havia uma corrida de estado em `HomeDashboardView`: `loadEpg()` começava a carregar a programação com um contador de geração, mas `loadHero()` incrementava o mesmo contador logo depois. Quando o EPG voltava, a resposta parecia obsoleta e era descartada. Agora o carregamento do banner não altera a geração de ciclo de vida. Uma sequência de requisições específica (`epgRequestId`) protege o EPG de resultados antigos.

A Home também evita downloads de programação repetidos quando o usuário volta de outro aplicativo dentro de uma janela de cinco minutos e a lista de canais é a mesma. O banner tem intervalo mínimo de cinco minutos entre tentativas fracassadas. Mudanças no conjunto de canais ou no ciclo de vida geram atualização.

### 2. Personalização de favoritos

`HomeFavoritesActivity` é uma tela interna para **organizar, subir, descer e remover** canais da Home e apps fixados. Os canais são adicionados ao segurar OK **em Agora na TV**, como combinado; não alteramos essa regra. Novos apps podem ser adicionados pela lista de apps instalados. A faixa de aplicativos continua usando a preferência nativa `Prefs.setShelf` do Free My TV, e canais continuam com armazenamento próprio limitado a 30.

Na nova Home, o botão **Organizar**, ao lado de Canais fixados ou Apps fixados, abre essa tela. A lista geral de Apps também mostra **Personalizar**.

### 3. Backup manual de canais e aplicativos

A opção **Copiar favoritos** gera um código com prefixo `FREEMYTV-FAV-1:`, salvando **somente** a ordem, IDs e nomes dos canais, logomarcas públicas e pacotes de apps fixados. O código fica na área de transferência por ação explícita do usuário.

A opção **Restaurar favoritos** aceita o código colado (ou o lê da área de transferência quando o usuário abre a função), valida formato, tamanho, duplicatas, lista de 30 canais, pacote de apps, presença dos apps na TV e solicita confirmação antes de substituir o que existe. Apps não instalados são ignorados com aviso. A importação de canais grava a lista validada de maneira atômica.

**Não são incluídos:** chave TMDB, credenciais, configurações de contas, histórico do UniTVnet/TizenTube, dados de reprodução, cookies, senhas.

**Limitação importante:** somente a Fase 6 e versões futuras que mantenham a função podem **exportar** o backup. Os APKs das Fases 1–5 têm pacotes isolados e **não possuem o botão de exportação**; a Fase 6 não consegue ler automaticamente dados privados desses outros pacotes. Os favoritos anteriores precisam ser selecionados uma vez na Fase 6 e podem ser transferidos dali em diante.

### 4. Preparação para uma assinatura de APK estável

O `build.sh` oficial já suporta `FREEMYTV_KEYSTORE` e `FREEMYTV_KEYSTORE_PASS`. Atualizamos o GitHub Actions para aceitar, de forma **opcional**, dois segredos do repositório:

- `FREEMYTV_KEYSTORE_BASE64`: bytes base64 de um arquivo keystore exclusivo do projeto;
- `FREEMYTV_KEYSTORE_PASS`: senha do keystore.

Se ambos estiverem disponíveis, o workflow faz o decode somente no runner, usa a chave para assinar o APK e apaga o arquivo ao final. A chave **nunca** deve ser enviada por conversa, embutida no código, publicada em artefatos ou versionada no GitHub. Não colocar senhas em documentos públicos.

**Se os Secrets não estiverem configurados**, o GitHub Actions usa uma chave provisória gerada a cada compilação: o APK instala, mas uma compilação posterior com certificado diferente **não atualiza por cima**. Para atualizações regulares com dados preservados, as versões devem manter **o mesmo applicationId + a mesma chave de assinatura**. Não é possível recuperar as antigas chaves provisórias a partir dos APKs assinados.

### 5. Verificações automáticas

O workflow executa `python3 tests/phase6_smoke.py projeto` após aplicar os patches, além da compilação `javac/aapt2/d8`, verificação `zipalign`, assinatura `apksigner` e manifesto final. As verificações estáticas garantem o essencial:

- `HomeActivity` continua sendo o receptor real HOME + LEANBACK; telas internas não são exportadas;
- atividade de backup e tela de favoritos estão no manifesto;
- Movies/Series/EPG continuam rotas válidas;
- erro de ciclo de vida do EPG foi corrigido;
- suporte a OK longo no EPG, máximo de 30 canais, backup validado;
- não foi adicionado serviço de leitura de conteúdo de outros aplicativos.

Estes testes não simulam o controle remoto TCL nem a qualidade das fontes EPG/TMDB em tempo real.

## Instalador de testes

- `io.freemytv.homep6test`
- Versão: `3.0-stability-phase6`
- Instala ao lado das versões anteriores; não altera automaticamente a escolha de HOME da TV.
- A configuração de TMDB precisa ser informada novamente nessa nova instalação, porque o ID de pacote é diferente. O backup não transfere a chave.

## Checklist de validação na TCL

- [ ] A instalação abre **Home** sem precisar de acessibilidade.
- [ ] Banner com TMDB e EPG dos canais carregam **juntos**, sem a programação desaparecer.
- [ ] Voltar do UniTVnet não reinicia downloads desnecessários nem causa tela preta.
- [ ] Controles de Apps, Filmes, Séries e Agora na TV seguem com foco visível.
- [ ] Segurar OK em Agora na TV fixa/desafixa corretamente; canal surge na Home.
- [ ] Em **Organizar**, subir/descer/remover canais altera imediatamente a ordem da Home.
- [ ] Em **Organizar**, adicionar/reordenar/remover apps altera a faixa de apps.
- [ ] Copiar favoritos e colar o código funciona; restaurar somente após confirmação.
- [ ] Tentar importar código inválido não destrói favoritos atuais.
- [ ] Com rede desligada, a Home continua funcionando.
- [ ] Testar após reiniciar a TV, sem definir como launcher padrão antes da homologação.

## Etapa seguinte

**Etapa 7:** testes reais na TCL, revisão de foco/desempenho, assinatura persistente e escolha de um identificador definitivo de aplicação. Assim que uma chave estável for configurada, versões posteriores devem usar o mesmo pacote e certificado para permitir atualização preservando os dados. O TizenTube/Watch Next é uma integração independente ainda não validada.
