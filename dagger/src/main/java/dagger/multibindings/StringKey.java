package dagger.multibindings;

import dagger.MapKey;
import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

@Beta
@Documented
@Target(METHOD)
@MapKey
public @interface StringKey {
  String value();
}
