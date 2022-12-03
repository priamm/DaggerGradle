package dagger.producers;

import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(TYPE)
@Documented
public @interface ProductionSubcomponent {

  Class<?>[] modules() default {};

  @Target(TYPE)
  @Documented
  @interface Builder {}
}
