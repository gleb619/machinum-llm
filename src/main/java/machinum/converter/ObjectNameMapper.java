package machinum.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import machinum.config.Holder;
import machinum.entity.ChapterGlossaryView;
import machinum.model.ObjectName;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@Mapper(componentModel = "spring")
public abstract class ObjectNameMapper {

    @Autowired
    @Qualifier("objectMapperHolder")
    private Holder<ObjectMapper> objectMapperHolder;

    public ObjectName toObjectName(ChapterGlossaryView view) {
        return objectMapperHolder.execute(mapper ->
                mapper.readValue(view.getRawJson(), ObjectName.class));
    }

}