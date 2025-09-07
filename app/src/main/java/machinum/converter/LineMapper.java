package machinum.converter;

import machinum.entity.LineView;
import machinum.model.Line;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LineMapper extends BaseMapper<LineView, Line> {

}
