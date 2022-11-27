package dagger.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;

import static dagger.internal.Collections.newLinkedHashSetWithExpectedSize;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

public final class SetFactory<T> implements Factory<Set<T>> {

  private static final String ARGUMENTS_MUST_BE_NON_NULL =
      "SetFactory.create() requires its arguments to be non-null";

  private static final Factory<Set<Object>> EMPTY_FACTORY =
      new Factory<Set<Object>>() {
        @Override
        public Set<Object> get() {
          return emptySet();
        }
      };

  @SuppressWarnings({"unchecked", "rawtypes"}) // safe covariant cast
  public static <T> Factory<Set<T>> create() {
    return (Factory<Set<T>>) (Factory) EMPTY_FACTORY;
  }

  public static <T> Factory<Set<T>> create(Factory<Set<T>> factory) {
    assert factory != null : ARGUMENTS_MUST_BE_NON_NULL;
    return factory;
  }

  public static <T> Factory<Set<T>> create(
      @SuppressWarnings("unchecked") Provider<Set<T>>... providers) {
    assert providers != null : ARGUMENTS_MUST_BE_NON_NULL;

    List<Provider<Set<T>>> contributingProviders = Arrays.asList(providers);

    assert !contributingProviders.contains(null)
        : "Codegen error?  Null within provider list.";
    assert !hasDuplicates(contributingProviders)
        : "Codegen error?  Duplicates in the provider list";

    return new SetFactory<T>(contributingProviders);
  }

  private static boolean hasDuplicates(List<? extends Object> original) {
    Set<Object> asSet = new HashSet<Object>(original);
    return original.size() != asSet.size();
  }

  private final List<Provider<Set<T>>> contributingProviders;

  private SetFactory(List<Provider<Set<T>>> contributingProviders) {
    this.contributingProviders = contributingProviders;
  }

  @Override
  public Set<T> get() {
    int size = 0;

    List<Set<T>> providedSets = new ArrayList<Set<T>>(contributingProviders.size());
    for (int i = 0, c = contributingProviders.size(); i < c; i++) {
      Provider<Set<T>> provider = contributingProviders.get(i);
      Set<T> providedSet = provider.get();
      if (providedSet == null) {
        throw new NullPointerException(provider + " returned null");
      }
      providedSets.add(providedSet);
      size += providedSet.size();
    }

    Set<T> result = newLinkedHashSetWithExpectedSize(size);
    for (int i = 0, c = providedSets.size(); i < c; i++) {
      for (T element : providedSets.get(i)) {
        if (element == null) {
          throw new NullPointerException("a null element was provided");
        }
        result.add(element);
      }
    }
    return unmodifiableSet(result);
  }
}
