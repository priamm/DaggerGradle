package dagger.internal.codegen;

import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import dagger.Lazy;
import javax.inject.Provider;

final class DependencyVariableNamer implements Function<DependencyRequest, String> {
  @Override
  public String apply(DependencyRequest dependency) {
    if (dependency.overriddenVariableName().isPresent()) {
      return dependency.overriddenVariableName().get();
    }
    String variableName = dependency.requestElement().getSimpleName().toString();
    if (Ascii.isUpperCase(variableName.charAt(0))) {
      variableName = toLowerCamel(variableName);
    }
    switch (dependency.kind()) {
      case INSTANCE:
        return variableName;
      case LAZY:
        return variableName.startsWith("lazy") && !variableName.equals("lazy")
            ? toLowerCamel(variableName.substring(4))
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
            ? toLowerCamel(variableName.substring(8))
            : variableName;
      case PRODUCER:
        return variableName.endsWith("Producer") && !variableName.equals("Producer")
            ? variableName.substring(0, variableName.length() - 8)
            : variableName;
      default:
        throw new AssertionError();
    }
  }

  private String toLowerCamel(String name) {
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, name);
  }
}
