from pathlib import Path
import hashlib
import shutil


def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


root = Path('src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage')
path = root / 'task/DelayedSavingLooperTask.java'
assert blob(path.read_bytes()) == '1395f28b4ee99b22e61b15111f9ade4cb030d326'
shutil.copyfile('.audit/delayed-save/DelayedSavingLooperTask.java', path)
path = root / 'controller/BlockDataController.java'
assert blob(path.read_bytes()) == 'f918c6d3b429b4b0c8c1330be8fda57cf12869ed'
text = path.read_text()
old = '''                        new DelayedSavingLooperTask(
                                forceSavePeriod, () -> new HashMap<>(delayedWriteTasks), delayedWriteTasks::remove),'''
assert text.count(old) == 1
text = text.replace(old, '                        createDelayedSavingLooper(forceSavePeriod),')
anchor = '    public int getPendingDelayedWriteTaskCount() {'
# Insert before its Javadoc, rather than attaching that public method's documentation to the helper.
position = text.rfind('    /**', 0, text.index(anchor))
assert position > 0
helper = '''    /** Creates the same looper used by delayed saving, with replacement-safe queue completion. */
    DelayedSavingLooperTask createDelayedSavingLooper(int forceSavePeriod) {
        return DelayedSavingLooperTask.withTaskCompletion(
                forceSavePeriod,
                () -> new HashMap<>(delayedWriteTasks),
                (key, completedTask) -> delayedWriteTasks.remove(key, completedTask));
    }

'''
text = text[:position] + helper + text[position:]
path.write_text(text)
target = Path('src/test/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/DelayedSaveReplacementTest.java')
assert not target.exists()
shutil.copyfile('.audit/delayed-save/DelayedSaveReplacementTest.java', target)
