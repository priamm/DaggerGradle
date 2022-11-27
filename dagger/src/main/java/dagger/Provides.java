package dagger;

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented @Target(METHOD) @Retention(RUNTIME)
public @interface Provides {

  enum Type {
    UNIQUE,
    SET,
    SET_VALUES,
    @Beta
    MAP;
  }

  Type type() default Type.UNIQUE;
}
