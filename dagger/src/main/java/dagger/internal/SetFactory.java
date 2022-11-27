package dagger.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;

import static dagger.internal.Collections.newLinkedHashSetWithExpectedSize;
import static java.util.Collections.unmodifiableSet;

public final class SetFactory<T> implements Factory<Set<T>> {

  public static <T> Factory<Set<T>> create(Provider<Set<T>> first,
      @SuppressWarnings("unchecked") Provider<Set<T>>... rest) {
    if (first == null) {
      throw new NullPointerException();
    }
    if (rest == null) {
      throw new NullPointerException();
    }
    Set<Provider<Set<T>>> contributingProviders = newLinkedHashSetWithExpectedSize(1 + rest.length);
    contributingProviders.add(first);
    for (Provider<Set<T>> provider : rest) {
      if (provider == null) {
        throw new NullPointerException();
      }
      contributingProviders.add(provider);
    }
    return new SetFactory<T>(contributingProviders);
  }

  private final Set<Provider<Set<T>>> contributingProviders;

  private SetFactory(Set<Provider<Set<T>>> contributingProviders) {
    this.contributingProviders = contributingProviders;
  }

  @Override
  public Set<T> get() {
    List<Set<T>> providedSets = new ArrayList<Set<T>>(contributingProviders.size());
    for (Provider<Set<T>> provider : contributingProviders) {
      Set<T> providedSet = provider.get();
      if (providedSet == null) {
        throw new NullPointerException(provider + " returned null");
      }
      providedSets.add(providedSet);
    }
    int size = 0;
    for (Set<T> providedSet : providedSets) {
      size += providedSet.size();
    }
    Set<T> result = newLinkedHashSetWithExpectedSize(size);
    for (Set<T> s : providedSets) {
      for (T element : s) {
        if (element == null) {
          throw new NullPointerException("a null element was provided");
        }
        result.add(element);
      }
    }
    return unmodifiableSet(result);
  }
}
