package dagger.internal.codegen;

import dagger.Provides;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.AnnotationMirror;

final class ErrorMessages {

  static final String INDENT = "    ";

  static final String MULTIPLE_INJECT_CONSTRUCTORS =
      "Types may only contain one @Inject constructor.";

  static final String FINAL_INJECT_FIELD = "@Inject fields may not be final";

  static final String ABSTRACT_INJECT_METHOD = "Methods with @Inject may not be abstract.";
  static final String GENERIC_INJECT_METHOD =
      "Methods with @Inject may not declare type parameters.";

  static final String MULTIPLE_QUALIFIERS =
      "A single injection site may not use more than one @Qualifier.";

  static final String MULTIPLE_SCOPES = "A single binding may not declare more than one @Scope.";

  static final String INJECT_ON_PRIVATE_CONSTRUCTOR =
      "Dagger does not support injection into private constructors";
  static final String INJECT_CONSTRUCTOR_ON_INNER_CLASS =
      "@Inject constructors are invalid on inner classes";
  static final String INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS =
      "@Inject is nonsense on the constructor of an abstract class";
    static final String QUALIFIER_ON_INJECT_CONSTRUCTOR =
      "@Qualifier annotations are not allowed on @Inject constructors.";

  static final String PRIVATE_INJECT_FIELD =
      "Dagger does not support injection into private fields";

  static final String PRIVATE_INJECT_METHOD =
      "Dagger does not support injection into private methods";

  static final String INJECT_INTO_PRIVATE_CLASS =
      "Dagger does not support injection into private classes";

  static final String DUPLICATE_BINDINGS_FOR_KEY_FORMAT =
      "%s is bound multiple times:";

  static final String PROVIDES_METHOD_RETURN_TYPE =
      "@Provides methods must either return a primitive, an array or a declared type.";

  static final String PRODUCES_METHOD_RETURN_TYPE =
      "@Produces methods must either return a primitive, an array or a declared type, or a"
      + " ListenableFuture of one of those types.";

  static final String PRODUCES_METHOD_RAW_FUTURE =
      "@Produces methods cannot return a raw ListenableFuture.";

  static final String BINDING_METHOD_SET_VALUES_RAW_SET =
      "@%s methods of type set values cannot return a raw Set";

  static final String PROVIDES_METHOD_SET_VALUES_RETURN_SET =
      "@Provides methods of type set values must return a Set";

  static final String PRODUCES_METHOD_SET_VALUES_RETURN_SET =
      "@Produces methods of type set values must return a Set or ListenableFuture of Set";

  static final String BINDING_METHOD_MUST_RETURN_A_VALUE =
      "@%s methods must return a value (not void).";

  static final String BINDING_METHOD_ABSTRACT = "@%s methods cannot be abstract";

  static final String BINDING_METHOD_STATIC = "@%s methods cannot be static";

  static final String BINDING_METHOD_PRIVATE = "@%s methods cannot be private";

  static final String BINDING_METHOD_TYPE_PARAMETER =
      "@%s methods may not have type parameters.";

  static final String BINDING_METHOD_NOT_IN_MODULE =
      "@%s methods can only be present within a @%s";

  static final String BINDING_METHOD_NOT_MAP_HAS_MAP_KEY =
      "@%s methods of non map type cannot declare a map key";

  static final String BINDING_METHOD_WITH_NO_MAP_KEY =
      "@%s methods of type map must declare a map key";

  static final String BINDING_METHOD_WITH_MULTIPLE_MAP_KEY =
      "@%s methods may not have more than one @MapKey-marked annotation";

  static final String BINDING_METHOD_WITH_SAME_NAME =
      "Cannot have more than one @%s method with the same name in a single module";

  static final String MODULES_WITH_TYPE_PARAMS_MUST_BE_ABSTRACT =
      "Modules with type parameters must be abstract";

  static final String REFERENCED_MODULES_MUST_NOT_BE_ABSTRACT =
      "%s is listed as a module, but is abstract";

