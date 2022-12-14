package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import dagger.Multibindings;
import dagger.Provides;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

final class ErrorMessages {

  static final String INDENT = "    ";
  static final int DUPLICATE_SIZE_LIMIT = 10;

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
  
  static final String STATIC_INJECT_FIELD =
      "Dagger does not support injection into static fields";

  static final String PRIVATE_INJECT_METHOD =
      "Dagger does not support injection into private methods";
  
  static final String STATIC_INJECT_METHOD =
      "Dagger does not support injection into static methods";

  static final String INJECT_INTO_PRIVATE_CLASS =
      "Dagger does not support injection into private classes";

  static final String CANNOT_INJECT_WILDCARD_TYPE =
      "Dagger does not support injecting Provider<T>, Lazy<T> or Produced<T> when T is a wildcard "
          + "type such as <%s>.";

  static final String DUPLICATE_BINDINGS_FOR_KEY_FORMAT =
      "%s is bound multiple times:";

  static String duplicateMapKeysError(String key) {
    return "The same map key is bound more than once for " + key;
  }

  static String inconsistentMapKeyAnnotationsError(String key) {
    return key + " uses more than one @MapKey annotation type";
  }

  static final String PROVIDES_METHOD_RETURN_TYPE =
      "@Provides methods must either return a primitive, an array, a type variable, or a declared"
          + " type.";

  static final String PROVIDES_METHOD_THROWS =
      "@Provides methods may only throw unchecked exceptions";

  static final String PRODUCES_METHOD_RETURN_TYPE =
      "@Produces methods must either return a primitive, an array, a type variable, or a declared"
          + " type, or a ListenableFuture of one of those types.";

  static final String PRODUCES_METHOD_RAW_FUTURE =
      "@Produces methods cannot return a raw ListenableFuture.";

  static final String BINDING_METHOD_SET_VALUES_RAW_SET =
      "@%s methods of type set values cannot return a raw Set";

  static final String PROVIDES_METHOD_SET_VALUES_RETURN_SET =
      "@Provides methods of type set values must return a Set";

  static final String PRODUCES_METHOD_SET_VALUES_RETURN_SET =
      "@Produces methods of type set values must return a Set or ListenableFuture of Set";

  static final String PRODUCES_METHOD_THROWS =
      "@Produces methods may only throw unchecked exceptions or exceptions subclassing Exception";

  static final String BINDING_METHOD_MUST_RETURN_A_VALUE =
      "@%s methods must return a value (not void).";

  static final String BINDING_METHOD_ABSTRACT = "@%s methods cannot be abstract";

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
      "%s is listed as a module, but is an abstract class or interface";

  static final String REFERENCED_MODULE_NOT_ANNOTATED =
      "%s is listed as a module, but is not annotated with %s";

  static final String REFERENCED_MODULE_MUST_NOT_HAVE_TYPE_PARAMS =
      "%s is listed as a module, but has type parameters";

  static final String PROVIDES_METHOD_OVERRIDES_ANOTHER =
      "@%s methods may not override another method. Overrides: %s";

  static final String METHOD_OVERRIDES_PROVIDES_METHOD =
      "@%s methods may not be overridden in modules. Overrides: %s";

  static final String PROVIDES_OR_PRODUCES_METHOD_MULTIPLE_QUALIFIERS =
      "Cannot use more than one @Qualifier on a @Provides or @Produces method";

  static final String MAPKEY_WITHOUT_MEMBERS =
      "Map key annotations must have members";

  static final String UNWRAPPED_MAP_KEY_WITH_TOO_MANY_MEMBERS=
      "Map key annotations with unwrapped values must have exactly one member";

  static final String UNWRAPPED_MAP_KEY_WITH_ARRAY_MEMBER =
      "Map key annotations with unwrapped values cannot use arrays";

  static final String MULTIPLE_BINDING_TYPES_FOR_KEY_FORMAT =
      "%s has incompatible bindings or declarations:\n";

  static final String PROVIDER_ENTRY_POINT_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT =
      "%s is a provision entry-point, which cannot depend on a production.";

  static final String PROVIDER_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT =
      "%s is a provision, which cannot depend on a production.";
  
  static final String DEPENDS_ON_PRODUCTION_EXECUTOR_FORMAT =
      "%s may not depend on the production executor.";

