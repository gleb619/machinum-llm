package machinum.processor.core;

import machinum.model.ObjectName;
import machinum.util.TextSearchHelperUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface ObjectNameSupport {

    default String findReferences(List<ObjectName> names, String name) {
        var nameMap = names.stream()
                .collect(Collectors.toMap(ObjectName::getName, Function.identity()));

        var data = nameMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> """
                        %s
                        %s
                        """.formatted(entry.getValue().getName(), entry.getValue().getDescription()), (f, s) -> f));

        var searchResult = TextSearchHelperUtil.search(data, name);

        var objects = nameMap.entrySet()
                .stream()
                .filter(entry -> searchResult.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        String result = objects.stream()
                .map(ObjectName::toString)
                .collect(Collectors.joining("\n---\n"));

        if (result.isBlank()) {
            return "No references";
        }

        return result;
    }

}
