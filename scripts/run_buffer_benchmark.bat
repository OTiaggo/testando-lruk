@echo off
setlocal enabledelayedexpansion

set CLASSPATH=bin;libs/*;src;.
set GENERATED_DIR=SQL-Examples\tpch\generated\small
set QUERY_FILE=SQL-Examples\tpch\queries\buffer-benchmark.sql
set RESULTS=resultados_execucao.csv
set BACKUP_DIR=database_benchmark_backup_small
set IMPORT_BUFFER=50000
set REPETITIONS=1
set KERNEL_PORT=3101
set POLICIES=LRU LRUK MRU FIFO
set SIZES=32 64 128 256

echo =====================================================
echo [COMPILANDO TESTES]
echo =====================================================

if not exist bin mkdir bin
dir /s /b src\*.java > "%TEMP%\sealdb_sources.txt"
javac -cp "libs/*;src" -d bin @"%TEMP%\sealdb_sources.txt"
if errorlevel 1 (
    echo [ERRO] Falha na compilacao.
    exit /b 1
)

echo.
echo =====================================================
echo [GERANDO DADOS TPC-H SINTETICOS]
echo =====================================================

java -cp "%CLASSPATH%" tests.TpchDataGenerator "%GENERATED_DIR%"
if errorlevel 1 (
    echo [ERRO] Falha ao gerar dados.
    exit /b 1
)

if exist "%BACKUP_DIR%" (
    echo.
    echo [BACKUP] "%BACKUP_DIR%" ja existe. Pulando importacao para evitar duplicar dados.
    echo [BACKUP] Apague "%BACKUP_DIR%" se quiser recriar a base benchmark do zero.
) else (
    echo.
    echo =====================================================
    echo [IMPORTANDO DADOS UMA VEZ]
    echo =====================================================

    java -cp "%CLASSPATH%" tests.TpchDataImporter "%GENERATED_DIR%" tpch %IMPORT_BUFFER% %KERNEL_PORT%
    if errorlevel 1 (
        echo [ERRO] Falha ao importar dados.
        exit /b 1
    )

    echo.
    echo [BACKUP] Criando "%BACKUP_DIR%" a partir de database...
    xcopy "database" "%BACKUP_DIR%" /e /i /q /y > nul
    if errorlevel 1 (
        echo [ERRO] Falha ao criar backup da base benchmark.
        exit /b 1
    )
)

if exist "%RESULTS%" del "%RESULTS%"

echo.
echo =====================================================
echo [INICIANDO BATERIA DE TESTES COM ISOLAMENTO DE ESTADO]
echo =====================================================

for %%S in (%SIZES%) do (
    echo.
    echo ----- TESTANDO COM BUFFER SIZE: %%S -----
    echo.

    for %%P in (%POLICIES%) do (
        echo [RESET] Restaurando database a partir de "%BACKUP_DIR%"...
        if exist "database" rmdir /s /q "database"
        xcopy "%BACKUP_DIR%" "database" /e /i /q /y > nul
        if errorlevel 1 (
            echo [ERRO] Falha ao restaurar database.
            exit /b 1
        )

        echo [EXECUTANDO] Politica: %%P ^| Buffer: %%S
        java -cp "%CLASSPATH%" tests.BufferBenchmark %%P %%S "%QUERY_FILE%" %REPETITIONS% "%RESULTS%" %KERNEL_PORT%
        if errorlevel 1 (
            echo [ERRO] Falha em %%P com tamanho %%S.
            exit /b 1
        )

        timeout /t 3 /nobreak > nul
        echo -----------------------------------------------------
    )
)

echo =====================================================
echo [EXPERIMENTO CONCLUIDO]
echo Resultados em: %RESULTS%
echo =====================================================