  static final String REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT =
      "%s cannot be provided without an @Inject constructor or from an @Provides-annotated method.";

  static final String REQUIRES_PROVIDER_FORMAT =
      "%s cannot be provided without an @Provides-annotated method.";

  static final String REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_OR_PRODUCER_FORMAT =
      "%s cannot be provided without an @Inject constructor or from an @Provides- or "
      + "@Produces-annotated method.";

  static final String REQUIRES_PROVIDER_OR_PRODUCER_FORMAT =
      "%s cannot be provided without an @Provides- or @Produces-annotated method.";

  private static final String PROVISION_MAY_NOT_DEPEND_ON_PRODUCER_TYPE_FORMAT =
      "%s may only be injected in @Produces methods.";

  static String provisionMayNotDependOnProducerType(TypeMirror type) {
    return String.format(
        PROVISION_MAY_NOT_DEPEND_ON_PRODUCER_TYPE_FORMAT,
        MoreTypes.asTypeElement(type).getSimpleName());
  }

  static final String PRODUCTION_COMPONENT_SCOPE =
      "Production components may not declare any @Scope other than @ProductionScope; they are "
          + "automatically scoped with @ProductionScope if no scope is applied.";

  static final String MEMBERS_INJECTION_DOES_NOT_IMPLY_PROVISION =
      "This type supports members injection but cannot be implicitly provided.";

  static final String MEMBERS_INJECTION_WITH_RAW_TYPE =
      "%s has type parameters, cannot members inject the raw type. via:\n%s";

  static final String MEMBERS_INJECTION_WITH_UNBOUNDED_TYPE =
      "Type parameters must be bounded for members injection. %s required by %s, via:\n%s";

  static final String CONTAINS_DEPENDENCY_CYCLE_FORMAT = "%s.%s() contains a dependency cycle:\n%s";

  static final String MALFORMED_MODULE_METHOD_FORMAT =
      "Cannot generated a graph because method %s on module %s was malformed";

  static String nullableToNonNullable(String typeName, String bindingString) {
    return String.format(
            "%s is not nullable, but is being provided by %s",
            typeName,
            bindingString);
  }

  static final String CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD =
      "Cannot return null from a non-@Nullable component method";

  static final String CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD =
      "Cannot return null from a non-@Nullable @Provides method";

  static ComponentBuilderMessages builderMsgsFor(ComponentDescriptor.Kind kind) {
    switch(kind) {
      case COMPONENT:
        return ComponentBuilderMessages.INSTANCE;
      case SUBCOMPONENT:
        return SubcomponentBuilderMessages.INSTANCE;
      case PRODUCTION_COMPONENT:
        return ProductionComponentBuilderMessages.INSTANCE;
      case PRODUCTION_SUBCOMPONENT:
        return ProductionSubcomponentBuilderMessages.INSTANCE;
      default:
        throw new IllegalStateException(kind.toString());
    }
  }

  static class ComponentBuilderMessages {
    static final ComponentBuilderMessages INSTANCE = new ComponentBuilderMessages();

    protected String process(String s) { return s; }

    final String moreThanOne() {
      return process("@Component has more than one @Component.Builder: %s");
    }

    final String cxtorOnlyOneAndNoArgs() {
      return process("@Component.Builder classes must have exactly one constructor,"
          + " and it must not have any parameters");
    }

    final String generics() {
      return process("@Component.Builder types must not have any generic types");
    }

    final String mustBeInComponent() {
      return process("@Component.Builder types must be nested within a @Component");
    }

    final String mustBeClassOrInterface() {
      return process("@Component.Builder types must be abstract classes or interfaces");
    }

    final String isPrivate() {
      return process("@Component.Builder types must not be private");
    }

    final String mustBeStatic() {
      return process("@Component.Builder types must be static");
    }

    final String mustBeAbstract() {
      return process("@Component.Builder types must be abstract");
    }

    final String missingBuildMethod() {
      return process("@Component.Builder types must have exactly one no-args method that "
          + " returns the @Component type");
    }

    final String manyMethodsForType() {
      return process("@Component.Builder types must not have more than one setter method per type,"
          + " but %s is set by %s");
    }

    final String extraSetters() {
      return process(
          "@Component.Builder has setters for modules or components that aren't required: %s");
    }

