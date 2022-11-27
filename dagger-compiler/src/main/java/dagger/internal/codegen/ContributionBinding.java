package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

abstract class ContributionBinding extends Binding {
  static enum BindingType {
    MAP,
    SET,
    UNIQUE;

    boolean isMultibinding() {
      return !this.equals(UNIQUE);
    }
  }

  abstract BindingType bindingType();

  abstract Optional<DeclaredType> nullableType();

  abstract Optional<TypeElement> contributedBy();

  abstract boolean isSyntheticBinding();

  abstract Class<?> frameworkClass();

  static <B extends ContributionBinding> ImmutableListMultimap<BindingType, B> bindingTypesFor(
      Iterable<? extends B> bindings) {
    ImmutableListMultimap.Builder<BindingType, B> builder =
        ImmutableListMultimap.builder();
    builder.orderKeysBy(Ordering.<BindingType>natural());
    for (B binding : bindings) {
      builder.put(binding.bindingType(), binding);
    }
    return builder.build();
  }

  static BindingType bindingTypeFor(Iterable<? extends ContributionBinding> bindings) {
    checkNotNull(bindings);
    checkArgument(!Iterables.isEmpty(bindings), "no bindings");
    Set<BindingType> types = EnumSet.noneOf(BindingType.class);
    for (ContributionBinding binding : bindings) {
      types.add(binding.bindingType());
    }
    if (types.size() > 1) {
      throw new IllegalArgumentException(
          String.format(ErrorMessages.MULTIPLE_BINDING_TYPES_FORMAT, types));
    }
    return Iterables.getOnlyElement(types);
  }
}
