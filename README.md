# Trabalho de Buffer em SGBD - LRUK

Este repositorio contem a entrega do trabalho descrito em `ListaExercicios2-Buffer-SGBD_Completo.pdf`.

O projeto base usado foi o SEAL-DB, um prototipo academico de SGBD em Java. Nesta entrega, o foco nao esta na interface grafica do SEAL-DB, mas sim na implementacao, execucao e analise de uma politica de substituicao de paginas no buffer.

A politica escolhida para o trabalho foi o algoritmo **LRUK com K = 2**.

## Objetivo da Entrega

O objetivo do trabalho foi:

- implementar/adicionar a politica **LRUK** ao gerenciador de buffer;
- gerar uma base de dados maior para permitir testes mais representativos;
- automatizar a execucao de consultas SQL;
- coletar metricas relacionadas ao buffer;
- comparar o comportamento de **LRU**, **LRUK**, **MRU** e **FIFO**;
- analisar os resultados usando tabelas e graficos.

## Algoritmo LRUK Implementado

O algoritmo **LRUK** e uma evolucao do LRU tradicional.

O LRU comum considera apenas o ultimo acesso de cada pagina. Ja o LRUK considera o historico de acessos. Nesta entrega foi usado **K = 2**, ou seja, cada pagina guarda informacoes sobre os dois acessos mais recentes.

Na implementacao, cada pagina possui:

- `t1`: instante do acesso mais recente;
- `t2`: instante do segundo acesso mais recente.

Quando uma pagina e acessada novamente, os tempos sao atualizados:

- o antigo `t1` passa a ser `t2`;
- o novo instante de acesso passa a ser `t1`.

Quando o buffer esta cheio e uma pagina precisa ser removida, o LRUK escolhe a pagina com menor valor de `t2`. Dessa forma, paginas que foram acessadas apenas uma vez tendem a ser removidas antes de paginas que ja demonstraram recorrencia de uso.

Em caso de empate no `t2`, a implementacao usa `t1` como criterio de desempate, removendo a pagina com acesso mais antigo.

A classe do algoritmo esta em:

```text
src/DBMS/bufferManager/policies/LRUK.java
```

O LRUK tambem foi registrado no `Kernel`, permitindo que ele seja selecionado pelo nome:

```text
LRUK
```

As politicas comparadas nos experimentos sao:

```text
LRU
LRUK
MRU
FIFO
```

## Geracao de Dados para Teste

Para que os testes nao fossem executados apenas sobre uma base muito pequena, foi criado um gerador de dados sinteticos no formato TPC-H.

Arquivo principal:

```text
src/tests/TpchDataGenerator.java
```

Esse gerador cria dados para as tabelas:

- `region`
- `nation`
- `supplier`
- `part`
- `partsupp`
- `customer`
- `order`
- `lineitem`

Os dados gerados preservam relacionamentos importantes entre tabelas. Por exemplo:

- `customer.nationkey` aponta para `nation`;
- `supplier.nationkey` aponta para `nation`;
- `nation.regionkey` aponta para `region`;
- `partsupp.partkey` aponta para `part`;
- `lineitem.orderkey`, `lineitem.partkey` e `lineitem.suppkey` apontam para registros existentes.

Isso permite que as consultas com joins tenham resultados coerentes.

Os arquivos de dados gerados ficam em:

```text
SQL-Examples/tpch/generated/small
```

Essa pasta e ignorada pelo Git, pois os dados podem ser recriados automaticamente pelo script.

## Importacao dos Dados

A importacao dos dados gerados e feita por:

```text
src/tests/TpchDataImporter.java
```

Esse programa le os arquivos `.txt` gerados e executa os comandos de insercao no schema `tpch`.

A ordem de importacao foi definida para respeitar as dependencias entre tabelas:

```text
region
nation
supplier
part
partsupp
customer
order
lineitem
```

Tambem foi necessario tratar a tabela `order` de forma especial. Como `order` e uma palavra reservada em SQL, o parser usado pelo projeto pode gerar erro ao processar:

```sql
INSERT INTO order (...)
```

Para resolver isso, o importador identifica inserts da tabela `order` e grava os registros diretamente usando a API interna da tabela, sem passar esse comando pelo parser SQL.

## Benchmark Automatizado

A execucao automatizada dos testes foi implementada em:

```text
src/tests/BufferBenchmark.java
```

Esse programa recebe como argumentos:

- politica de buffer;
- tamanho do buffer;
- arquivo de queries;
- numero de repeticoes;
- arquivo CSV de saida;
- porta usada pelo Kernel.

Para cada query executada, ele coleta:

- tempo de execucao;
- quantidade de linhas retornadas;
- quantidade de hits no buffer;
- quantidade de misses no buffer;
- total de operacoes no buffer;
- taxa de acerto;
- quantidade de paginas no buffer;
- validacao das estatisticas.

