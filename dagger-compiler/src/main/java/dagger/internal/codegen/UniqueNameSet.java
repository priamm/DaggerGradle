package dagger.internal.codegen;

import java.util.HashSet;
import java.util.Set;

final class UniqueNameSet {
  private final Set<String> uniqueNames = new HashSet<>();

  String getUniqueName(CharSequence base) {
    String name = base.toString();
    for (int differentiator = 2; !uniqueNames.add(name); differentiator++) {
      name = base.toString() + differentiator;
    }
    return name;
  }
}
