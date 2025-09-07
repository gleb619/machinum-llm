package machinum.converter;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import machinum.entity.ChapterGlossaryView;
import machinum.model.ChapterGlossary;
import machinum.model.ObjectName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:04:18+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class ChapterGlossaryMapperImpl implements ChapterGlossaryMapper {

    @Autowired
    private ObjectNameMapper objectNameMapper;

    @Override
    public List<ChapterGlossaryView> toEntity(List<ChapterGlossary> list) {
        if ( list == null ) {
            return null;
        }

        List<ChapterGlossaryView> list1 = new ArrayList<ChapterGlossaryView>( list.size() );
        for ( ChapterGlossary chapterGlossary : list ) {
            list1.add( toEntity( chapterGlossary ) );
        }

        return list1;
    }

    @Override
    public List<ChapterGlossary> toDto(List<ChapterGlossaryView> value) {
        if ( value == null ) {
            return null;
        }

        List<ChapterGlossary> list = new ArrayList<ChapterGlossary>( value.size() );
        for ( ChapterGlossaryView chapterGlossaryView : value ) {
            list.add( toDto( chapterGlossaryView ) );
        }

        return list;
    }

    @Override
    public ChapterGlossary toDto(ChapterGlossary.ChapterGlossaryProjection projection) {
        if ( projection == null ) {
            return null;
        }

        ChapterGlossary.ChapterGlossaryBuilder chapterGlossary = ChapterGlossary.builder();

        chapterGlossary.id( projection.getId() );
        chapterGlossary.chapterId( projection.getChapterId() );
        chapterGlossary.chapterNumber( projection.getChapterNumber() );

        return chapterGlossary.build();
    }

    @Override
    public ChapterGlossary toDto(ChapterGlossaryView view) {
        if ( view == null ) {
            return null;
        }

        ChapterGlossary.ChapterGlossaryBuilder chapterGlossary = ChapterGlossary.builder();

        chapterGlossary.objectName( objectNameMapper.toObjectName( view ) );
        chapterGlossary.chapterNumber( view.getNumber() );
        chapterGlossary.id( view.getId() );
        chapterGlossary.chapterId( view.getChapterId() );

        return chapterGlossary.build();
    }

    @Override
    public ChapterGlossaryView toEntity(ChapterGlossary dto) {
        if ( dto == null ) {
            return null;
        }

        ChapterGlossaryView.ChapterGlossaryViewBuilder chapterGlossaryView = ChapterGlossaryView.builder();

        chapterGlossaryView.name( dtoObjectNameName( dto ) );
        chapterGlossaryView.category( dtoObjectNameCategory( dto ) );
        chapterGlossaryView.description( dtoObjectNameDescription( dto ) );
        chapterGlossaryView.id( dto.getId() );
        chapterGlossaryView.chapterId( dto.getChapterId() );

        return chapterGlossaryView.build();
    }

    private String dtoObjectNameName(ChapterGlossary chapterGlossary) {
        ObjectName objectName = chapterGlossary.getObjectName();
        if ( objectName == null ) {
            return null;
        }
        return objectName.getName();
    }

    private String dtoObjectNameCategory(ChapterGlossary chapterGlossary) {
        ObjectName objectName = chapterGlossary.getObjectName();
        if ( objectName == null ) {
            return null;
        }
        return objectName.getCategory();
    }

    private String dtoObjectNameDescription(ChapterGlossary chapterGlossary) {
        ObjectName objectName = chapterGlossary.getObjectName();
        if ( objectName == null ) {
            return null;
        }
        return objectName.getDescription();
    }
}
