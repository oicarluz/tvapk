# Free My TV — Etapa 9: homologação de navegação e testes TCL

**Situação: correções de código desenvolvidas; homologação física na TCL ainda pendente.** CI e emulador não substituem o aparelho.

## Identidade da versão

| Propriedade | Valor |
| --- | --- |
| Nome | Free My TV Fase 9 (Teste) |
| Pacote | io.freemytv.homep9test |
| Versão | 3.3-qa-phase9 |
| VersionCode | 9 |
| Instalação | Paralela à Fase 8 e ao launcher atual |
| TMDB | Configurar chave nesta instalação |
| Favoritos | Importar código manualmente de uma instalação com Fase 6 ou posterior |
| Assinatura | Provisória, se os Secrets de release não estiverem configurados |

## Correções concretas

**TMDB sem repetição infinita:** quando Filmes ou Séries falha por rede/API, um evento de rolagem não dispara novamente a mesma busca sem intervenção. A próxima tentativa só ocorre ao pressionar Tentar novamente, alterar filtros ou refazer pesquisa.

**Foco em Fixados:** se o usuário segurar OK para desafixar um canal dentro da aba Fixados, o guia seleciona o próximo canal disponível. Ao remover o último, o foco retorna ao filtro da categoria. O OK curto continua abrindo UniTVnet; o OK longo apenas na aba Agora na TV fixa/desafixa o canal.

**Regressão de módulos:** permanecem as cinco abas, a Home, o EPG compartilhado de seis fontes, os até 30 canais fixados, o backup e a organização dos apps, o catálogo TMDB e a cópia de títulos antes de abrir o UniTVnet.

## Testes automáticos de integração

A pipeline primeiro compila o APK e executa os testes estáticos das Fases 6, 7, 8 e 9. Há ainda um teste separado em **emulador Android TV** que tenta abrir a Home, confirmar menu superior e navegar até Agora na TV, Filmes, Séries e Apps pelo D-pad, guardando prints e logs. Só considerar teste de emulador aprovado se **esse job** concluir com sucesso.

O emulador não tem o UniTVnet instalado nem uma chave TMDB pessoal. Portanto, o comportamento real do UniTVnet, a grade EPG de cada afiliada e o desempenho da TCL só podem ser conferidos na TV.

## Homologação na TCL com Windows

O pacote de homologação contém:

- **tcl-diagnostico.ps1:** usa ADB para abrir apenas a Home de testes e reunir informações de foco/logcat. Não troca o launcher padrão ou desinstala aplicativos.
- **tcl-checklist-fase9.ps1:** executa um roteiro de **14 verificações manuais**, perguntando S (aprovado), N (falhou), P (pendente). Salva JSON e Markdown localmente. Não acessa a TV.

O procedimento recomendado é:

1. Manter o launcher atual instalado como padrão.
2. Instalar a Fase 9 em paralelo.
3. Configurar sua chave TMDB apenas na nova instalação.
4. Testar o controle, canais fixados, programas, películas, séries, apps, UniTVnet, falhas offline, backups e retorno após reiniciar.
5. Executar no PowerShell, na pasta dos scripts, o diagnóstico ADB opcional e o checklist:

    powershell -ExecutionPolicy Bypass -File .\tcl-diagnostico.ps1 -ApkPath ".\FreeMyTV-Fase-9.apk"

    powershell -ExecutionPolicy Bypass -File .\tcl-checklist-fase9.ps1

6. Revisar arquivos antes de compartilhar, porque logs podem citar outros aplicativos.

## Critérios para concluir a Etapa 9

- Nenhuma tela preta nem crash recorrente na Home.
- As cinco abas recebem foco e OK funciona no controle TCL.
- Pressão longa em Agora na TV fixa/desafixa sem abrir indevidamente UniTVnet.
- A faixa de até 30 canais aparece na Home e conserva a ordem.
- A programação exibida corresponde às fontes EPG disponíveis; não inventar horários.
- Filmes/Séries carregam páginas, permitem copiar título, lidam com falha de rede sem repetição automática.
- A restauração de canais e aplicativos só ocorre após confirmação do usuário.
- Retorno do UniTVnet e reinício da TCL não impedem o acesso ao launcher anterior.
- Os logs ADB não mostram erros fatais recorrentes.

## Etapa 10

A versão definitiva exige a homologação física, um identificador de pacote fixo, assinatura permanente protegida e testes de instalação/atualização. **Não há liberação de produção nesta Etapa 9.**
