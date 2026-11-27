from pathlib import Path
import re, sqlite3
s=Path('app/src/main/java/com/example/data/AppDatabase.kt').read_text()
creates=re.findall(r'CREATE TABLE \w+ \(.*?\n\s*\)',s,re.S)
assert len(creates)==5
c=sqlite3.connect(':memory:')
for q in creates: c.execute(q)
rows={
'chat_messages': ('hello',1,0,'0:00','',0,0,1),
'memory_facts': ('key','legacy value',1),
'voice_routines': ('routine','trigger','summary','[]'),
'location_reminders': ('place','reminder',1),
'audit_logs': ('category','summary','status',1)}
for table,row in rows.items():
    c.execute(f'INSERT INTO {table} VALUES(NULL,{",".join("?" for _ in row)})',row)
before={t:c.execute(f'SELECT * FROM {t}').fetchall() for t in rows}
indices=re.findall(r'db.execSQL\("(CREATE INDEX IF NOT EXISTS [^"]+)"\)',s)
assert len(indices)==3
for q in indices: c.execute(q)
for t in rows: assert c.execute(f'SELECT * FROM {t}').fetchall()==before[t],t
assert 'DROP TABLE' not in s
print('PASS: 5 table rows preserved by actual index SQL (SQLite desktop test, not Android migration test)')
