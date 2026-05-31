package tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import DBMS.Kernel;
import DBMS.bufferManager.policies.AbstractBufferPolicy;
import DBMS.connectionManager.DBConnection;
import DBMS.fileManager.ISchema;
import DBMS.queryProcessing.ITable;
import DBMS.queryProcessing.ITuple;
import DBMS.queryProcessing.parse.Parse;
import DBMS.queryProcessing.queryEngine.Plan;
import DBMS.queryProcessing.queryEngine.InteratorsAlgorithms.TableScan;
import DBMS.transactionManager.ITransaction;
import DBMS.transactionManager.TransactionRunnable;

public class BufferBenchmark {

	private static final String DEFAULT_QUERY_FILE = "SQL-Examples/tpch/queries/buffer-benchmark.sql";
	private static final String DEFAULT_OUTPUT = "resultados_execucao.csv";

	public static void main(String[] args) throws Exception {
		String policy = args.length > 0 ? args[0] : "LRUK";
		int bufferSize = args.length > 1 ? Integer.parseInt(args[1]) : 128;
		String queryFile = args.length > 2 ? args[2] : DEFAULT_QUERY_FILE;
		int repetitions = args.length > 3 ? Integer.parseInt(args[3]) : 1;
		String outputCsv = args.length > 4 ? args[4] : DEFAULT_OUTPUT;
		int port = args.length > 5 ? Integer.parseInt(args[5]) : 3101;

		Kernel.BUFFER_SIZE = bufferSize;
		Kernel.PORT = port;
		Kernel.setBufferPolicy(policy);
		Kernel.start();

		System.out.println("[BENCH] Available policies: " + Kernel.getBufferPoliciesListNames());
		System.out.println("[BENCH] Policy=" + policy + " BufferSize=" + bufferSize + " QueryFile=" + queryFile);

		List<QueryCase> queries = readQueries(new File(queryFile));
		ensureHeader(new File(outputCsv));

		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<Exception> failure = new AtomicReference<>();

		DBConnection connection = Kernel.getTransactionManager().getLocalConnection("tpch", "admin", "admin");
		ITransaction transaction = Kernel.getExecuteTransactions().begin(connection);

		transaction.execRunnable(new TransactionRunnable() {
			@Override
			public void run(ITransaction transaction) {
				try {
					ISchema schema = Kernel.getCatalog().getSchemabyName("tpch");
					if (schema == null) {
						throw new IllegalStateException("Schema not found: tpch");
					}

					for (int repetition = 1; repetition <= repetitions; repetition++) {
						for (QueryCase query : queries) {
							Measurement measurement = executeQuery(policy, bufferSize, repetition, query, schema, transaction);
							appendMeasurement(outputCsv, measurement);
							System.out.println("[BENCH] " + measurement.queryName + " rep=" + repetition + " rows="
									+ measurement.resultRows + " hits=" + measurement.hits + " misses=" + measurement.misses
									+ " ops=" + measurement.operations + " timeMs=" + measurement.elapsedMs);
						}
					}
				} catch (Exception e) {
					failure.set(e);
				} finally {
					latch.countDown();
				}
			}

			@Override
			public void onFail(ITransaction transaction, Exception e) {
				failure.set(e);
				transaction.abort();
				latch.countDown();
			}
		});

		latch.await();
		Thread.sleep(1000);
		Kernel.stop();

		if (failure.get() != null) {
			failure.get().printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}

	private static Measurement executeQuery(String policy, int bufferSize, int repetition, QueryCase query, ISchema schema,
			ITransaction transaction) throws SQLException {
		Kernel.getBufferManager().resetStatistics();
		long start = System.nanoTime();

		Plan plan = Parse.getNewInstance().parseSQL(query.sql, schema);
		plan.setTransaction(transaction);
		ITable result = plan.execute();
		int rows = consume(result, transaction);

		long elapsedMs = (System.nanoTime() - start) / 1000000L;
		AbstractBufferPolicy bufferPolicy = Kernel.getBufferManager().getBufferPolicy();
		long hits = bufferPolicy.getHitCount();
		long misses = bufferPolicy.getMissCount();
		long operations = bufferPolicy.getNumberOfOperation();
		int pagesInBuffer = bufferPolicy.getCurrentNumberOfPages();
		double hitRatio = operations == 0 ? 0.0 : (hits * 100.0) / operations;
		boolean validStats = hits + misses == operations;

		return new Measurement(policy, bufferSize, repetition, query.name, elapsedMs, rows, hits, misses, operations,
				hitRatio, pagesInBuffer, validStats, "");
	}

	private static int consume(ITable result, ITransaction transaction) {
		if (result == null) {
			return 0;
		}
		int count = 0;
		TableScan scan = new TableScan(transaction, result);
		ITuple tuple = scan.nextTuple();
		while (tuple != null) {
			count++;
			tuple = scan.nextTuple();
		}
		return count;
	}

	private static List<QueryCase> readQueries(File file) throws IOException {
		if (!file.exists()) {
			throw new IOException("Query file not found: " + file.getAbsolutePath());
		}

		StringBuilder builder = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.startsWith("--") || trimmed.startsWith("#") || trimmed.length() == 0) {
					continue;
				}
				builder.append(line).append("\n");
			}
		}

		List<QueryCase> queries = new ArrayList<>();
		String[] statements = builder.toString().split(";");
		for (String statement : statements) {
			String sql = statement.trim();
			if (sql.length() > 0) {
				queries.add(new QueryCase(String.format("q%02d", queries.size() + 1), sql + ";"));
			}
		}
		return queries;
	}

	private static void ensureHeader(File outputCsv) throws IOException {
		if (outputCsv.exists() && outputCsv.length() > 0) {
			return;
		}
		File parent = outputCsv.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		try (PrintWriter writer = new PrintWriter(new FileWriter(outputCsv, false))) {
			writer.println(
					"policy,buffer_size,repetition,query,elapsed_ms,result_rows,hit_count,miss_count,operation_count,hit_ratio_percent,pages_in_buffer,valid_stats,error");
		}
	}

	private static synchronized void appendMeasurement(String outputCsv, Measurement measurement) throws IOException {
		try (PrintWriter writer = new PrintWriter(new FileWriter(outputCsv, true))) {
			writer.println(measurement.toCsv());
		}
	}

	private static String csv(String value) {
		if (value == null) {
			return "";
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static class QueryCase {
		private final String name;
		private final String sql;

		QueryCase(String name, String sql) {
			this.name = name;
			this.sql = sql;
		}
	}

	private static class Measurement {
		private final String policy;
		private final int bufferSize;
		private final int repetition;
		private final String queryName;
		private final long elapsedMs;
		private final int resultRows;
		private final long hits;
		private final long misses;
		private final long operations;
		private final double hitRatio;
		private final int pagesInBuffer;
		private final boolean validStats;
		private final String error;

		Measurement(String policy, int bufferSize, int repetition, String queryName, long elapsedMs, int resultRows,
				long hits, long misses, long operations, double hitRatio, int pagesInBuffer, boolean validStats,
				String error) {
			this.policy = policy;
			this.bufferSize = bufferSize;
			this.repetition = repetition;
			this.queryName = queryName;
			this.elapsedMs = elapsedMs;
			this.resultRows = resultRows;
			this.hits = hits;
			this.misses = misses;
			this.operations = operations;
			this.hitRatio = hitRatio;
			this.pagesInBuffer = pagesInBuffer;
			this.validStats = validStats;
			this.error = error;
		}

		String toCsv() {
			return csv(policy) + "," + bufferSize + "," + repetition + "," + csv(queryName) + "," + elapsedMs + ","
					+ resultRows + "," + hits + "," + misses + "," + operations + ","
					+ String.format(Locale.US, "%.4f", hitRatio) + "," + pagesInBuffer + "," + validStats + ","
					+ csv(error);
		}
	}
}
