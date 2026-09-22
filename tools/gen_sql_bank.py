import json, sqlite3, random

BANK=[]
def add(slug,title,diff,rating,pattern,schema,seed_sql,task,ref,ordered=False):
    con=sqlite3.connect(":memory:")
    for s in schema: con.execute(s)
    for s in seed_sql: con.execute(s)
    con.commit()
    cur=con.execute(ref)
    cols=[d[0] for d in cur.description]
    rows=[list(r) for r in cur.fetchall()]
    assert rows, f"{slug}: reference returned 0 rows - bad generator"
    assert len(rows)<len(list(con.execute("select * from "+schema[0].split()[2].split('(')[0]).fetchall()))+999
    BANK.append({"slug":slug,"title":title,"difficulty":diff,"rating":rating,"pattern":pattern,
                 "schema":schema,"seedData":seed_sql,"task":task,"referenceQuery":ref,
                 "ordered":ordered,"expected":{"columns":cols,"rows":rows}})
    con.close()

EMP_SCHEMA=["CREATE TABLE departments (id INTEGER PRIMARY KEY, name TEXT)",
            "CREATE TABLE employees (id INTEGER PRIMARY KEY, name TEXT, dept_id INTEGER, salary INTEGER, hired TEXT)"]
EMP_DATA=["INSERT INTO departments VALUES (10,'Engineering'),(20,'Sales'),(30,'Support')",
 """INSERT INTO employees VALUES
 (1,'Arun',10,45000,'2021-03-14'),(2,'Ravi',20,72000,'2020-07-01'),
 (3,'Kavi',10,68000,'2019-11-23'),(4,'Divya',10,91000,'2022-01-09'),
 (5,'Meena',20,54000,'2021-06-30'),(6,'Suresh',30,39000,'2023-02-17'),
 (7,'Latha',30,47000,'2020-09-05'),(8,'Vikram',20,88000,'2018-04-12'),
 (9,'Anita',10,52000,'2023-08-21'),(10,'Rahul',30,61000,'2019-05-19'),(11,'Bala',10,64000,'2022-05-03')"""]

add("sql-above-dept-avg","Above Department Average","medium",1400,"aggregation+subquery",
 EMP_SCHEMA,EMP_DATA,
 "List the names of employees who earn more than the average salary of their own department.",
 """SELECT e.name FROM employees e
    JOIN (SELECT dept_id, AVG(salary) a FROM employees GROUP BY dept_id) d
      ON e.dept_id=d.dept_id WHERE e.salary>d.a""")

add("sql-dept-headcount","Department Headcount","easy",900,"group-by",
 EMP_SCHEMA,EMP_DATA,
 "For each department, return its name and the number of employees. Order by department name.",
 """SELECT d.name, COUNT(e.id) AS headcount FROM departments d
    LEFT JOIN employees e ON e.dept_id=d.id GROUP BY d.id,d.name ORDER BY d.name""",ordered=True)

add("sql-top-earner-per-dept","Top Earner Per Department","hard",1700,"window-function",
 EMP_SCHEMA,EMP_DATA,
 "For each department return the name of its highest paid employee and that salary. Order by department id.",
 """SELECT d.id, e.name, e.salary FROM employees e JOIN departments d ON d.id=e.dept_id
    WHERE e.salary=(SELECT MAX(salary) FROM employees x WHERE x.dept_id=e.dept_id)
    ORDER BY d.id""",ordered=True)

add("sql-second-highest","Second Highest Salary","medium",1300,"ranking",
 EMP_SCHEMA,EMP_DATA,
 "Return the second highest distinct salary across all employees.",
 "SELECT DISTINCT salary FROM employees ORDER BY salary DESC LIMIT 1 OFFSET 1")

add("sql-no-employees","Empty Departments","easy",1000,"anti-join",
 ["CREATE TABLE departments (id INTEGER PRIMARY KEY, name TEXT)",
  "CREATE TABLE employees (id INTEGER PRIMARY KEY, name TEXT, dept_id INTEGER, salary INTEGER, hired TEXT)"],
 ["INSERT INTO departments VALUES (10,'Engineering'),(20,'Sales'),(30,'Support'),(40,'Legal')",
  "INSERT INTO employees VALUES (1,'Arun',10,45000,'2021-03-14'),(2,'Ravi',20,72000,'2020-07-01'),(3,'Kavi',30,68000,'2019-11-23')"],
 "Return the names of departments that have no employees.",
 "SELECT d.name FROM departments d LEFT JOIN employees e ON e.dept_id=d.id WHERE e.id IS NULL")

