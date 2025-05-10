package machinum.processor.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import machinum.processor.core.models.GemmaJsonSupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.github.victools.jsonschema.generator.OptionPreset.PLAIN_JSON;
import static com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_2020_12;

public interface JsonSupport extends GemmaJsonSupport {

    /**
     * Scans the specified class for fields annotated with {@link JsonDescription}
     * and returns a function that provides the description for target fields.
     *
     * @param clazz the class to scan
     * @return a function that maps {@link MemberScope} to field descriptions
     */
    static Function<MemberScope, String> createDescriptionResolver(Class<?> clazz) {
        Map<String, String> fieldDescriptions = new HashMap<>();

        // Traverse the class hierarchy to include fields from superclasses
        for (Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()) {
            for (Field field : currentClass.getDeclaredFields()) {
                JsonDescription description = field.getAnnotation(JsonDescription.class);
                if (description != null) {
                    fieldDescriptions.put(field.getName(), description.value());
                }
            }
        }

        return memberScope -> fieldDescriptions.get(memberScope.getName());
    }

    /**
     * Scans the specified class and its fields recursively for elements annotated with {@link JsonDescription}
     * and returns a function that provides the description for target fields and classes.
     *
     * @param rootClass the root class to scan
     * @return a function that maps {@link MemberScope} to field descriptions
     */
    //TODO Cache descriptionMap
    static Function<MemberScope, String> createComplexDescriptionResolver(Class<?> rootClass) {
        Map<String, String> descriptionMap = new HashMap<>();
        collectDescriptions(rootClass, "", descriptionMap, 0);
        return memberScope -> {
            //TODO Fix bug in com.github.victools.jsonschema duplicate
            if (Objects.nonNull(memberScope.getOverriddenType())) {
                return null;
            }

            String key = memberScope.getDeclaringType().getErasedType().getName() + "." + memberScope.getName();

            return descriptionMap.get(key);
        };
    }

    /**
     * Creates a function to resolve descriptions for types (classes) annotated with {@link JsonDescription}.
     *
     * @param rootClass the root class to scan
     * @return a function that maps {@link TypeScope} to class descriptions
     */
    //TODO Cache descriptionMap
    static Function<TypeScope, String> createTypeDescriptionResolver(Class<?> rootClass) {
        Map<String, String> descriptionMap = new HashMap<>();
        collectDescriptions(rootClass, "", descriptionMap, 0);
        return typeScope -> descriptionMap.get(typeScope.getType().getErasedType().getName());
    }

    /**
     * Recursively collects descriptions from the specified class and its fields.
     *
     * @param clazz          the class to scan
     * @param parentPath     the parent path to prepend to field names
     * @param descriptionMap the map to store descriptions
     */
    private static void collectDescriptions(Class<?> clazz, String parentPath, Map<String, String> descriptionMap, int level) {
        if (clazz == null || clazz.equals(Object.class) || level >= 5) {
            return;
        }

        // Collect class-level description
        JsonDescription classDescription = clazz.getAnnotation(JsonDescription.class);
        if (classDescription != null) {
            String classKey = clazz.getName();
            descriptionMap.put(classKey, classDescription.value());
        }

        // Traverse the class hierarchy to include fields from superclasses
        for (Field field : clazz.getDeclaredFields()) {
            String fieldPath = parentPath.isEmpty() ? field.getName() : parentPath + "." + field.getName();
            JsonDescription fieldDescription = field.getAnnotation(JsonDescription.class);
            if (fieldDescription != null) {
                String fieldKey = clazz.getName() + "." + fieldPath;
                descriptionMap.put(fieldKey, fieldDescription.value());

                if (level > 0) {
                    descriptionMap.put(clazz.getName() + "." + field.getName(), fieldDescription.value());
                }
            }

            if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
                for (Type type : parameterizedType.getActualTypeArguments()) {
                    collectDescriptions((Class<?>) type, fieldPath, descriptionMap, level + 1);
                }
            }

            // Recursively collect descriptions from nested fields
            collectDescriptions(field.getType(), fieldPath, descriptionMap, level + 1);
        }

        // Recursively collect descriptions from superclass
        collectDescriptions(clazz.getSuperclass(), parentPath, descriptionMap, level + 1);
    }

    /**
     * Determines whether a field should be ignored based on the presence of the {@link SchemaIgnore} annotation.
     *
     * @param member the member scope representing the field
     * @return true if the field should be ignored; false otherwise
     */
    private static boolean isFieldIgnored(MemberScope<?, ?> member) {
        return member.getAnnotationConsideringFieldAndGetter(SchemaIgnore.class) != null;
    }

    default String parse(String text) {
        if (getChatModel().contains("gemma2")) {
            return gemmaParse(text);
        } else if (text.contains("```json")) {
            return parseJsonFromMarkdown(text);
        }

        return text;
    }

    default String generateSchema(TypeReference<?> typeRef) {
        return generateSchema(typeRef, null, null);
    }

    default String generateSchema(TypeReference<?> typeRef,
                                  Function<MemberScope, String> fieldsDescriptionResolver,
                                  Function<TypeScope, String> typesDescriptionResolver) {
        JacksonModule jacksonModule = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);
        JakartaValidationModule module = new JakartaValidationModule();
        Module ignoreModule = configBuilder -> configBuilder.forFields().withIgnoreCheck(JsonSupport::isFieldIgnored);
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(DRAFT_2020_12, PLAIN_JSON)
                .with(jacksonModule)
                .with(module)
                .with(ignoreModule)
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);

        if (Objects.nonNull(fieldsDescriptionResolver)) {
            configBuilder.forFields()
                    .withDescriptionResolver(fieldsDescriptionResolver::apply);
        }

        if (Objects.nonNull(typesDescriptionResolver)) {
            configBuilder.forTypesInGeneral()
                    .withDescriptionResolver(typesDescriptionResolver::apply);
        }

        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);
        JsonNode jsonNode = generator.generateSchema(typeRef.getType());
        ObjectWriter objectWriter = new ObjectMapper().writer(new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter().withLinefeed(System.lineSeparator())));
        try {
            return objectWriter.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not pretty print json schema for " + typeRef, e);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD})
    @interface JsonDescription {

        String value();

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface SchemaIgnore {

    }

}
