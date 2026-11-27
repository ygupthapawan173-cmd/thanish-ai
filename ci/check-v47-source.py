from pathlib import Path
import sqlite3
p=Path('app/src/main/java/com/example')
def read(f):return (p/f).read_text()
s=read('ai/PanduAiRepository.kt'); assert 'LocalBrainEngine' not in s and 'val response = gateway.answer(settings, userMessage' in s
assert not (p/'ai/EmbeddedLocalModel.kt').exists()
s=read('voice/VoiceInputController.kt'); assert 'handler.postDelayed(this, 150L)' in s and 'if (!current() || enforcePrivacy()) return' in s
s=read('voice/TeluguTtsController.kt'); assert 'TtsChunkPolicy.split' in s and 'TextToSpeech.QUEUE_ADD' in s and 'if (error==null || backgroundOwned)' not in s
s=read('data/AppDatabase.kt'); assert 'unreadableEncryptedRows = true; null' in s and s.count('?: continue')>=7
s=read('security/KeystoreCryptoManager.kt'); assert 'cipher.init(Cipher.DECRYPT_MODE, existing' in s
s=read('data/PortableDataBackup.kt'); assert 'if (duplicate) { skipped++; continue }' in s and 'cv.remove("id")' in s
# Desktop illustration of SQLite ID collision / unique key rules, not execution of Android restore.
c=sqlite3.connect(':memory:');c.execute('create table t(id integer primary key autoincrement, text text unique)');c.execute('insert into t values(1,?)',('old',));c.execute('insert or ignore into t(text) values(?)',('new',));c.execute('insert or ignore into t(text) values(?)',('new',));assert c.execute('select * from t order by id').fetchall()==[(1,'old'),(2,'new')]
print('PASS: 7 source guards (updated for V50 intentional LLM removal) + 1 desktop SQLite collision example (not Android runtime tests)')
