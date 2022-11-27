package dagger.internal.codegen;

import com.google.common.base.Ascii;
import com.google.common.base.Function;
import dagger.Lazy;
import javax.inject.Provider;

final class DependencyVariableNamer implements Function<DependencyRequest, String> {
  @Override
  public String apply(DependencyRequest dependency) {
    String variableName = dependency.requestElement().getSimpleName().toString();
    switch (dependency.kind()) {
      case INSTANCE:
        return variableName;
      case LAZY:
        return variableName.startsWith("lazy") && !variableName.equals("lazy")
            ? Ascii.toLowerCase(variableName.charAt(4)) + variableName.substring(5)
            : variableName;
      case PROVIDER:
        return variableName.endsWith("Provider") && !variableName.equals("Provider")
            ? variableName.substring(0, variableName.length() - 8)
            : variableName;
      case MEMBERS_INJECTOR:
        return variableName.endsWith("MembersInjector") && !variableName.equals("MembersInjector")
            ? variableName.substring(0, variableName.length() - 15)
            : variableName;
      case PRODUCED:
        return variableName.startsWith("produced") && !variableName.equals("produced")
            ? Ascii.toLowerCase(variableName.charAt(8)) + variableName.substring(9)
            : variableName;
      case PRODUCER:
        return variableName.endsWith("Producer") && !variableName.equals("Producer")
            ? variableName.substring(0, variableName.length() - 8)
            : variableName;
      default:
        throw new AssertionError();
    }
  }
}
