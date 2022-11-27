package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import dagger.producers.Produced;
import dagger.producers.Producer;
import javax.lang.model.type.TypeMirror;

final class FrameworkTypes {

  private static final ImmutableSet<Class<?>> PRODUCER_TYPES =
      ImmutableSet.of(Produced.class, Producer.class);

  static boolean isProducerType(TypeMirror type) {
    if (!MoreTypes.isType(type)) {
      return false;
    }
    for (Class<?> clazz : PRODUCER_TYPES) {
      if (MoreTypes.isTypeOf(clazz, type)) {
        return true;
      }
    }
    return false;
  }

  private FrameworkTypes() {}
}
