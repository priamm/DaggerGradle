package dagger;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

@Target(TYPE)
@Documented
public @interface Subcomponent {
  Class<?>[] modules() default {};
}
