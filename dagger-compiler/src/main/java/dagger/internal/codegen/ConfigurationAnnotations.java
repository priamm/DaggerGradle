package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.Module;
import dagger.producers.ProducerModule;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.Types;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.common.base.Preconditions.checkNotNull;

final class ConfigurationAnnotations {

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

  static Optional<DeclaredType> getNullableType(Element element) {
    List<? extends AnnotationMirror> mirrors = element.getAnnotationMirrors();
    for (AnnotationMirror mirror : mirrors) {
      if (mirror.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable")) {
        return Optional.of(mirror.getAnnotationType());
      }
    }
    return Optional.absent();
  }

  static ImmutableList<TypeMirror> convertClassArrayToListOfTypes(
      AnnotationMirror annotationMirror, String elementName) {
    return TO_LIST_OF_TYPES.visit(getAnnotationValue(annotationMirror, elementName), elementName);
  }

  private static final AnnotationValueVisitor<ImmutableList<TypeMirror>, String> TO_LIST_OF_TYPES =
      new SimpleAnnotationValueVisitor6<ImmutableList<TypeMirror>, String>() {
        @Override
        public ImmutableList<TypeMirror> visitArray(
            List<? extends AnnotationValue> vals, String elementName) {
          return FluentIterable.from(vals)
              .transform(
                  new Function<AnnotationValue, TypeMirror>() {
                    @Override
                    public TypeMirror apply(AnnotationValue typeValue) {
                      return TO_TYPE.visit(typeValue);
                    }
                  })
              .toList();
        }

        @Override
        protected ImmutableList<TypeMirror> defaultAction(Object o, String elementName) {
          throw new IllegalArgumentException(elementName + " is not an array: " + o);
        }
      };

  private static final AnnotationValueVisitor<TypeMirror, Void> TO_TYPE =
      new SimpleAnnotationValueVisitor6<TypeMirror, Void>() {
        @Override
        public TypeMirror visitType(TypeMirror t, Void p) {
          return t;
        }

        @Override
        protected TypeMirror defaultAction(Object o, Void p) {
          throw new TypeNotPresentException(o.toString(), null);
        }
      };

  @Deprecated
  static ImmutableSet<TypeElement> getTransitiveModules(
      Types types, Elements elements, Iterable<TypeElement> seedModules) {
    TypeMirror objectType = elements.getTypeElement(Object.class.getCanonicalName()).asType();
    Queue<TypeElement> moduleQueue = new ArrayDeque<>();
    Iterables.addAll(moduleQueue, seedModules);
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

  static ImmutableList<DeclaredType> enclosedBuilders(TypeElement typeElement,
      final Class<? extends Annotation> annotation) {
    final ImmutableList.Builder<DeclaredType> builders = ImmutableList.builder();
    for (TypeElement element : ElementFilter.typesIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, annotation)) {
        builders.add(MoreTypes.asDeclared(element.asType()));
      }
    }
    return builders.build();
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