A validacao usada foi:

```text
hit_count + miss_count == operation_count
```

## Workload de Consultas

As consultas usadas no experimento estao em:

```text
SQL-Examples/tpch/queries/buffer-benchmark.sql
```

Esse arquivo contem consultas com:

- scans simples;
- filtros;
- joins;
- repeticao de queries.

A repeticao de algumas consultas foi intencional, pois ajuda a observar se a politica de buffer consegue reaproveitar paginas ja carregadas.

## Script Principal de Execucao

O script principal da entrega e:

```text
scripts/run_buffer_benchmark.bat
```

Ele automatiza todo o processo:

1. compila os arquivos Java;
2. gera os dados sinteticos;
3. importa os dados para o banco `tpch`;
4. cria um backup local da base carregada;
5. executa os testes para cada politica e tamanho de buffer;
6. grava os resultados em CSV.

As politicas testadas sao:

```text
LRU LRUK MRU FIFO
```

Os tamanhos de buffer usados sao:

```text
32 64 128 256
```

## Como Executar

Execute a partir da raiz do projeto.

No PowerShell:

```powershell
.\scripts\run_buffer_benchmark.bat
```

No Prompt de Comando:

```bat
scripts\run_buffer_benchmark.bat
```

Ao final da execucao, os resultados ficam em:

```text
resultados_execucao.csv
```

## Arquivo de Resultados

O arquivo `resultados_execucao.csv` contem uma linha para cada combinacao de:

- politica;
- tamanho de buffer;
- repeticao;
- query executada.

As colunas principais sao:

- `policy`: politica usada;
- `buffer_size`: tamanho do buffer em paginas;
- `query`: query executada;
- `elapsed_ms`: tempo em milissegundos;
- `result_rows`: linhas retornadas;
- `hit_count`: acertos no buffer;
- `miss_count`: faltas no buffer;
- `operation_count`: total de operacoes;
- `hit_ratio_percent`: percentual de acerto;
- `pages_in_buffer`: paginas no buffer ao final da query;
- `valid_stats`: indica se as metricas estao consistentes.

## Analise dos Resultados

A analise foi feita no notebook:

```text
analises.ipynb
```

O notebook le `resultados_execucao.csv` e gera:

- tabelas resumo;
- grafico de tempo total por politica;
- grafico de hit ratio ponderado;
- grafico de misses totais;
- heatmaps por query;
- ranking geral das politicas.

Esses graficos sao usados para comparar o comportamento do **LRUK** em relacao a **LRU**, **MRU** e **FIFO**.

## Pasta de Entregaveis da Tarefa

A pasta abaixo foi organizada para facilitar a avaliacao do trabalho:

```text
entregaveis_da_tarefa/
```

Ela contem os principais arquivos da entrega:

- `entregaveis_da_tarefa/LRUK.java`
  - codigo da politica **LRUK com K = 2** implementada para o gerenciador de buffer;
  - e o arquivo principal para verificar a logica do algoritmo escolhido.

- `entregaveis_da_tarefa/analises.ipynb`
  - notebook com as analises dos resultados obtidos;
  - contem tabelas e graficos comparando `LRU`, `LRUK`, `MRU` e `FIFO`.

Essa pasta serve como um resumo pratico dos entregaveis principais: o codigo do algoritmo implementado e a analise experimental dos resultados.

## Observacao sobre Arquivos Grandes

Durante a execucao, o SGBD gera arquivos internos de banco, logs e backups que podem ficar muito grandes.

Por isso, o `.gitignore` foi ajustado para ignorar:

```text
/database/
/database_benchmark_backup*/
/SQL-Examples/tpch/generated/
```

Assim, o repositorio enviado contem o codigo, os scripts, as queries, o CSV de resultados e o notebook de analise, mas nao inclui arquivos grandes gerados em tempo de execucao.

## Estrutura Principal da Entrega

```text
src/DBMS/bufferManager/policies/LRUK.java
src/tests/TpchDataGenerator.java
src/tests/TpchDataImporter.java
src/tests/BufferBenchmark.java
SQL-Examples/tpch/queries/buffer-benchmark.sql
scripts/run_buffer_benchmark.bat
resultados_execucao.csv
analises.ipynb
entregaveis_da_tarefa/LRUK.java
entregaveis_da_tarefa/analises.ipynb
```

## Resumo

Esta entrega adiciona ao projeto uma politica de substituicao de buffer baseada em **LRUK com K = 2**, automatiza a preparacao de dados, executa testes comparativos contra **LRU**, **MRU** e **FIFO**, coleta metricas de buffer e disponibiliza uma analise grafica dos resultados.
