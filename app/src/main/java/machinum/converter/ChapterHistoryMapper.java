package machinum.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.patch.Patch;
import machinum.entity.ChapterHistoryEntity;
import machinum.model.ChapterHistory;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class ChapterHistoryMapper implements BaseMapper<ChapterHistoryEntity, ChapterHistory> {

    @Autowired
    ChapterInfoHistoryConverter chapterInfoHistoryConverter;

    @Override
    @Mapping(source = "patch", target = "patch", qualifiedByName = "toPatch")
    public abstract ChapterHistoryEntity toEntity(ChapterHistory value);

    @Override
    @Mapping(source = "patch", target = "patch", qualifiedByName = "fromPatch")
    public abstract ChapterHistory toDto(ChapterHistoryEntity value);

    @Named("fromPatch")
    Patch<String> fromPatch(JsonNode jsonString) {
        return chapterInfoHistoryConverter.convert(jsonString);
    }

    @Named("toPatch")
    JsonNode toPatch(Patch<String> patchObject) {
        return chapterInfoHistoryConverter.convert(patchObject);
    }

    @RequiredArgsConstructor
    public static class ChapterInfoHistoryConverter {

        private final ObjectMapper mapper;

        @SneakyThrows
        public Patch<String> convert(JsonNode jsonNode) {
            return mapper.treeToValue(jsonNode, Patch.class);
        }

        @SneakyThrows
        public JsonNode convert(Patch<String> patch) {
            return mapper.valueToTree(patch);
        }

    }

}
