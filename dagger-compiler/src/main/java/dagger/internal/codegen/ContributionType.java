package dagger.internal.codegen;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;

enum ContributionType {
  MAP,
  SET,
  UNIQUE,
  ;

  interface HasContributionType {
    ContributionType contributionType();
  }

  boolean isMultibinding() {
    return !this.equals(UNIQUE);
  }

  static ContributionType forProvisionType(dagger.Provides.Type provisionType) {
    switch (provisionType) {
      case SET:
      case SET_VALUES:
        return SET;
      case MAP:
        return MAP;
      case UNIQUE:
        return UNIQUE;
      default:
        throw new AssertionError("Unknown provision type: " + provisionType);
    }
  }

  static <T extends HasContributionType>
      ImmutableListMultimap<ContributionType, T> indexByContributionType(
          Iterable<T> haveContributionTypes) {
    return Multimaps.index(
        haveContributionTypes,
        new Function<HasContributionType, ContributionType>() {
          @Override
          public ContributionType apply(HasContributionType hasContributionType) {
            return hasContributionType.contributionType();
          }
        });
  }
}
