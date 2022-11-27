package dagger.mapkeys;

import dagger.MapKey;
import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

@Documented
@Target(METHOD)
@MapKey
public @interface LongKey {
  long value();
}