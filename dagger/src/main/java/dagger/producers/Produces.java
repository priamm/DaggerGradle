package dagger.producers;

import dagger.internal.Beta;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

@Documented
@Target(METHOD)
@Beta
public @interface Produces {

  enum Type {
    UNIQUE,

    SET,

    SET_VALUES,

    MAP;
  }

  Type type() default Type.UNIQUE;
}
