package dagger;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(TYPE)
@Documented
public @interface Subcomponent {

  Class<?>[] modules() default {};

  @Target(TYPE)
  @Documented
  @interface Builder {}
}
