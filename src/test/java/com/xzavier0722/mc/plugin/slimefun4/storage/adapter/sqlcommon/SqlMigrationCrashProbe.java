package com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;

/** Child JVM intentionally exits at the real SQLite transaction boundary; never a production entry point. */
public final class SqlMigrationCrashProbe {
    private SqlMigrationCrashProbe() {}

    public static void main(String[] arguments) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + arguments[0])) {
            Connection intercepted = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, parameters) -> {
                        if (method.getName().equals("commit")) {
                            if (arguments[1].equals("before"))
                                Runtime.getRuntime().halt(23);
                            connection.commit();
                            Runtime.getRuntime().halt(24);
                        }
                        try {
                            return method.invoke(connection, parameters);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
            SqlUniversalBlockMigration.execute(intercepted, "", SqlMigrationFixtures.plan());
        }
        throw new AssertionError("The crash boundary was not reached");
    }
}
