package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import static com.google.common.base.Functions.constant;

final class ComponentHierarchyValidator {
  ValidationReport<TypeElement> validate(ComponentDescriptor componentDescriptor) {
    return validateSubcomponentMethods(
        componentDescriptor,
        Maps.toMap(
            componentDescriptor.transitiveModuleTypes(),
            constant(componentDescriptor.componentDefinitionType())));
  }

  private ValidationReport<TypeElement> validateSubcomponentMethods(
      ComponentDescriptor componentDescriptor,
      ImmutableMap<TypeElement, TypeElement> existingModuleToOwners) {
    ValidationReport.Builder<TypeElement> reportBuilder =
        ValidationReport.about(componentDescriptor.componentDefinitionType());
    for (Map.Entry<ComponentMethodDescriptor, ComponentDescriptor> subcomponentEntry :
        componentDescriptor.subcomponents().entrySet()) {
      ComponentMethodDescriptor subcomponentMethodDescriptor = subcomponentEntry.getKey();
      ComponentDescriptor subcomponentDescriptor = subcomponentEntry.getValue();
      switch (subcomponentMethodDescriptor.kind()) {
        case SUBCOMPONENT:
        case PRODUCTION_SUBCOMPONENT:
          for (VariableElement factoryMethodParameter :
              subcomponentMethodDescriptor.methodElement().getParameters()) {
            TypeElement moduleType = MoreTypes.asTypeElement(factoryMethodParameter.asType());
            TypeElement originatingComponent = existingModuleToOwners.get(moduleType);
            if (originatingComponent != null) {
              reportBuilder.addError(
                  String.format(
                      "%s is present in %s. A subcomponent cannot use an instance of a "
                          + "module that differs from its parent.",
                      moduleType.getSimpleName(),
                      originatingComponent.getQualifiedName()),
                  factoryMethodParameter);
            }
          }
          break;
          
        case SUBCOMPONENT_BUILDER:
        case PRODUCTION_SUBCOMPONENT_BUILDER:
          break;
          
        default:
          throw new AssertionError();
      }
      reportBuilder.addSubreport(
          validateSubcomponentMethods(
              subcomponentDescriptor,
              new ImmutableMap.Builder<TypeElement, TypeElement>()
                  .putAll(existingModuleToOwners)
                  .putAll(
                      Maps.toMap(
                          Sets.difference(
                              subcomponentDescriptor.transitiveModuleTypes(),
                              existingModuleToOwners.keySet()),
                          constant(subcomponentDescriptor.componentDefinitionType())))
                  .build()));
    }
    return reportBuilder.build();
  }
}
