from pathlib import Path
import hashlib
root = Path('.')
test = root / 'src/test/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/InventoryReadFailureTest.java'
helper = test.with_name('InventoryReadTestPlugin.java')
def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
assert blob(test.read_bytes()) == 'f01984540ec8d3113ea06c90e0c03fd26176f0e6'
assert blob(helper.read_bytes()) == '1e4191870d052f925b525e7e536c9ddd4fcc98f9'
text = test.read_text()
text = text.replace('    private ServerMock server;', '    private ServerMock server;\n    private InventoryReadTestPlugin fixture;', 1)
text = text.replace('        MockBukkit.loadSimple(InventoryReadTestPlugin.class);', '        fixture = new InventoryReadTestPlugin(server);', 1)
text = text.replace('    @AfterEach void tearDown() { MockBukkit.unmock(); }', '''    @AfterEach void tearDown() {
        try {
            if (fixture != null) fixture.close();
        } finally {
            MockBukkit.unmock();
        }
    }''', 1)
helper_text = '''package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Field;
import org.mockbukkit.mockbukkit.ServerMock;

/** Construct the real final plugin without enabling production services; restore static state after each test. */
final class InventoryReadTestPlugin implements AutoCloseable {
    private final Field instance;
    private final Object previous;

    InventoryReadTestPlugin(ServerMock server) {
        try {
            instance = Slimefun.class.getDeclaredField("instance");
            instance.setAccessible(true);
            previous = instance.get(null);
            Slimefun plugin = (Slimefun) server.getPluginManager().loadPlugin(Slimefun.class);
            instance.set(null, plugin);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not initialize the inventory-read fixture", failure);
        }
    }

    @Override
    public void close() {
        try {
            instance.set(null, previous);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not restore the inventory-read fixture", failure);
        }
    }
}
'''
assert blob(helper_text.encode()) == '5753a8964a8151d4cfc0b26f4d458d942e721e48'
assert blob(text.encode()) == '1056cf0ccc2ee721b88597018430a3bd662fb6fe'
helper.write_text(helper_text)
test.write_text(text)
print('TEST_FIXTURE_CORRECTION', blob(helper.read_bytes()), helper)
print('TEST_FIXTURE_CORRECTION', blob(test.read_bytes()), test)
