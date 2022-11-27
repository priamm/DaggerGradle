package dagger.internal.codegen;

import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Deque;

class MissingBindingSuggestions {

  static ImmutableList<String> forKey(BindingGraph topLevelGraph, BindingKey key) {
    ImmutableList.Builder<String> resolutions = new ImmutableList.Builder<>();
    Deque<BindingGraph> graphsToTry = new ArrayDeque<>();

    graphsToTry.add(topLevelGraph);
    do {
      BindingGraph graph = graphsToTry.removeLast();
      ResolvedBindings bindings = graph.resolvedBindings().get(key);
      if ((bindings == null) || bindings.bindings().isEmpty()) {
        graphsToTry.addAll(graph.subgraphs().values());
      } else {
        resolutions.add("A binding with matching key exists in component: "
            + graph.componentDescriptor().componentDefinitionType().getQualifiedName());
      }
    } while (!graphsToTry.isEmpty());

    return resolutions.build();
  }

  private MissingBindingSuggestions() {}
}
