package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.producers.ProductionComponent;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static dagger.internal.codegen.ConfigurationAnnotations.enclosedBuilders;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static dagger.internal.codegen.ConfigurationAnnotations.getTransitiveModules;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.VOID;

final class ComponentValidator {
  private final Elements elements;
  private final Types types;
  private final ModuleValidator moduleValidator;
  private final ComponentValidator subcomponentValidator;
  private final BuilderValidator subcomponentBuilderValidator;

  private ComponentValidator(Elements elements,
      Types types,
      ModuleValidator moduleValidator,
      BuilderValidator subcomponentBuilderValidator) {
    this.elements = elements;
    this.types = types;
    this.moduleValidator = moduleValidator;
    this.subcomponentValidator = this;
    this.subcomponentBuilderValidator = subcomponentBuilderValidator;
  }

  private ComponentValidator(Elements elements,
      Types types,
      ModuleValidator moduleValidator,
      ComponentValidator subcomponentValidator,
      BuilderValidator subcomponentBuilderValidator) {
    this.elements = elements;
    this.types = types;
    this.moduleValidator = moduleValidator;
    this.subcomponentValidator = subcomponentValidator;
    this.subcomponentBuilderValidator = subcomponentBuilderValidator;
  }

  static ComponentValidator createForComponent(Elements elements,
      Types types,
      ModuleValidator moduleValidator,
      ComponentValidator subcomponentValidator,
      BuilderValidator subcomponentBuilderValidator) {
    return new ComponentValidator(
        elements, types, moduleValidator, subcomponentValidator, subcomponentBuilderValidator);
  }

  static ComponentValidator createForSubcomponent(Elements elements,
      Types types,
      ModuleValidator moduleValidator,
      BuilderValidator subcomponentBuilderValidator) {
    return new ComponentValidator(elements, types, moduleValidator, subcomponentBuilderValidator);
  }

  @AutoValue
  static abstract class ComponentValidationReport {
    abstract Set<Element> referencedSubcomponents();
    abstract ValidationReport<TypeElement> report();
  }

  public ComponentValidationReport validate(final TypeElement subject,
      Set<? extends Element> validatedSubcomponents,
      Set<? extends Element> validatedSubcomponentBuilders) {
    ValidationReport.Builder<TypeElement> builder = ValidationReport.about(subject);

    ComponentDescriptor.Kind componentKind =
        ComponentDescriptor.Kind.forAnnotatedElement(subject).get();

    if (!subject.getKind().equals(INTERFACE)
        && !(subject.getKind().equals(CLASS) && subject.getModifiers().contains(ABSTRACT))) {
      builder.addError(
          String.format(
              "@%s may only be applied to an interface or abstract class",
              componentKind.annotationType().getSimpleName()),
          subject);
    }

    ImmutableList<DeclaredType> builders =
        enclosedBuilders(subject, componentKind.builderAnnotationType());
    if (builders.size() > 1) {
      builder.addError(
          String.format(ErrorMessages.builderMsgsFor(componentKind).moreThanOne(), builders),
          subject);
    }

    DeclaredType subjectType = MoreTypes.asDeclared(subject.asType());

    List<? extends Element> members = elements.getAllMembers(subject);
    Multimap<Element, ExecutableElement> referencedSubcomponents = LinkedHashMultimap.create();
    for (ExecutableElement method : ElementFilter.methodsIn(members)) {
      if (method.getModifiers().contains(ABSTRACT)) {
        ExecutableType resolvedMethod =
            MoreTypes.asExecutable(types.asMemberOf(subjectType, method));
        List<? extends TypeMirror> parameterTypes = resolvedMethod.getParameterTypes();
        List<? extends VariableElement> parameters = method.getParameters();
        TypeMirror returnType = resolvedMethod.getReturnType();

        Optional<AnnotationMirror> subcomponentAnnotation =
            checkForAnnotations(
                returnType,
                FluentIterable.from(componentKind.subcomponentKinds())
                    .transform(ComponentDescriptor.Kind.toAnnotationType())
                    .toSet());
        Optional<AnnotationMirror> subcomponentBuilderAnnotation =
            checkForAnnotations(
                returnType,
                FluentIterable.from(componentKind.subcomponentKinds())
                    .transform(ComponentDescriptor.Kind.toBuilderAnnotationType())
                    .toSet());
        if (subcomponentAnnotation.isPresent()) {
          referencedSubcomponents.put(MoreTypes.asElement(returnType), method);
          validateSubcomponentMethod(
              builder,
              ComponentDescriptor.Kind.forAnnotatedElement(MoreTypes.asTypeElement(returnType))
                  .get(),
              method,
              parameters,
              parameterTypes,
              returnType,
              subcomponentAnnotation);
        } else if (subcomponentBuilderAnnotation.isPresent()) {
          referencedSubcomponents.put(MoreTypes.asElement(returnType).getEnclosingElement(),
              method);
          validateSubcomponentBuilderMethod(builder,
              method,
              parameters,
              returnType,
              validatedSubcomponentBuilders);
        } else {
          switch (parameters.size()) {
            case 0:
              break;
            case 1:
              TypeMirror onlyParameter = Iterables.getOnlyElement(parameterTypes);
              if (!(returnType.getKind().equals(VOID)
                  || types.isSameType(returnType, onlyParameter))) {
                builder.addError(
                    "Members injection methods may only return the injected type or void.", method);
              }
              break;
            default:
              builder.addError(
                  "This method isn't a valid provision method, members injection method or "
                      + "subcomponent factory method. Dagger cannot implement this method",
                  method);
              break;
          }
        }
      }
    }

    for (Map.Entry<Element, Collection<ExecutableElement>> entry :
        referencedSubcomponents.asMap().entrySet()) {
      if (entry.getValue().size() > 1) {
        builder.addError(
            String.format(
                ErrorMessages.SubcomponentBuilderMessages.INSTANCE.moreThanOneRefToSubcomponent(),
                entry.getKey(),
                entry.getValue()),
            subject);
      }
    }

    AnnotationMirror componentMirror =
        getAnnotationMirror(subject, componentKind.annotationType()).get();
    ImmutableList<TypeMirror> moduleTypes = getComponentModules(componentMirror);
    moduleValidator.validateReferencedModules(
        subject, builder, moduleTypes, componentKind.moduleKinds());

    ImmutableSet.Builder<Element> allSubcomponents =
        ImmutableSet.<Element>builder().addAll(referencedSubcomponents.keySet());
    for (Element subcomponent :
        Sets.difference(referencedSubcomponents.keySet(), validatedSubcomponents)) {
      ComponentValidationReport subreport = subcomponentValidator.validate(
          MoreElements.asType(subcomponent), validatedSubcomponents, validatedSubcomponentBuilders);
      builder.addItems(subreport.report().items());
      allSubcomponents.addAll(subreport.referencedSubcomponents());
    }

    return new AutoValue_ComponentValidator_ComponentValidationReport(allSubcomponents.build(),
        builder.build());
  }

