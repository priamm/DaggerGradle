package dagger.internal.codegen;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.MapKey;
import dagger.Module;
import dagger.Subcomponent;
import dagger.producers.ProducerModule;
import dagger.producers.ProductionComponent;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.common.base.Preconditions.checkNotNull;

final class ConfigurationAnnotations {

  static boolean isComponent(TypeElement componentDefinitionType) {
    return MoreElements.isAnnotationPresent(componentDefinitionType, Component.class)
        || MoreElements.isAnnotationPresent(componentDefinitionType, ProductionComponent.class);
  }

  private static final String MODULES_ATTRIBUTE = "modules";

  static ImmutableList<TypeMirror> getComponentModules(AnnotationMirror componentAnnotation) {
    checkNotNull(componentAnnotation);
    return convertClassArrayToListOfTypes(componentAnnotation, MODULES_ATTRIBUTE);
  }

  private static final String DEPENDENCIES_ATTRIBUTE = "dependencies";

  static ImmutableList<TypeMirror> getComponentDependencies(AnnotationMirror componentAnnotation) {
    checkNotNull(componentAnnotation);
    return convertClassArrayToListOfTypes(componentAnnotation, DEPENDENCIES_ATTRIBUTE);
  }

  private static final String INCLUDES_ATTRIBUTE = "includes";

  static ImmutableList<TypeMirror> getModuleIncludes(AnnotationMirror moduleAnnotation) {
    checkNotNull(moduleAnnotation);
    return convertClassArrayToListOfTypes(moduleAnnotation, INCLUDES_ATTRIBUTE);
  }

  private static final String INJECTS_ATTRIBUTE = "injects";

  static ImmutableList<TypeMirror> getModuleInjects(AnnotationMirror moduleAnnotation) {
    checkNotNull(moduleAnnotation);
    return convertClassArrayToListOfTypes(moduleAnnotation, INJECTS_ATTRIBUTE);
  }

  static ImmutableSet<? extends AnnotationMirror> getMapKeys(Element element) {
    return AnnotationMirrors.getAnnotatedAnnotations(element, MapKey.class);
  }

  static Optional<DeclaredType> getNullableType(Element element) {
    List<? extends AnnotationMirror> mirrors = element.getAnnotationMirrors();
    for (AnnotationMirror mirror : mirrors) {
      if (mirror.getAnnotationType().asElement().getSimpleName().toString().equals("Nullable")) {
        return Optional.of(mirror.getAnnotationType());
      }
    }
    return Optional.absent();
  }

  static ImmutableList<TypeMirror> convertClassArrayToListOfTypes(
      AnnotationMirror annotationMirror, final String elementName) {
    @SuppressWarnings("unchecked")
    List<? extends AnnotationValue> listValue = (List<? extends AnnotationValue>)
        getAnnotationValue(annotationMirror, elementName).getValue();
    return FluentIterable.from(listValue).transform(new Function<AnnotationValue, TypeMirror>() {
      @Override public TypeMirror apply(AnnotationValue typeValue) {
        return (TypeMirror) typeValue.getValue();
      }
    }).toList();
  }

  static ImmutableSet<TypeElement> getTransitiveModules(
      Types types, Elements elements, Iterable<TypeElement> seedModules) {
    TypeMirror objectType = elements.getTypeElement(Object.class.getCanonicalName()).asType();
    Queue<TypeElement> moduleQueue = Queues.newArrayDeque(seedModules);
    Set<TypeElement> moduleElements = Sets.newLinkedHashSet();
    for (TypeElement moduleElement = moduleQueue.poll();
        moduleElement != null;
        moduleElement = moduleQueue.poll()) {
      Optional<AnnotationMirror> moduleMirror = getAnnotationMirror(moduleElement, Module.class)
          .or(getAnnotationMirror(moduleElement, ProducerModule.class));
      if (moduleMirror.isPresent()) {
        ImmutableSet.Builder<TypeElement> moduleDependenciesBuilder = ImmutableSet.builder();
        moduleDependenciesBuilder.addAll(
            MoreTypes.asTypeElements(getModuleIncludes(moduleMirror.get())));
        addIncludesFromSuperclasses(types, moduleElement, moduleDependenciesBuilder, objectType);
        ImmutableSet<TypeElement> moduleDependencies = moduleDependenciesBuilder.build();
        moduleElements.add(moduleElement);
        for (TypeElement dependencyType : moduleDependencies) {
          if (!moduleElements.contains(dependencyType)) {
            moduleQueue.add(dependencyType);
          }
        }
      }
    }
    return ImmutableSet.copyOf(moduleElements);
  }

  static boolean isSubcomponentType(TypeMirror type) {
    return type.accept(new SubcomponentDetector(), null).isPresent();
  }

  private static final class SubcomponentDetector
      extends SimpleTypeVisitor6<Optional<AnnotationMirror>, Void> {
    @Override
    protected Optional<AnnotationMirror> defaultAction(TypeMirror e, Void p) {
      return Optional.absent();
    }

    @Override
    public Optional<AnnotationMirror> visitDeclared(DeclaredType t, Void p) {
      return MoreElements.getAnnotationMirror(t.asElement(), Subcomponent.class);
    }
  }

  private static void addIncludesFromSuperclasses(Types types, TypeElement element,
      ImmutableSet.Builder<TypeElement> builder, TypeMirror objectType) {
    TypeMirror superclass = element.getSuperclass();
    while (!types.isSameType(objectType, superclass)
        && superclass.getKind().equals(TypeKind.DECLARED)) {
      element = MoreElements.asType(types.asElement(superclass));
      Optional<AnnotationMirror> moduleMirror = getAnnotationMirror(element, Module.class)
          .or(getAnnotationMirror(element, ProducerModule.class));
      if (moduleMirror.isPresent()) {
        builder.addAll(MoreTypes.asTypeElements(getModuleIncludes(moduleMirror.get())));
      }
      superclass = element.getSuperclass();
    }
  }

  private ConfigurationAnnotations() {}
}
