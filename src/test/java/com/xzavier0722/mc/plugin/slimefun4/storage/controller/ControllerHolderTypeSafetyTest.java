package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Exercises the actual registry and its erased addon-facing methods without opening storage. */
class ControllerHolderTypeSafetyTest {
    @AfterEach
    void cleanUp() {
        ControllerHolder.clearControllers();
    }

    @Test
    void classAndStorageTypeKeysStayIndependent() {
        assertNull(ControllerHolder.getController(BlockController.class, StorageType.SQLITE));
        var blockSqlite = ControllerHolder.createController(BlockController.class, StorageType.SQLITE);
        var blockMysql = ControllerHolder.createController(BlockController.class, StorageType.MYSQL);
        var profileSqlite = ControllerHolder.createController(ProfileController.class, StorageType.SQLITE);

        assertSame(blockSqlite, ControllerHolder.getController(BlockController.class, StorageType.SQLITE));
        assertSame(blockMysql, ControllerHolder.getController(BlockController.class, StorageType.MYSQL));
        assertSame(profileSqlite, ControllerHolder.getController(ProfileController.class, StorageType.SQLITE));
        assertNull(ControllerHolder.getController(BlockController.class, StorageType.POSTGRESQL));
        assertNull(ControllerHolder.getController(ProfileController.class, StorageType.MYSQL));
        assertNotSame(blockSqlite, blockMysql);
    }

    @Test
    void creatingTheSameClassAndStorageTypeStillReplacesItsEntry() {
        var old = ControllerHolder.createController(BlockController.class, StorageType.SQLITE);
        var replacement = ControllerHolder.createController(BlockController.class, StorageType.SQLITE);
        assertNotSame(old, replacement);
        assertSame(replacement, ControllerHolder.getController(BlockController.class, StorageType.SQLITE));
    }

    @Test
    void clearStillInvalidatesAllClassAndStorageMappings() {
        ControllerHolder.createController(BlockController.class, StorageType.MYSQL);
        ControllerHolder.createController(ProfileController.class, StorageType.POSTGRESQL);
        ControllerHolder.clearControllers();
        assertNull(ControllerHolder.getController(BlockController.class, StorageType.MYSQL));
        assertNull(ControllerHolder.getController(ProfileController.class, StorageType.POSTGRESQL));
    }

    @Test
    void failingConstructionKeepsTheOriginalExceptionContractAndDoesNotRegister() {
        var failure = assertThrows(
                IllegalStateException.class,
                () -> ControllerHolder.createController(FailingController.class, StorageType.SQLITE));
        assertEquals(
                "Exception thrown while creating controller: FailingController",
                failure.getMessage());
        var reflected = assertInstanceOf(InvocationTargetException.class, failure.getCause());
        assertEquals("fixture failure", reflected.getCause().getMessage());
        assertNull(ControllerHolder.getController(FailingController.class, StorageType.SQLITE));
    }

    @Test
    void publicLegacyMethodsRetainTheirErasedDescriptors() throws NoSuchMethodException {
        var get = ControllerHolder.class.getDeclaredMethod("getController", Class.class, StorageType.class);
        var create = ControllerHolder.class.getDeclaredMethod("createController", Class.class, StorageType.class);
        var clear = ControllerHolder.class.getDeclaredMethod("clearControllers");
        assertSame(ADataController.class, get.getReturnType());
        assertSame(ADataController.class, create.getReturnType());
        assertSame(void.class, clear.getReturnType());
        assertEquals(1, ControllerHolder.class.getTypeParameters().length);
        assertEquals(1, get.getTypeParameters().length);
        assertEquals(1, create.getTypeParameters().length);
    }

    public static final class BlockController extends ADataController {
        public BlockController() {
            super(DataType.BLOCK_STORAGE);
        }
    }

    public static final class ProfileController extends ADataController {
        public ProfileController() {
            super(DataType.PLAYER_PROFILE);
        }
    }

    public static final class FailingController extends ADataController {
        public FailingController() {
            super(DataType.BLOCK_STORAGE);
            throw new IllegalArgumentException("fixture failure");
        }
    }
}
