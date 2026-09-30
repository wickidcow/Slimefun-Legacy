from pathlib import Path
import hashlib

path = Path('src/test/java/com/xzavier0722/mc/plugin/slimefun4/storage/adapter/sqlcommon/SqlUniversalBlockMigrationTest.java')
def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
assert blob(path.read_bytes()) == 'a932bb8d68c5b83693176534113295c7b01729df'
source = path.read_text()
before = '''                            assertThrows(BlockStorageMigration.Failure.class,
                                    () -> SqlUniversalBlockMigration.execute(failAfterUpdate(connection, 7), prefix, SqlMigrationFixtures.plan()));
                            assertEquals(original, SqlMigrationFixtures.dump(connection, prefix));
                            SqlUniversalBlockMigration.execute(connection, prefix, SqlMigrationFixtures.plan());
                            assertCommitted(connection, prefix);'''
after = '''                            for (int stage = 1; stage <= 7; stage++) {
                                int failureStage = stage;
                                var failure = assertThrows(BlockStorageMigration.Failure.class,
                                        () -> SqlUniversalBlockMigration.execute(failAfterUpdate(connection, failureStage),
                                                prefix, SqlMigrationFixtures.plan()));
                                assertTrue(failure.isRestagingSafe(), "Confirmed rollback at mutation " + stage);
                                assertEquals(original, SqlMigrationFixtures.dump(connection, prefix),
                                        "All original rows must survive mutation failure " + stage);
                                assertTrue(connection.getAutoCommit());
                            }
                            var lostAcknowledgement = wrap(connection, (method, parameters) -> {
                                if (method.equals("commit")) {
                                    connection.commit();
                                    throw new SQLException("injected lost acknowledgement after real database commit");
                                }
                                return UNHANDLED;
                            });
                            var uncertain = assertThrows(BlockStorageMigration.Failure.class,
                                    () -> SqlUniversalBlockMigration.execute(lostAcknowledgement, prefix,
                                            SqlMigrationFixtures.plan()));
                            assertFalse(uncertain.isRestagingSafe());
                            assertCommitted(connection, prefix);'''
assert source.count(before) == 1
path.write_text(source.replace(before, after))
assert blob(path.read_bytes()) == '95b02b60c75cc6c1065d4d399d891579f565bfbe'
print('EXTENDED_REAL_DATABASE_CASES', blob(path.read_bytes()), path)
