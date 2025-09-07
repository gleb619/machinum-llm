package machinum.converter;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import machinum.entity.ChapterHistoryEntity;
import machinum.model.ChapterHistory;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:04:17+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class ChapterHistoryMapperImpl extends ChapterHistoryMapper {

    @Override
    public List<ChapterHistoryEntity> toEntity(List<ChapterHistory> list) {
        if ( list == null ) {
            return null;
        }

        List<ChapterHistoryEntity> list1 = new ArrayList<ChapterHistoryEntity>( list.size() );
        for ( ChapterHistory chapterHistory : list ) {
            list1.add( toEntity( chapterHistory ) );
        }

        return list1;
    }

    @Override
    public List<ChapterHistory> toDto(List<ChapterHistoryEntity> value) {
        if ( value == null ) {
            return null;
        }

        List<ChapterHistory> list = new ArrayList<ChapterHistory>( value.size() );
        for ( ChapterHistoryEntity chapterHistoryEntity : value ) {
            list.add( toDto( chapterHistoryEntity ) );
        }

        return list;
    }

    @Override
    public ChapterHistoryEntity toEntity(ChapterHistory value) {
        if ( value == null ) {
            return null;
        }

        ChapterHistoryEntity.ChapterHistoryEntityBuilder chapterHistoryEntity = ChapterHistoryEntity.builder();

        chapterHistoryEntity.patch( toPatch( value.getPatch() ) );
        chapterHistoryEntity.id( value.getId() );
        chapterHistoryEntity.chapterInfoId( value.getChapterInfoId() );
        chapterHistoryEntity.number( value.getNumber() );
        chapterHistoryEntity.fieldName( value.getFieldName() );
        chapterHistoryEntity.createdAt( value.getCreatedAt() );

        return chapterHistoryEntity.build();
    }

    @Override
    public ChapterHistory toDto(ChapterHistoryEntity value) {
        if ( value == null ) {
            return null;
        }

        ChapterHistory.ChapterHistoryBuilder chapterHistory = ChapterHistory.builder();

        chapterHistory.patch( fromPatch( value.getPatch() ) );
        chapterHistory.id( value.getId() );
        chapterHistory.chapterInfoId( value.getChapterInfoId() );
        chapterHistory.number( value.getNumber() );
        chapterHistory.fieldName( value.getFieldName() );
        chapterHistory.createdAt( value.getCreatedAt() );

        return chapterHistory.build();
    }
}
