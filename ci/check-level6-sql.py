from pathlib import Path
import re, sqlite3

s = Path('app/src/main/java/com/example/data/AppDatabase.kt').read_text()
creates = re.findall(r'CREATE TABLE \w+ \(.*?\n\s*\)', s, re.S)
assert len(creates) == 2, f"Expected 2 V110 tables (voice_routines & audit_logs), found {len(creates)}"

c = sqlite3.connect(':memory:')
for q in creates:
    c.execute(q)

rows = {
    'voice_routines': ('routine', 'trigger', 'summary', '[]'),
    'audit_logs': ('category', 'summary', 'status', 1)
}
for table, row in rows.items():
    c.execute(f'INSERT INTO {table} VALUES(NULL,{",".join("?" for _ in row)})', row)

before = {t: c.execute(f'SELECT * FROM {t}').fetchall() for t in rows}
indices = re.findall(r'db.execSQL\("(CREATE INDEX IF NOT EXISTS [^"]+)"\)', s)
assert len(indices) == 1
for q in indices:
    c.execute(q)
for t in rows:
    assert c.execute(f'SELECT * FROM {t}').fetchall() == before[t], t
assert 'DROP TABLE IF EXISTS memory_facts' in s
assert 'DROP TABLE IF EXISTS location_reminders' in s
assert 'DROP TABLE IF EXISTS chat_messages' in s
print('PASS: V110 DB schema (voice_routines + audit_logs) verified cleanly')
