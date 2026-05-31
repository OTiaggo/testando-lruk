package tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import DBMS.Kernel;
import DBMS.connectionManager.DBConnection;
import DBMS.fileManager.ISchema;
import DBMS.queryProcessing.ITable;
import DBMS.queryProcessing.TableManipulate;
import DBMS.queryProcessing.parse.Parse;
import DBMS.queryProcessing.queryEngine.Plan;
import DBMS.transactionManager.ITransaction;
import DBMS.transactionManager.TransactionRunnable;

public class TpchDataImporter {

	private static final String[] TABLE_ORDER = { "region", "nation", "supplier", "part", "partsupp", "customer",
			"order", "lineitem" };

	public static void main(String[] args) throws Exception {
		String insertsDir = args.length > 0 ? args[0] : "SQL-Examples/tpch/generated/medium";
		String schemaName = args.length > 1 ? args[1] : "tpch";
		int bufferSize = args.length > 2 ? Integer.parseInt(args[2]) : 50000;
		int port = args.length > 3 ? Integer.parseInt(args[3]) : 3101;

		Kernel.BUFFER_SIZE = bufferSize;
		Kernel.PORT = port;
		Kernel.start();

		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean success = new AtomicBoolean(true);
		AtomicReference<Exception> failure = new AtomicReference<>();

		DBConnection connection = Kernel.getTransactionManager().getLocalConnection(schemaName, "admin", "admin");
		ITransaction transaction = Kernel.getExecuteTransactions().begin(connection);

		transaction.execRunnable(new TransactionRunnable() {
			@Override
			public void run(ITransaction transaction) {
				try {
					ISchema schema = Kernel.getCatalog().getSchemabyName(schemaName);
					if (schema == null) {
						throw new IllegalStateException("Schema not found: " + schemaName);
					}

					int totalCommands = 0;
					for (String table : TABLE_ORDER) {
						File file = new File(insertsDir, table + ".txt");
						if (!file.exists()) {
							throw new IllegalStateException("Insert file not found: " + file.getAbsolutePath());
						}
						int commands = executeFile(file, schema, transaction);
						totalCommands += commands;
						System.out.println("[IMPORT] " + table + ": " + commands + " insert command(s)");
					}
					System.out.println("[IMPORT] Finished. Total commands: " + totalCommands);
				} catch (Exception e) {
					success.set(false);
					failure.set(e);
				} finally {
					latch.countDown();
				}
			}

			@Override
			public void onFail(ITransaction transaction, Exception e) {
				success.set(false);
				failure.set(e);
				transaction.abort();
				latch.countDown();
			}
		});

		latch.await();
		Thread.sleep(1000);
		Kernel.stop();

		if (!success.get()) {
			Exception e = failure.get();
			if (e != null) {
				e.printStackTrace();
			}
			System.exit(1);
		}
		System.exit(0);
	}

	private static int executeFile(File file, ISchema schema, ITransaction transaction) throws Exception {
		Parse parser = Parse.getNewInstance();
		StringBuilder sqlBuilder = new StringBuilder();
		int commandCount = 0;

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sqlBuilder.append(line).append("\n");
				if (line.trim().endsWith(";")) {
					String sql = sqlBuilder.toString();
					commandCount += executeSql(parser, sql, schema, transaction);
					sqlBuilder.setLength(0);
				}
			}
		}

		if (sqlBuilder.toString().trim().length() > 0) {
			commandCount += executeSql(parser, sqlBuilder.toString(), schema, transaction);
		}

		return commandCount;
	}

	private static int executeSql(Parse parser, String sql, ISchema schema, ITransaction transaction) throws SQLException {
		if (isOrderInsert(sql)) {
			return executeOrderInsert(sql, schema, transaction);
		}

		List<Plan> plans = parser.parse(sql, schema);
		if (plans == null) {
			return 0;
		}
		for (Plan plan : plans) {
			plan.setTransaction(transaction);
			plan.execute();
		}
		return plans.size();
	}

	private static boolean isOrderInsert(String sql) {
		return sql.trim().toLowerCase().startsWith("insert into order ");
	}

	private static int executeOrderInsert(String sql, ISchema schema, ITransaction transaction) throws SQLException {
		ITable table = schema.getTableByName("order");
		if (table == null) {
			throw new SQLException("order not in the schema " + schema.getName());
		}

		int firstColumnOpen = sql.indexOf('(');
		int firstColumnClose = sql.indexOf(')', firstColumnOpen);
		int valuesIndex = sql.toLowerCase().indexOf("values", firstColumnClose);
		if (firstColumnOpen < 0 || firstColumnClose < 0 || valuesIndex < 0) {
			throw new SQLException("Invalid INSERT command for table order");
		}

		String[] columns = splitCsv(sql.substring(firstColumnOpen + 1, firstColumnClose));
		String valuesText = sql.substring(valuesIndex + "values".length()).trim();
		if (valuesText.endsWith(";")) {
			valuesText = valuesText.substring(0, valuesText.length() - 1);
		}

		int rowCount = 0;
		for (String row : extractRows(valuesText)) {
			String[] values = splitCsv(row);
			if (values.length != columns.length) {
				throw new SQLException("Invalid number of values for table order");
			}

			String[] data = new String[table.getColumnNames().length];
			for (int i = 0; i < values.length; i++) {
				String column = columns[i].trim();
				int index = table.getIdColumn(column);
				if (index == -1) {
					throw new SQLException("column " + column + " not in table order");
				}
				data[index] = values[i].trim();
			}
			table.writeTuple(transaction, TableManipulate.arrayToString(data));
			rowCount++;
		}
		return 1;
	}

	private static List<String> extractRows(String valuesText) throws SQLException {
		List<String> rows = new ArrayList<>();
		boolean inQuote = false;
		int depth = 0;
		int rowStart = -1;

		for (int i = 0; i < valuesText.length(); i++) {
			char c = valuesText.charAt(i);
			if (c == '\'') {
				if (inQuote && i + 1 < valuesText.length() && valuesText.charAt(i + 1) == '\'') {
					i++;
				} else {
					inQuote = !inQuote;
				}
			}

			if (inQuote) {
				continue;
			}

			if (c == '(') {
				if (depth == 0) {
					rowStart = i + 1;
				}
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0 && rowStart >= 0) {
					rows.add(valuesText.substring(rowStart, i));
					rowStart = -1;
				} else if (depth < 0) {
					throw new SQLException("Invalid row list in INSERT command for table order");
				}
			}
		}

		if (depth != 0 || inQuote) {
			throw new SQLException("Unclosed row or quote in INSERT command for table order");
		}
		return rows;
	}

	private static String[] splitCsv(String value) {
		List<String> parts = new ArrayList<>();
		boolean inQuote = false;
		int start = 0;

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\'') {
				if (inQuote && i + 1 < value.length() && value.charAt(i + 1) == '\'') {
					i++;
				} else {
					inQuote = !inQuote;
				}
			} else if (c == ',' && !inQuote) {
				parts.add(value.substring(start, i).trim());
				start = i + 1;
			}
		}

		parts.add(value.substring(start).trim());
		return parts.toArray(new String[parts.size()]);
	}
}
