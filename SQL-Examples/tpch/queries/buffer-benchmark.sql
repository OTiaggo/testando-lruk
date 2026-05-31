-- Workload for buffer replacement policy comparison.
-- BufferBenchmark strips comment lines and executes each statement separately.

select * from region;

select * from nation;

select n_name, r_name
from nation n, region r
where n.regionkey = r.regionkey;

select s_name, s_phone
from supplier
where nationkey = 1;

select c_name, c_phone, c_mktsegment
from customer
where nationkey = 1;

select orderkey, partkey, suppkey, l_quantity, l_shipmode
from lineitem
where l_quantity >= 45;

select orderkey, partkey, suppkey, l_quantity, l_shipmode
from lineitem
where l_quantity >= 45;

select s_name, n_name
from supplier s, nation n
where s.nationkey = n.nationkey;

select s_name, n_name
from supplier s, nation n
where s.nationkey = n.nationkey;
