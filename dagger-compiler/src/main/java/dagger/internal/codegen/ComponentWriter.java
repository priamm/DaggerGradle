package dagger.internal.codegen;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

final class ComponentWriter extends AbstractComponentWriter {

  ComponentWriter(
      Types types,
      Elements elements,
      Key.Factory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph) {
    super(
        types,
        elements,
        keyFactory,
        compilerOptions,
        name,
        graph,
        new UniqueSubcomponentNamesGenerator(graph).generate());
  }

  private static class UniqueSubcomponentNamesGenerator {

    private static final Splitter QUALIFIED_NAME_SPLITTER = Splitter.on('.');
    private static final Joiner QUALIFIED_NAME_JOINER = Joiner.on('_');

    private final BindingGraph graph;
    private final ImmutableListMultimap<String, ComponentDescriptor>
        componentDescriptorsBySimpleName;
    private final ImmutableListMultimap<ComponentDescriptor, String> componentQualifiedNamePieces;

    private UniqueSubcomponentNamesGenerator(BindingGraph graph) {
      this.graph = graph;
      componentDescriptorsBySimpleName =
          Multimaps.index(
              graph.componentDescriptors(),
              new Function<ComponentDescriptor, String>() {
                @Override
                public String apply(ComponentDescriptor componentDescriptor) {
                  return componentDescriptor.componentDefinitionType().getSimpleName().toString();
                }
              });
      componentQualifiedNamePieces = qualifiedNames(graph.componentDescriptors());
    }

    private ImmutableBiMap<ComponentDescriptor, String> generate() {
      Map<ComponentDescriptor, String> subcomponentImplSimpleNames = new LinkedHashMap<>();
      for (Entry<String, Collection<ComponentDescriptor>> componentEntry :
          componentDescriptorsBySimpleName.asMap().entrySet()) {
        Collection<ComponentDescriptor> components = componentEntry.getValue();
        subcomponentImplSimpleNames.putAll(disambiguateConflictingSimpleNames(components));
      }
      subcomponentImplSimpleNames.remove(graph.componentDescriptor());
      return ImmutableBiMap.copyOf(subcomponentImplSimpleNames);
    }

    private ImmutableBiMap<ComponentDescriptor, String> disambiguateConflictingSimpleNames(
        Collection<ComponentDescriptor> components) {
      Map<String, ComponentDescriptor> generatedSimpleNames = new LinkedHashMap<>();
      for (int levels = 0; generatedSimpleNames.size() != components.size(); levels++) {
        generatedSimpleNames.clear();
        for (ComponentDescriptor component : components) {
          List<String> pieces = componentQualifiedNamePieces.get(component);
          String simpleName =
              QUALIFIED_NAME_JOINER.join(
                  pieces.subList(Math.max(0, pieces.size() - levels - 1), pieces.size()));
          ComponentDescriptor conflict = generatedSimpleNames.put(simpleName, component);
          if (conflict != null) {
            break;
          }
        }
      }
      return ImmutableBiMap.copyOf(generatedSimpleNames).inverse();
    }

    private static ImmutableListMultimap<ComponentDescriptor, String> qualifiedNames(
        Iterable<ComponentDescriptor> componentDescriptors) {
      ImmutableListMultimap.Builder<ComponentDescriptor, String> builder =
          ImmutableListMultimap.builder();
      for (ComponentDescriptor component : componentDescriptors) {
        Name qualifiedName = component.componentDefinitionType().getQualifiedName();
        builder.putAll(component, QUALIFIED_NAME_SPLITTER.split(qualifiedName));
      }
      return builder.build();
    }
  }

  @Override
  protected TypeSpec.Builder createComponentClass() {
    TypeSpec.Builder component = classBuilder(name.simpleName()).addModifiers(PUBLIC, FINAL);
    addSupertype(component, componentDefinitionType());
    return component;
  }

  @Override
  protected ClassName builderName() {
    return name.nestedClass("Builder");
  }

  @Override
  protected TypeSpec.Builder createBuilder(String builderSimpleName) {
    TypeSpec.Builder builder = classBuilder(builderSimpleName).addModifiers(STATIC);

    MethodSpec builderFactoryMethod =
        methodBuilder("builder")
            .addModifiers(PUBLIC, STATIC)
            .returns(
                graph.componentDescriptor().builderSpec().isPresent()
                    ? ClassName.get(
                        graph.componentDescriptor().builderSpec().get().builderDefinitionType())
                    : builderName.get())
            .addStatement("return new $T()", builderName.get())
            .build();
    component.addMethod(builderFactoryMethod);
    return builder;
  }

  @Override
  protected void addBuilderClass(TypeSpec builder) {
    component.addType(builder);
  }

  @Override
  protected void addFactoryMethods() {
    if (canInstantiateAllRequirements()) {
      CharSequence buildMethodName =
          graph.componentDescriptor().builderSpec().isPresent()
              ? graph.componentDescriptor().builderSpec().get().buildMethod().getSimpleName()
              : "build";
      component.addMethod(
          methodBuilder("create")
              .returns(componentDefinitionTypeName())
              .addModifiers(PUBLIC, STATIC)
              .addStatement("return builder().$L()", buildMethodName)
              .build());
    }
  }

  private boolean canInstantiateAllRequirements() {
    return Iterables.all(
        graph.componentRequirements(),
        new Predicate<TypeElement>() {
          @Override
          public boolean apply(TypeElement dependency) {
            return componentCanMakeNewInstances(dependency);
          }
        });
  }
}
