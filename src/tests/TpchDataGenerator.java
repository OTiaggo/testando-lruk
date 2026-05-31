package tests;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

public class TpchDataGenerator {

	private static final int BATCH_SIZE = 500;
	private static final int REGION_COUNT = 5;
	private static final int NATION_COUNT = 25;
	private static final int SUPPLIER_COUNT = 100;
	private static final int PART_COUNT = 250;
	private static final int PARTSUPP_COUNT = 500;
	private static final int CUSTOMER_COUNT = 300;
	private static final int ORDER_COUNT = 500;
	private static final int LINEITEM_COUNT = 1000;

	private static final Random RANDOM = new Random(20260531L);

	public static void main(String[] args) throws IOException {
		File outputDir = new File(args.length > 0 ? args[0] : "SQL-Examples/tpch/generated/medium");
		if (!outputDir.exists() && !outputDir.mkdirs()) {
			throw new IOException("Could not create directory: " + outputDir.getAbsolutePath());
		}

		generateRegion(outputDir);
		generateNation(outputDir);
		generateSupplier(outputDir);
		generatePart(outputDir);
		generatePartSupp(outputDir);
		generateCustomer(outputDir);
		generateOrder(outputDir);
		generateLineItem(outputDir);

		System.out.println("TPC-H synthetic data generated at: " + outputDir.getAbsolutePath());
	}

	private static void generateRegion(File outputDir) throws IOException {
		String[] names = { "AFRICA", "AMERICA", "ASIA", "EUROPE", "MIDDLE EAST" };
		try (SqlBatchWriter writer = new SqlBatchWriter(outputDir, "region",
				"regionkey, r_name, r_comment")) {
			for (int i = 0; i < REGION_COUNT; i++) {
				writer.write(row(i, q(names[i]), q("Region " + names[i] + " benchmark comment")));
			}
		}
	}

	private static void generateNation(File outputDir) throws IOException {
		String[] names = { "ALGERIA", "ARGENTINA", "BRAZIL", "CANADA", "EGYPT", "ETHIOPIA", "FRANCE",
				"GERMANY", "INDIA", "INDONESIA", "IRAN", "IRAQ", "JAPAN", "JORDAN", "KENYA", "MOROCCO",
				"MOZAMBIQUE", "PERU", "CHINA", "ROMANIA", "SAUDI ARABIA", "VIETNAM", "RUSSIA",
				"UNITED KINGDOM", "UNITED STATES" };
		try (SqlBatchWriter writer = new SqlBatchWriter(outputDir, "nation",
				"nationkey, n_name, regionkey, n_comment")) {
			for (int i = 0; i < NATION_COUNT; i++) {
				writer.write(row(i, q(names[i]), i % REGION_COUNT, q("Nation " + names[i] + " benchmark comment")));
			}
		}
	}

	private static void generateSupplier(File outputDir) throws IOException {
		try (SqlBatchWriter writer = new SqlBatchWriter(outputDir, "supplier",
				"suppkey, s_name, s_address, nationkey, s_phone, s_acctbal, s_comment")) {
			for (int i = 1; i <= SUPPLIER_COUNT; i++) {
				writer.write(row(i, q(numbered("Supplier", i)), q(address(i)), i % NATION_COUNT, q(phone(i)),
						money(1000 + RANDOM.nextDouble() * 9000), q(comment("supplier", i))));
			}
		}
	}

	private static void generatePart(File outputDir) throws IOException {
		String[] containers = { "SM BOX", "LG BOX", "MED BAG", "JUMBO PKG", "WRAP CASE" };
		String[] types = { "STANDARD STEEL", "ANODIZED COPPER", "BRUSHED TIN", "POLISHED BRASS", "ECONOMY NICKEL" };
		try (SqlBatchWriter writer = new SqlBatchWriter(outputDir, "part",
				"partkey, p_name, p_mfgr, p_brand, p_type, p_size, p_container, p_retailprice, p_comment")) {
			for (int i = 1; i <= PART_COUNT; i++) {
				writer.write(row(i, q("part " + i + " " + word(i)), q("Manufacturer#" + ((i % 5) + 1)),
						q("Brand#" + ((i % 40) + 1)), q(types[i % types.length]), (i % 50) + 1,
						q(containers[i % containers.length]), money(900 + i * 0.11), q(comment("part", i))));
			}
		}
	}

	private static void generatePartSupp(File outputDir) throws IOException {
		try (SqlBatchWriter writer = new SqlBatchWriter(outputDir, "partsupp",
				"partkey, suppkey, ps_availqty, ps_supplycost, ps_comment")) {
			for (int i = 1; i <= PARTSUPP_COUNT; i++) {
				int partKey = ((i - 1) % PART_COUNT) + 1;
				int supplierKey = ((i - 1) % SUPPLIER_COUNT) + 1;
				writer.write(row(partKey, supplierKey, (i % 9999) + 1, money(10 + RANDOM.nextDouble() * 990),
						q(comment("partsupp", i))));
			}
		}
	}

