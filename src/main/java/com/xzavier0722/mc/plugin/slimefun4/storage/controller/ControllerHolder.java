package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The public generic parameter is retained for addon source compatibility.
 * Internally, the registry class key determines the concrete controller type.
 */
public class ControllerHolder<T extends ADataController> {
    private static final Map<Class<? extends ADataController>, ControllerHolder<?>> holders = new ConcurrentHashMap<>();

    private final Map<StorageType, ADataController> controllers;

    public static <CT extends ADataController> CT getController(Class<CT> clazz, StorageType type) {
        final var holder = holders.get(clazz);
        return holder == null ? null : clazz.cast(holder.get(type));
    }

    public static void clearControllers() {
        holders.clear();
    }

    public static <CT extends ADataController> CT createController(Class<CT> clazz, StorageType type) {
        try {
            var re = clazz.getDeclaredConstructor().newInstance();
            holders.computeIfAbsent(clazz, k -> new ControllerHolder<>()).put(type, re);
            return re;
        } catch (Throwable e) {
            throw new IllegalStateException("Exception thrown while creating controller: " + clazz.getSimpleName(), e);
        }
    }

    private ControllerHolder() {
        controllers = new ConcurrentHashMap<>();
    }

    private ADataController get(StorageType type) {
        return controllers.get(type);
    }

    private void put(StorageType type, ADataController controller) {
        controllers.put(type, controller);
    }

    private void remove(StorageType type) {
        controllers.remove(type);
    }
}
