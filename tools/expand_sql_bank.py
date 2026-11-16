"""Add reproducible, dataset-tested practice across eight everyday domains."""
import json, sqlite3
from pathlib import Path
path=Path(__file__).resolve().parents[1]/'backend/src/main/resources/sql-problems.json'
bank=json.loads(path.read_text());bank=[p for p in bank if not p['slug'].startswith('sql-lab-')]
# Tiers describe required concepts, not the length of a query.
for p in bank:
 if p['slug'] in ['sql-above-overall-avg']:p['difficulty']='medium';p['rating']=1200
 if p['pattern']=='join+aggregate':p['difficulty']='medium';p['rating']=1400
recipes=[
('Names only','Return every name, keeping duplicates.','SELECT name FROM {t}','select','easy'),
('Category labels','Return each distinct category, including NULL if present.','SELECT DISTINCT category FROM {t}','distinct','easy'),
('Active entries','Return id and name for rows with active = 1.','SELECT id,name FROM {t} WHERE active=1','where-equals','easy'),
('Low values','Return name and value where value is below 50.','SELECT name,value FROM {t} WHERE value<50','where-number','easy'),
('Plenty available','Return id where quantity is at least 10.','SELECT id FROM {t} WHERE quantity>=10','where-number','easy'),
('Missing category','Return id and name where category is NULL.','SELECT id,name FROM {t} WHERE category IS NULL','is-null','easy'),
('Largest first','Return id and value sorted by value descending, then id ascending.','SELECT id,value FROM {t} ORDER BY value DESC,id','order-by','easy'),
('First three','Return id and name of the three lowest IDs, ordered by id.','SELECT id,name FROM {t} ORDER BY id LIMIT 3','limit','easy'),
('Total quantity','Return one column with the sum of quantity. On an empty table return NULL.','SELECT SUM(quantity) FROM {t}','sum','easy'),
('Available count','Return the number of rows where quantity is greater than zero.','SELECT COUNT(*) FROM {t} WHERE quantity>0','count','easy'),
('Names with a','Return id and name where name contains the letter a (case insensitive).','SELECT id,name FROM {t} WHERE name LIKE \'%a%\'','like','easy'),
('Extended value','Return id and value multiplied by quantity, in that column order.','SELECT id,value*quantity FROM {t}','arithmetic','easy'),
('Category totals','Return category and total quantity for each category, including the NULL group.','SELECT category,SUM(quantity) FROM {t} GROUP BY category','group-by','medium'),
('Busy categories','Return category and row count for categories with at least two rows, including the NULL group.','SELECT category,COUNT(*) FROM {t} GROUP BY category HAVING COUNT(*)>=2','having','medium'),
('Above average','Return id and name for rows whose value is strictly above the overall average value.','SELECT id,name FROM {t} WHERE value>(SELECT AVG(value) FROM {t})','scalar-subquery','medium'),
('Category leaders','Return category, id and value for every row tied for the highest value in its category. Treat NULL as a category.','SELECT category,id,value FROM (SELECT *,DENSE_RANK() OVER(PARTITION BY category ORDER BY value DESC) r FROM {t}) WHERE r=1','window-function','hard'),
('Inactive entries','Return id and name for rows with active = 0.','SELECT id,name FROM {t} WHERE active=0','where-equals','easy'),
('Known categories','Return id and category for rows with a non-NULL category.','SELECT id,category FROM {t} WHERE category IS NOT NULL','is-null','easy'),
('Between values','Return id where value is between 20 and 80 inclusive.','SELECT id FROM {t} WHERE value BETWEEN 20 AND 80','between','easy'),
('Sold out','Return name where quantity is zero.','SELECT name FROM {t} WHERE quantity=0','where-equals','easy'),
('Active and stocked','Return id where active is 1 and quantity is greater than zero.','SELECT id FROM {t} WHERE active=1 AND quantity>0','where-and','easy'),
('Two categories','Return id and name where category is either Core or Extra.','SELECT id,name FROM {t} WHERE category IN (\'Core\',\'Extra\')','in','easy'),
('Minimum value','Return the minimum value, or NULL for an empty table.','SELECT MIN(value) FROM {t}','min','easy'),
('Maximum quantity','Return the highest quantity, or NULL for an empty table.','SELECT MAX(quantity) FROM {t}','max','easy'),
('Names backwards','Return id and name ordered by name descending, then id ascending.','SELECT id,name FROM {t} ORDER BY name DESC,id','order-by','easy'),
('Unique values','Return distinct values greater than zero.','SELECT DISTINCT value FROM {t} WHERE value>0','distinct','easy'),
('Average quantity','Return the average quantity, or NULL for an empty table.','SELECT AVG(quantity) FROM {t}','avg','easy'),
('Value adjustment','Return id and value plus 10.','SELECT id,value+10 FROM {t}','arithmetic','easy'),
('Category range','Return category, minimum value and maximum value for each category, including NULL.','SELECT category,MIN(value),MAX(value) FROM {t} GROUP BY category','group-by','medium'),
('Classify stock','Return id and a label: empty when quantity is 0, low when it is 1 through 9, otherwise stocked.','SELECT id,CASE WHEN quantity=0 THEN \'empty\' WHEN quantity<10 THEN \'low\' ELSE \'stocked\' END FROM {t}','case','medium'),
('Repeated names','Return each name appearing more than once and its row count.','SELECT name,COUNT(*) FROM {t} GROUP BY name HAVING COUNT(*)>1','having','medium'),
('Running quantity','Return id and cumulative quantity in ascending id order. Include the current row.','SELECT id,SUM(quantity) OVER(ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) FROM {t} ORDER BY id','window-function','hard')]
domains=[('books','Library','book value and copies'),('products','Shop','unit price and stock'),('courses','Academy','course fee and seats'),('tickets','Cinema','ticket price and remaining tickets'),('supplies','Workshop','unit cost and stock'),('plants','Nursery','unit price and available plants'),('meals','Kitchen','meal price and portions'),('rewards','Arcade','point cost and stock')]
sets=[[(1,'Atlas','Core',20,10,1),(2,'Birch','Extra',50,0,0),(3,'Cedar','Core',80,4,1),(4,'Dawn',None,100,20,1),(5,'Atlas','Extra',50,3,0)],[(11,'Alpha','Core',50,0,1),(3,'Beta','Core',50,10,0),(9,'Gamma',None,0,1,1),(1,'Delta',None,80,9,0),(20,'Alpha','Extra',20,11,1)],[(99,'Solo',None,20,0,0)],[],[(8,'Oak','Core',19,9,1),(2,'oak','Core',20,10,1),(7,'Ash','Extra',80,0,0),(1,'Elm','Extra',81,11,1),(15,'Ash',None,100,20,0),(4,'Fir',None,100,2,1)]]
def seeds(t,rows):
 def literal(v):return 'NULL' if v is None else "'"+v.replace("'","''")+"'" if isinstance(v,str) else str(v)
 return [f"INSERT INTO {t} VALUES ("+','.join(map(literal,row))+')' for row in rows]
