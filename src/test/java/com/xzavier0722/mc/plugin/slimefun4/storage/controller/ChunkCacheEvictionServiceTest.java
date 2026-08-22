package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkCacheEvictionServiceTest {

    @Test
    void controllerStateAccessorsMatchCurrentStorageLayout() {
        var controller = new BlockDataController();
        assertTrue(ChunkCacheEvictionService.canAccessControllerState(controller));
    }
}
