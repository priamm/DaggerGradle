package dagger;

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
@Beta
public @interface MapKey {
  boolean unwrapValue() default true;
}
