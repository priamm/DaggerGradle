package dagger.mapkeys;

import dagger.MapKey;
import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

@Beta
@Documented
@Target(METHOD)
@MapKey
@Deprecated
public @interface StringKey {
  String value();
}
