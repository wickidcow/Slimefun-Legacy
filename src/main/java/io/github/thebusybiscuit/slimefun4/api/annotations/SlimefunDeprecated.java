package io.github.thebusybiscuit.slimefun4.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents Slimefun Legacy's compatibility lifecycle for a deprecated addon-facing API.
 *
 * <p>This annotation supplements Java's {@link Deprecated} annotation with a stable replacement and removal policy.
 * Deprecated APIs remain available unless a future removal version is explicitly declared.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.CONSTRUCTOR,
    ElementType.FIELD,
    ElementType.PACKAGE
})
public @interface SlimefunDeprecated {

    /**
     * The first Slimefun Legacy version that deprecated this API.
     *
     * @return the deprecation version
     */
    String since();

    /**
     * A source-level replacement or migration hint.
     *
     * @return the replacement hint, or an empty string when no direct replacement exists
     */
    String replacement() default "";

    /**
     * The earliest version in which removal may occur.
     *
     * <p>An empty value means no removal version is scheduled.
     *
     * @return the planned removal version, or an empty string
     */
    String removalVersion() default "";
}
