package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ScopedLockConcurrencyTest {
    @TestFactory
    Stream<DynamicTest> preservesScopeOwnershipUntilEveryCallerReleasesIt() {
        return ScopedLockRegression.cases().stream()
                .map(test -> DynamicTest.dynamicTest(test.name(), test.body()::run));
    }
}
