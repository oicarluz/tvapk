# Free My TV — Etapa 8: programação consistente e navegação estável

**Estado:** implementação e compilação de homologação, sujeitas à aprovação física na TCL. Esta fase não altera o aplicativo atual nem publica uma versão de produção.

## O que foi corrigido

### 1. Uma fonte de programação para o guia e a Home

Até a Etapa 7, a tela **Agora na TV** consultava seis fontes externas de EPG, mas a Home usava somente quatro. As rotinas tinham duplicação de downloads/cache. Isso podia fazer a programação aparecer no guia, mas não na faixa de canais da Home.

Agora os dois componentes consultam **EpgFeedCache**, com as mesmas fontes XMLTV:

- globo.xml
- maissbt.xml
- epg.xml
- claro.xml
- plutotv.xml
- xsports.xml

Os XMLTV continuam hospedados no projeto de terceiros BrazilTVEPG (limaalef), não retransmitem canais e podem ficar indisponíveis ou incompletos. O cache local dura quatro horas, oferece uma cópia anterior caso não haja internet e limita cada arquivo a 30 MB. Downloads e parsing rodam fora da thread de interface.

### 2. Identificação consistente dos canais

O guia antes usava normalização diferente da lista de fixados. Nomes contendo pontuação, espaços especiais e outros caracteres poderiam ser salvos com uma chave que não coincidia com a chave de consulta da Home.

O guia agora cria a chave com o mesmo método **HomeChannelPins.canonical** usado ao salvar favoritos. O EPG da Home consulta os mesmos nomes canônicos, sem tentar adivinhar a programação de uma afiliada diferente.

A regra de interação permanece:
- **OK curto na aba Agora na TV:** abre UniTVnet.
- **Segurar OK na aba Agora na TV:** fixa/desafixa canal.
- **Home:** exibe apenas os até 30 canais escolhidos, numa faixa horizontal.

### 3. Foco do controle remoto e rolagem

Atualizar o EPG da Home antes reconstruía as células da faixa horizontal, podendo jogar o foco do controle para outra posição e deslocar o usuário. Agora a faixa tenta restaurar o canal que estava selecionado e sua rolagem horizontal.

Da mesma forma, o guia compacto tenta preservar o canal e a posição vertical ao receber mais uma fonte. A ação **Carregar mais canais** conserva seu comportamento próprio de navegação.

### 4. Testes adicionais

O workflow executa:
- testes de regressão das Etapas 6 e 7 com os parâmetros desta instalação;
- **tests/phase8_smoke.py**, que compara as rotas do EPG e da Home, exige o cache compartilhado e chaves canônicas e verifica a restauração do foco;
- compilação Android nativa e verificação de assinatura, alinhamento e manifesto.

Esses testes não simulam uma TCL real. Eles não comprovam que as seis fontes estão disponíveis neste exato momento nem que o UniTVnet procura o canal automaticamente.

## Identidade do APK de testes

- Nome: **Free My TV Fase 8 (Teste)**
- Package ID: **io.freemytv.homep8test**
- VersionName: **3.2-epg-phase8**
- VersionCode: **8**
- Nova instalação paralela. Não substitui automaticamente a Fase 7 ou o launcher principal.
- Chave TMDB: deve ser configurada nesta instalação.
- Favoritos: podem ser exportados manualmente a partir de uma instalação Fase 6 ou Fase 7 usando **Copiar favoritos** e importados na Fase 8 usando **Restaurar favoritos**.
- Assinatura permanente: ainda depende de configurar uma chave privada própria e os Secrets do GitHub. As assinaturas provisórias do CI não servem para atualização sem perda de instalação.

## Checklist de homologação na TCL

1. Mantenha a versão de launcher atual como padrão. Instale a Fase 8 em paralelo.
2. Abra Início, Depois Agora na TV. Confira categorias e a lista, inclusive fontes de Esportes e demais canais.
3. Escolha um canal, segure OK e volte à Home. Ele deve aparecer na faixa horizontal.
4. Confira a programação. Ela deve ser consistente entre o guia e a Home quando o EPG disponibilizar os dados.
5. Deixe o foco em um canal da faixa na Home e volte do UniTVnet. O foco e a posição horizontal não devem ser perdidos.
6. No guia, mova o foco para baixo enquanto novas fontes chegam. A rolagem não deve voltar abruptamente ao topo.
7. Teste as abas Filmes e Séries com chave TMDB configurada, carregamento progressivo, gêneros e cópia do título ao abrir o UniTVnet.
8. Teste Organizar, alterar ordem de canais/apps, copiar e restaurar favoritos.
9. Desconecte temporariamente o Wi-Fi e confirme que a Home abre e exibe os dados disponíveis ou estados de ausência.
10. Reinicie a TV e confirme que a instalação anterior continua funcionando.

## Diagnóstico pelo computador Windows

No pacote, há o script **tcl-diagnostico.ps1**. Com ADB instalado e a TCL autorizada, execute numa pasta contendo o script e o APK:

    powershell -ExecutionPolicy Bypass -File .\tcl-diagnostico.ps1 -ApkPath ".\FreeMyTV-Fase-8.apk"

O script instala/aponta apenas para **io.freemytv.homep8test**, abre sua Home e coleta logs recentes. Não desativa o launcher padrão, não limpa dados e não desinstala os outros aplicativos. Logs podem conter dados de outros aplicativos, portanto revise antes de compartilhar.

## Pendências da próxima etapa

- Teste presencial de estabilidade, desempenho e navegação na TCL com logs, caso ocorram falhas.
- Escolha de package ID definitivo e chave de assinatura permanente, antes de uma versão de produção atualizável.
- Integração de **Continue assistindo** com TizenTube/Watch Next: **não implementada**, pois ainda requer verificar se o aplicativo fornece dados acessíveis e com autorização.
- Cobertura regional e qualidade do EPG dependem de terceiros; horários não devem ser apresentados como garantidos.

Branch: **feature/epg-catalog-hardening-phase-8**. Esta documentação distingue compilação aprovada de homologação física, que ainda depende da sua TCL.
