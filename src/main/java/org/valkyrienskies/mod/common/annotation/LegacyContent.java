package org.valkyrienskies.mod.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation that is put on classes and methods that are part of the legacy content.
 * They will be removed when exporting to the no-legacy jar.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface LegacyContent {
    /**
     * Ant-style resource paths to omit along with this declaration.
     * */
    String[] resources() default {};
}