ORD_SCHEMA=["CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, city TEXT)",
 "CREATE TABLE orders (id INTEGER PRIMARY KEY, customer_id INTEGER, amount INTEGER, placed TEXT)"]
ORD_DATA=["INSERT INTO customers VALUES (1,'Nila','Chennai'),(2,'Karthik','Madurai'),(3,'Priya','Coimbatore'),(4,'Selvam','Chennai'),(5,'Deepa','Salem')",
 """INSERT INTO orders VALUES
 (101,1,2500,'2024-01-05'),(102,1,1800,'2024-02-11'),(103,2,4300,'2024-01-19'),
 (104,3,900,'2024-03-02'),(105,1,3100,'2024-03-15'),(106,2,1200,'2024-02-27'),
 (107,4,7600,'2024-01-30'),(108,3,2200,'2024-04-08'),(109,4,500,'2024-04-21')"""]

add("sql-customer-total","Customer Order Totals","easy",1000,"join+group-by",
 ORD_SCHEMA,ORD_DATA,
 "Return each customer's name and their total order amount, highest total first. Only customers with at least one order.",
 """SELECT c.name, SUM(o.amount) AS total FROM customers c JOIN orders o ON o.customer_id=c.id
    GROUP BY c.id,c.name ORDER BY total DESC""",ordered=True)

add("sql-never-ordered","Customers Without Orders","easy",950,"anti-join",
 ORD_SCHEMA,ORD_DATA,
 "Return the names of customers who have never placed an order.",
 "SELECT c.name FROM customers c LEFT JOIN orders o ON o.customer_id=c.id WHERE o.id IS NULL")

add("sql-city-avg","Average Order Value By City","medium",1350,"join+aggregate",
 ORD_SCHEMA,ORD_DATA,
 "For each city, return the city and the average order amount rounded to 2 decimals, for cities that have orders. Order by city.",
 """SELECT c.city, ROUND(AVG(o.amount),2) AS avg_amount FROM customers c
    JOIN orders o ON o.customer_id=c.id GROUP BY c.city ORDER BY c.city""",ordered=True)

add("sql-repeat-customers","Repeat Customers","medium",1250,"having",
 ORD_SCHEMA,ORD_DATA,
 "Return the names of customers who placed more than 2 orders.",
 """SELECT c.name FROM customers c JOIN orders o ON o.customer_id=c.id
    GROUP BY c.id,c.name HAVING COUNT(o.id)>2""")

add("sql-running-total","Running Order Total","hard",1800,"window-function",
 ORD_SCHEMA,ORD_DATA,
 "For customer id 1, return each order id, its amount, and a running total of amounts ordered by placed date.",
 """SELECT id, amount, SUM(amount) OVER (ORDER BY placed ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running
    FROM orders WHERE customer_id=1 ORDER BY placed""",ordered=True)

add("sql-month-revenue","Monthly Revenue","medium",1300,"date+group-by",
 ORD_SCHEMA,ORD_DATA,
 "Return each month (YYYY-MM) and total revenue for that month, ordered by month.",
 """SELECT substr(placed,1,7) AS month, SUM(amount) AS revenue FROM orders
    GROUP BY month ORDER BY month""",ordered=True)

add("sql-above-overall-avg","Orders Above Overall Average","medium",1200,"scalar-subquery",
 ORD_SCHEMA,ORD_DATA,
 "Return the ids of orders whose amount is greater than the overall average order amount.",
 "SELECT id FROM orders WHERE amount > (SELECT AVG(amount) FROM orders)")

json.dump(BANK,open("/home/pontiff/playground/codecompete/backend/src/main/resources/sql-problems.json","w"),indent=1)
print(f"{len(BANK)} SQL challenges generated & executed successfully\n")
for b in BANK:
    print(f"  {b['difficulty']:6} r{b['rating']}  {b['slug']:28} -> {len(b['expected']['rows'])} rows  [{b['pattern']}]")
