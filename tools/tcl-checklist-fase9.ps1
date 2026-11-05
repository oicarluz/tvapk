#Requires -Version 5.1
<#
.SYNOPSIS
  Registra a homologacao REAL do Free My TV Fase 9 na TCL, sem alterar a TV.
.DESCRIPTION
  O proprio usuario executa os testes com o controle remoto e registra
  sucesso, falha ou pendencia. Este script NAO acessa ADB, NAO coleta dados
  e NAO instala ou desinstala aplicativos. Salva um relatorio local em JSON/MD.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\tcl-checklist-fase9.ps1
#>
[CmdletBinding()]
param([string]$OutputDirectory=".")
Set-StrictMode -Version Latest
$ErrorActionPreference="Stop"

$cases=@(
    [PSCustomObject]@{Id=1;Area="Home";Instruction="Abrir Free My TV Fase 9 (Teste) na TCL. A Home deve mostrar o menu superior, banner e faixas, sem tela preta."},
    [PSCustomObject]@{Id=2;Area="Controle";Instruction="Mover as setas para navegar no menu Início/Agora na TV/Filmes/Séries/Apps e confirmar foco visível, OK e Voltar."},
    [PSCustomObject]@{Id=3;Area="Agora na TV";Instruction="Verificar categorias e rolagem de canais. A programacao deve mostrar dados ou aviso de indisponibilidade, sem congelar."},
    [PSCustomObject]@{Id=4;Area="OK curto";Instruction="Na aba Agora na TV, clicar OK rapidamente em um canal: UniTVnet deve abrir, sem sintonia automatica prometida."},
    [PSCustomObject]@{Id=5;Area="OK longo";Instruction="Na aba Agora na TV, segurar OK: canal e fixado e depois desafixado. Nao deve abrir UniTVnet por engano."},
    [PSCustomObject]@{Id=6;Area="Home canais";Instruction="Fixar 2 ou mais canais em Agora na TV; voltar a Inicio e confirmar exibicao, ordem, EPG e rolagem horizontal."},
    [PSCustomObject]@{Id=7;Area="Favoritos";Instruction="Abrir Organizar, reordenar canais e apps, fechar e reabrir. Conferir ordem persistente e foco sem saltos."},
    [PSCustomObject]@{Id=8;Area="Filmes";Instruction="Com chave TMDB configurada, selecionar genero, buscar um filme e descer ate uma nova pagina. OK copia titulo e abre UniTVnet."},
    [PSCustomObject]@{Id=9;Area="Series";Instruction="Na aba Series, testar filtros proprios, busca, paginacao e copia do titulo para UniTVnet."},
    [PSCustomObject]@{Id=10;Area="Falha TMDB";Instruction="Temporariamente sem internet, abrir Filmes e Series: nao deve ocorrer loop infinito de tentativas. Botao Tentar novamente deve funcionar apos reconectar."},
    [PSCustomObject]@{Id=11;Area="Rede/EPG";Instruction="Com internet desligada, abrir a Home e guia; conferir estado de indisponibilidade, sem encerrar o launcher."},
    [PSCustomObject]@{Id=12;Area="Retorno";Instruction="Abrir e sair do UniTVnet varias vezes. A Home deve voltar rapidamente e sem ficar preta."},
    [PSCustomObject]@{Id=13;Area="Backup";Instruction="Copiar favoritos e restaurar o codigo manual, confirmando a solicitacao antes de alterar canais/apps."},
    [PSCustomObject]@{Id=14;Area="Reinicio";Instruction="Reiniciar a TCL. O launcher anterior deve permanecer instalado e funcionando; favoritos da Fase 9 devem persistir."}
)

Write-Host ""
Write-Host "Free My TV Fase 9 - Homologacao na TCL" -ForegroundColor Cyan
Write-Host "Este formulario nao modifica nada na TV."
Write-Host "Responda S (passou), N (falhou), P (pendente/nao testado)."
Write-Host ""
$results=@()
foreach($case in $cases){
    Write-Host ("Teste {0}/14 - {1}" -f $case.Id,$case.Area) -ForegroundColor Cyan
    Write-Host $case.Instruction
    $response=""
    do {
        $response=(Read-Host "Resultado [S/N/P]").Trim().ToUpperInvariant()
    } while($response -notin @("S","N","P"))
    $notes=""
    if($response -ne "S"){
        $notes=Read-Host "Detalhes (opcional, sem senhas ou informacoes privadas)"
    }
    $results+= [PSCustomObject]@{
        Numero=$case.Id
        Area=$case.Area
        Resultado=$response
        Instrucao=$case.Instruction
        Observacoes=$notes
    }
}
if(-not (Test-Path -LiteralPath $OutputDirectory)){
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
}
$out=(Resolve-Path -LiteralPath $OutputDirectory).Path
$stamp=Get-Date -Format "yyyyMMdd-HHmmss"
$jsonPath=Join-Path $out "free-my-tv-fase9-homologacao-$stamp.json"
$mdPath=Join-Path $out "free-my-tv-fase9-homologacao-$stamp.md"
$document=[PSCustomObject]@{
    Data=(Get-Date).ToString("o")
    Versao="3.3-qa-phase9"
    Pacote="io.freemytv.homep9test"
    Total=$results.Count
    Passou=@($results | Where-Object Resultado -eq "S").Count
    Falhou=@($results | Where-Object Resultado -eq "N").Count
    Pendente=@($results | Where-Object Resultado -eq "P").Count
    Testes=$results
}
$document | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
$lines=@("# Free My TV Fase 9 - Homologacao","",
    "Data: $($document.Data)","","Pacote: $($document.Pacote)","",
    "Aprovados: $($document.Passou) / $($document.Total)",
    "Falhas: $($document.Falhou)",
    "Pendentes: $($document.Pendente)","")
foreach($item in $results){
    $status=@{S="APROVADO";N="FALHOU";P="PENDENTE"}[$item.Resultado]
    $lines+=("## {0}. {1} [{2}]" -f $item.Numero,$item.Area,$status)
    $lines+=$item.Instrucao
    if($item.Observacoes){$lines+=("Observacoes: "+$item.Observacoes)}
    $lines+=""
}
$lines | Set-Content -LiteralPath $mdPath -Encoding UTF8
Write-Host ""
Write-Host "Relatorios locais: " -ForegroundColor Cyan
Write-Host $mdPath
Write-Host $jsonPath
if($document.Falhou -gt 0 -or $document.Pendente -gt 0){
    Write-Host "A Fase 9 ainda nao pode ser considerada homologada na TCL." -ForegroundColor Yellow
}else{
    Write-Host "Checklist manual completo. Falta analisar logs/estabilidade antes de liberar para producao." -ForegroundColor Green
}
