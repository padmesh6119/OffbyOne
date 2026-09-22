import json, sqlite3
BANK=json.load(open("/home/pontiff/playground/codecompete/backend/src/main/resources/sql-problems.json"))
by={b["slug"]:b for b in BANK}

def run(b,q):
    con=sqlite3.connect(":memory:")
    for s in b["schema"]: con.execute(s)
    for s in b["seedData"]: con.execute(s)
    cur=con.execute(q); rows=[tuple(r) for r in cur.fetchall()]; con.close(); return rows

def compare(b,rows):
    exp=[tuple(r) for r in b["expected"]["rows"]]
    if b["ordered"]: return rows==exp
    return sorted(exp,key=repr)==sorted(rows,key=repr)   # multiset, order-insensitive

CASES=[
 # (slug, label, query, should_pass)
 ("sql-above-dept-avg","CTE form","""WITH a AS (SELECT dept_id,AVG(salary) av FROM employees GROUP BY dept_id)
     SELECT e.name FROM employees e JOIN a ON a.dept_id=e.dept_id WHERE e.salary>a.av""",True),
 ("sql-above-dept-avg","correlated subquery","""SELECT name FROM employees e WHERE salary >
     (SELECT AVG(salary) FROM employees x WHERE x.dept_id=e.dept_id)""",True),
 ("sql-above-dept-avg","window function","""SELECT name FROM (SELECT name,salary,
     AVG(salary) OVER (PARTITION BY dept_id) av FROM employees) WHERE salary>av""",True),
 ("sql-above-dept-avg","reversed order","""SELECT name FROM employees e WHERE salary >
     (SELECT AVG(salary) FROM employees x WHERE x.dept_id=e.dept_id) ORDER BY name DESC""",True),
 ("sql-above-dept-avg","WRONG: >= not >","""SELECT name FROM employees e WHERE salary >=
     (SELECT AVG(salary) FROM employees x WHERE x.dept_id=e.dept_id)""",False),
 ("sql-above-dept-avg","WRONG: overall avg","""SELECT name FROM employees
     WHERE salary > (SELECT AVG(salary) FROM employees)""",False),
 ("sql-never-ordered","NOT IN form","SELECT name FROM customers WHERE id NOT IN (SELECT customer_id FROM orders)",True),
 ("sql-never-ordered","NOT EXISTS form","""SELECT name FROM customers c WHERE NOT EXISTS
     (SELECT 1 FROM orders o WHERE o.customer_id=c.id)""",True),
 ("sql-repeat-customers","HAVING >=3","""SELECT c.name FROM customers c JOIN orders o ON o.customer_id=c.id
     GROUP BY c.id,c.name HAVING COUNT(*)>=3""",True),
 ("sql-above-overall-avg","join-free alt","SELECT id FROM orders WHERE amount>(SELECT SUM(amount)*1.0/COUNT(*) FROM orders)",True),
 ("sql-top-earner-per-dept","window RANK","""SELECT dept_id,name,salary FROM (SELECT dept_id,name,salary,
     RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) rk FROM employees) WHERE rk=1 ORDER BY dept_id""",True),
]
ok=fail=0
for slug,label,q,should in CASES:
    b=by[slug]
    try: got=compare(b,run(b,q))
    except Exception as e: got=False; label+=f" (err {str(e)[:30]})"
    good = (got==should)
    ok,fail=(ok+1,fail) if good else (ok,fail+1)
    mark="OK " if good else "BAD"
    print(f"{mark} {slug:26} {label:26} accepted={got} expected={should}")
print(f"\n{ok} correct / {fail} incorrect")
print("VERDICT:", "answer-by-execution works" if fail==0 else "comparison logic needs work")