  private void validateSubcomponentMethod(
      final ValidationReport.Builder<TypeElement> builder,
      final ComponentDescriptor.Kind subcomponentKind,
      ExecutableElement method,
      List<? extends VariableElement> parameters,
      List<? extends TypeMirror> parameterTypes,
      TypeMirror returnType,
      Optional<AnnotationMirror> subcomponentAnnotation) {
    ImmutableSet<TypeElement> moduleTypes =
        MoreTypes.asTypeElements(getComponentModules(subcomponentAnnotation.get()));

    @SuppressWarnings("deprecation")
    ImmutableSet<TypeElement> transitiveModules =
        getTransitiveModules(types, elements, moduleTypes);

    Set<TypeElement> variableTypes = Sets.newHashSet();

    for (int i = 0; i < parameterTypes.size(); i++) {
      VariableElement parameter = parameters.get(i);
      TypeMirror parameterType = parameterTypes.get(i);
      Optional<TypeElement> moduleType =
          parameterType.accept(
              new SimpleTypeVisitor6<Optional<TypeElement>, Void>() {
                @Override
                protected Optional<TypeElement> defaultAction(TypeMirror e, Void p) {
                  return Optional.absent();
                }

                @Override
                public Optional<TypeElement> visitDeclared(DeclaredType t, Void p) {
                  for (ModuleDescriptor.Kind moduleKind : subcomponentKind.moduleKinds()) {
                    if (MoreElements.isAnnotationPresent(
                        t.asElement(), moduleKind.moduleAnnotation())) {
                      return Optional.of(MoreTypes.asTypeElement(t));
                    }
                  }
                  return Optional.absent();
                }
              },
              null);
      if (moduleType.isPresent()) {
        if (variableTypes.contains(moduleType.get())) {
          builder.addError(
              String.format(
                  "A module may only occur once an an argument in a Subcomponent factory "
                      + "method, but %s was already passed.",
                  moduleType.get().getQualifiedName()),
              parameter);
        }
        if (!transitiveModules.contains(moduleType.get())) {
          builder.addError(
              String.format(
                  "%s is present as an argument to the %s factory method, but is not one of the"
                      + " modules used to implement the subcomponent.",
                  moduleType.get().getQualifiedName(),
                  MoreTypes.asTypeElement(returnType).getQualifiedName()),
              method);
        }
        variableTypes.add(moduleType.get());
      } else {
        builder.addError(
            String.format(
                "Subcomponent factory methods may only accept modules, but %s is not.",
                parameterType),
            parameter);
      }
    }
  }

  private void validateSubcomponentBuilderMethod(ValidationReport.Builder<TypeElement> builder,
      ExecutableElement method, List<? extends VariableElement> parameters, TypeMirror returnType,
      Set<? extends Element> validatedSubcomponentBuilders) {

    if (!parameters.isEmpty()) {
      builder.addError(
          ErrorMessages.SubcomponentBuilderMessages.INSTANCE.builderMethodRequiresNoArgs(), method);
    }

    TypeElement builderElement = MoreTypes.asTypeElement(returnType);
    if (!validatedSubcomponentBuilders.contains(builderElement)) {
      builder.addItems(subcomponentBuilderValidator.validate(builderElement).items());
    }
  }

  private Optional<AnnotationMirror> checkForAnnotations(
      TypeMirror type, final Set<Class<? extends Annotation>> annotations) {
    return type.accept(
        new SimpleTypeVisitor6<Optional<AnnotationMirror>, Void>() {
          @Override
          protected Optional<AnnotationMirror> defaultAction(TypeMirror e, Void p) {
            return Optional.absent();
          }

          @Override
          public Optional<AnnotationMirror> visitDeclared(DeclaredType t, Void p) {
            for (Class<? extends Annotation> annotation : annotations) {
              Optional<AnnotationMirror> mirror =
                  MoreElements.getAnnotationMirror(t.asElement(), annotation);
              if (mirror.isPresent()) {
                return mirror;
              }
            }
            return Optional.absent();
          }
        },
        null);
  }
}