	private static void generateCustomer(File outputDir) throws IOException {
		String[] segments = { "AUTOMOBILE", "BUILDING", "FURNITURE", "MACHINERY", "HOUSEHOLD" };
		try (SqlBatchWriter writer = new SqlBatchWriter(outputDir, "customer",
				"custkey, c_name, c_address, nationkey, c_phone, c_acctbal, c_mktsegment, c_comment")) {
			for (int i = 1; i <= CUSTOMER_COUNT; i++) {
				writer.write(row(i, q(numbered("Customer", i)), q(address(i + 10000)), i % NATION_COUNT,
						q(phone(i + 30000)), money(RANDOM.nextDouble() * 12000), q(segments[i % segments.length]),
						q(comment("customer", i))));
			}
		}
	}

	private static void generateOrder(File outputDir) throws IOException {
		String[] priorities = { "1-URGENT", "2-HIGH", "3-MEDIUM", "4-NOT SPECIFIED", "5-LOW" };
		try (SqlBatchWriter writer = new SqlBatchWriter(outputDir, "order",
				"orderkey, custkey, o_orderstatus, o_totalprice, o_orderdate, o_orderpriority, o_clerk, o_shippriority, o_comment")) {
			for (int i = 1; i <= ORDER_COUNT; i++) {
				int customerKey = ((i - 1) % CUSTOMER_COUNT) + 1;
				writer.write(row(i, customerKey, q(i % 3 == 0 ? "F" : "O"), money(500 + RANDOM.nextDouble() * 95000),
						q(date(1992 + (i % 7), (i % 12) + 1, (i % 28) + 1)), q(priorities[i % priorities.length]),
						q("Clerk#" + pad(i % 1000, 9)), i % 5, q(comment("order", i))));
			}
		}
	}

	private static void generateLineItem(File outputDir) throws IOException {
		String[] flags = { "A", "N", "R" };
		String[] status = { "F", "O" };
		String[] instructions = { "DELIVER IN PERSON", "COLLECT COD", "NONE", "TAKE BACK RETURN" };
		String[] modes = { "AIR", "FOB", "MAIL", "RAIL", "SHIP", "TRUCK" };
		try (SqlBatchWriter writer = new SqlBatchWriter(outputDir, "lineitem",
				"orderkey, partkey, suppkey, l_linenumber, l_quantity, l_extendedprice, l_discount, l_tax, l_returnflag, l_linestatus, l_shipdate, l_commitdate, l_receiptdate, l_shipinstruct, l_shipmode, l_comment")) {
			for (int i = 1; i <= LINEITEM_COUNT; i++) {
				int orderKey = ((i - 1) % ORDER_COUNT) + 1;
				int partKey = ((i - 1) % PART_COUNT) + 1;
				int supplierKey = ((i - 1) % SUPPLIER_COUNT) + 1;
				int month = (i % 12) + 1;
				int day = (i % 28) + 1;
				writer.write(row(orderKey, partKey, supplierKey, ((i - 1) % 7) + 1, (i % 50) + 1,
						money(100 + RANDOM.nextDouble() * 9000), money((i % 10) / 100.0),
						money((i % 8) / 100.0), q(flags[i % flags.length]), q(status[i % status.length]),
						q(date(1993 + (i % 5), month, day)), q(date(1993 + (i % 5), month, Math.min(day + 2, 28))),
						q(date(1993 + (i % 5), month, Math.min(day + 4, 28))),
						q(instructions[i % instructions.length]), q(modes[i % modes.length]), q(comment("lineitem", i))));
			}
		}
	}

	private static String row(Object... values) {
		StringBuilder builder = new StringBuilder("(");
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(values[i]);
		}
		builder.append(")");
		return builder.toString();
	}

	private static String q(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private static String money(double value) {
		return String.format(java.util.Locale.US, "%.2f", value);
	}

	private static String numbered(String prefix, int number) {
		return prefix + "#" + pad(number, 9);
	}

	private static String pad(int number, int size) {
		String value = String.valueOf(number);
		while (value.length() < size) {
			value = "0" + value;
		}
		return value;
	}

	private static String phone(int seed) {
		return String.format("%02d-%03d-%03d-%04d", seed % 90 + 10, seed % 900 + 100, seed % 800 + 100,
				seed % 9000 + 1000);
	}

	private static String address(int seed) {
		return "Street " + seed + " Block " + (seed % 97) + " Unit " + (seed % 31);
	}

	private static String date(int year, int month, int day) {
		return String.format("%04d-%02d-%02d", year, month, day);
	}

	private static String word(int seed) {
		String[] words = { "blue", "green", "white", "metal", "smooth", "bright", "dense", "clear" };
		return words[seed % words.length];
	}

	private static String comment(String table, int id) {
		return "Synthetic " + table + " row " + id + " for buffer benchmark locality";
	}

	private static class SqlBatchWriter implements AutoCloseable {
		private final PrintWriter writer;
		private final String table;
		private final String columns;
		private int batchCount = 0;
		private boolean open = false;

		SqlBatchWriter(File outputDir, String table, String columns) throws IOException {
			this.writer = new PrintWriter(new FileWriter(new File(outputDir, table + ".txt")));
			this.table = table;
			this.columns = columns;
		}

		void write(String row) {
			if (!open) {
				writer.println("INSERT INTO " + table + " (" + columns + ") VALUES ");
				open = true;
			} else {
				writer.println(",");
			}

			writer.print(row);
			batchCount++;

			if (batchCount == BATCH_SIZE) {
				writer.println(";");
				writer.println();
				batchCount = 0;
				open = false;
			}
		}

		@Override
		public void close() {
			if (open) {
				writer.println(";");
			}
			writer.close();
		}
	}
}