for di,(table,label,meaning) in enumerate(domains):
 # Alternating curricula offer different concepts within each domain.
 for ri in range(16):
  title,task,query,pattern,difficulty=recipes[(ri+di*4)%32];query=query.format(t=table)
  schema=[f'CREATE TABLE {table} (id INTEGER PRIMARY KEY, name TEXT NOT NULL, category TEXT, value INTEGER NOT NULL, quantity INTEGER NOT NULL, active INTEGER NOT NULL)']
  con=sqlite3.connect(':memory:');con.execute(schema[0]);[con.execute(s) for s in seeds(table,sets[0])];cur=con.execute(query);expected={'columns':[x[0] for x in cur.description],'rows':[list(x) for x in cur.fetchall()]};con.close()
  bank.append(dict(slug=f'sql-lab-{table}-{ri+1}',title=f'{label}: {title}',difficulty=difficulty,rating={'easy':250,'medium':1100,'hard':1700}[difficulty]+ri*10,pattern=pattern,schema=schema,seedData=seeds(table,sets[0]),hiddenDatasets=[seeds(table,x) for x in sets[1:]],task=f'Table {table} tracks {meaning}. value and quantity are non-negative integers; active is 0 or 1. '+task+(' Return rows in the stated order.' if ('ORDER BY' in query and pattern!='window-function') or 'Running' in title else ' Any row order is accepted.'),referenceQuery=query,ordered='ORDER BY' in query and 'window-function'!=pattern or pattern=='window-function' and 'Running' in title,expected=expected,explanation=f'{title}: '+task+' The query uses '+pattern.replace('-',' ')+'.',hint={'easy':'Start with the requested columns and the named table. Apply only the condition or calculation requested.','medium':'Work out whether you need one result per group or one result per row. Group before filtering aggregate values.','hard':'A window can compute a value for each row without collapsing the rows. Choose its partition and ordering carefully.'}[difficulty]))
assert len(bank)==184
path.write_text(json.dumps(bank,indent=2,ensure_ascii=False)+'\n')
print('Wrote',len(bank),'problems')