    final String missingSetters() {
      return process(
          "@Component.Builder is missing setters for required modules or components: %s");
    }

    final String twoBuildMethods() {
      return process("@Component.Builder types must have exactly one zero-arg method, and that"
          + " method must return the @Component type. Already found: %s");
    }

    final String inheritedTwoBuildMethods() {
      return process("@Component.Builder types must have exactly one zero-arg method, and that"
          + " method must return the @Component type. Found %s and %s");
    }

    final String buildMustReturnComponentType() {
      return process(
          "@Component.Builder methods that have no arguments must return the @Component type");
    }

    final String inheritedBuildMustReturnComponentType() {
      return process(
          "@Component.Builder methods that have no arguments must return the @Component type"
          + " Inherited method: %s");
    }

    final String methodsMustTakeOneArg() {
      return process("@Component.Builder methods must not have more than one argument");
    }

    final String inheritedMethodsMustTakeOneArg() {
      return process(
          "@Component.Builder methods must not have more than one argument. Inherited method: %s");
    }

    final String methodsMustReturnVoidOrBuilder() {
      return process("@Component.Builder setter methods must return void, the builder,"
          + " or a supertype of the builder");
    }

    final String inheritedMethodsMustReturnVoidOrBuilder() {
      return process("@Component.Builder setter methods must return void, the builder,"
          + "or a supertype of the builder. Inherited method: %s");
    }

    final String methodsMayNotHaveTypeParameters() {
      return process("@Component.Builder methods must not have type parameters");
    }

    final String inheritedMethodsMayNotHaveTypeParameters() {
      return process(
          "@Component.Builder methods must not have type parameters. Inherited method: %s");
    }
  }

  static final class SubcomponentBuilderMessages extends ComponentBuilderMessages {
    @SuppressWarnings("hiding")
    static final SubcomponentBuilderMessages INSTANCE = new SubcomponentBuilderMessages();

    @Override protected String process(String s) {
      return s.replaceAll("component", "subcomponent").replaceAll("Component", "Subcomponent");
    }

    String builderMethodRequiresNoArgs() {
      return "Methods returning a @Subcomponent.Builder must have no arguments";
    }

    String moreThanOneRefToSubcomponent() {
      return "Only one method can create a given subcomponent. %s is created by: %s";
    }
  }

  static final class ProductionComponentBuilderMessages extends ComponentBuilderMessages {
    @SuppressWarnings("hiding")
    static final ProductionComponentBuilderMessages INSTANCE =
        new ProductionComponentBuilderMessages();

    @Override protected String process(String s) {
      return s.replaceAll("component", "production component")
          .replaceAll("Component", "ProductionComponent");
    }
  }

  static final class ProductionSubcomponentBuilderMessages extends ComponentBuilderMessages {
    @SuppressWarnings("hiding")
    static final ProductionSubcomponentBuilderMessages INSTANCE =
        new ProductionSubcomponentBuilderMessages();

    @Override
    protected String process(String s) {
      return s.replaceAll("component", "production subcomponent")
          .replaceAll("Component", "ProductionSubcomponent");
    }

    String builderMethodRequiresNoArgs() {
      return "Methods returning a @ProductionSubcomponent.Builder must have no arguments";
    }

    String moreThanOneRefToSubcomponent() {
      return "Only one method can create a given production subcomponent. %s is created by: %s";
    }
  }

  static final class MultibindingsMessages {
    static final String MUST_BE_INTERFACE = "@Multibindings can be applied only to interfaces";

    static final String MUST_NOT_HAVE_TYPE_PARAMETERS =
        "@Multibindings types must not have type parameters";

    static final String MUST_BE_IN_MODULE =
        "@Multibindings types must be nested within a @Module or @ProducerModule";

    static final String METHOD_MUST_RETURN_MAP_OR_SET =
        "@Multibindings methods must return Map<K, V> or Set<T>";

    static final String TOO_MANY_QUALIFIERS =
        "Cannot use more than one @Qualifier on a method in an @Multibindings type";

    static String tooManyMethodsForKey(String formattedKey) {
      return String.format(
          "Too many @Multibindings methods for %s", stripCommonTypePrefixes(formattedKey));
    }

    private MultibindingsMessages() {}
  }

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
