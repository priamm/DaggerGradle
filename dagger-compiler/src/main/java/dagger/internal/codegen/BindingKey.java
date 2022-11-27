package dagger.internal.codegen;

import com.google.auto.value.AutoValue;

@AutoValue
abstract class BindingKey {
  enum Kind {
    CONTRIBUTION, MEMBERS_INJECTION;
  }

  static BindingKey create(Kind kind, Key key) {
    return new AutoValue_BindingKey(kind, key);
  }

  abstract Kind kind();
  abstract Key key();
}