  static final String REFERENCED_MODULE_NOT_ANNOTATED =
      "%s is listed as a module, but is not annotated with @%s";

  static final String REFERENCED_MODULE_MUST_NOT_HAVE_TYPE_PARAMS =
      "%s is listed as a module, but has type parameters";

  static final String PROVIDES_METHOD_OVERRIDES_ANOTHER =
      "@%s methods may not override another method. Overrides: %s";

  static final String METHOD_OVERRIDES_PROVIDES_METHOD =
      "@%s methods may not be overridden in modules. Overrides: %s";

  static final String PROVIDES_OR_PRODUCES_METHOD_MULTIPLE_QUALIFIERS =
      "Cannot use more than one @Qualifier on a @Provides or @Produces method";

  static final String MAPKEY_WITHOUT_FIELDS =
      "Map key annotation does not have fields";

  static final String MULTIPLE_BINDING_TYPES_FORMAT =
      "More than one binding present of different types %s";

  static final String MULTIPLE_BINDING_TYPES_FOR_KEY_FORMAT =
      "%s has incompatible bindings:\n";

  static final String PROVIDER_ENTRY_POINT_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT =
      "%s is a provision entry-point, which cannot depend on a production.";

  static final String PROVIDER_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT =
      "%s is a provision, which cannot depend on a production.";

  static final String REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT =
      "%s cannot be provided without an @Inject constructor or from an @Provides-annotated method.";

  static final String REQUIRES_PROVIDER_FORMAT =
      "%s cannot be provided without an @Provides-annotated method.";

  static final String REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_OR_PRODUCER_FORMAT =
      "%s cannot be provided without an @Inject constructor or from an @Provides- or "
      + "@Produces-annotated method.";

  static final String REQUIRES_PROVIDER_OR_PRODUCER_FORMAT =
      "%s cannot be provided without an @Provides- or @Produces-annotated method.";

  static final String MEMBERS_INJECTION_DOES_NOT_IMPLY_PROVISION =
      "This type supports members injection but cannot be implicitly provided.";

  static final String MEMBERS_INJECTION_WITH_RAW_TYPE =
      "%s has type parameters, cannot members inject the raw type. via:\n%s";

  static final String MEMBERS_INJECTION_WITH_UNBOUNDED_TYPE =
      "Type parameters must be bounded for members injection. %s required by %s, via:\n%s";

  static final String CONTAINS_DEPENDENCY_CYCLE_FORMAT = "%s.%s() contains a dependency cycle:\n%s";

  static final String MALFORMED_MODULE_METHOD_FORMAT =
      "Cannot generated a graph because method %s on module %s was malformed";

  static final String NULLABLE_TO_NON_NULLABLE =
      "%s is not nullable, but is being provided by %s";

  static final String CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD =
      "Cannot return null from a non-@Nullable component method";

  static final String CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD =
      "Cannot return null from a non-@Nullable @Provides method";

  private static final Pattern COMMON_PACKAGE_PATTERN = Pattern.compile(
      "(?:^|[^.a-z_])"
      + "((?:"
      + "java[.]lang"
      + "|java[.]util"
      + "|javax[.]inject"
      + "|dagger"
      + "|com[.]google[.]common[.]base"
      + "|com[.]google[.]common[.]collect"
      + ")[.])"
      + "[A-Z]");

  static String stripCommonTypePrefixes(String type) {
    type = type.replace(Provides.Type.class.getCanonicalName() + ".", "");

    Matcher matcher = COMMON_PACKAGE_PATTERN.matcher(type);
    StringBuilder result = new StringBuilder();
    int index = 0;
    while (matcher.find()) {
      result.append(type.subSequence(index, matcher.start(1)));
      index = matcher.end(1);
    }
    result.append(type.subSequence(index, type.length()));
    return result.toString();
  }

  static String format(AnnotationMirror annotation) {
    return stripCommonTypePrefixes(annotation.toString());
  }

  private ErrorMessages() {}
}
