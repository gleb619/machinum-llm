package machinum.converter;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import machinum.entity.LineView;
import machinum.model.Line;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:49:42+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class LineMapperImpl implements LineMapper {

    @Override
    public List<LineView> toEntity(List<Line> list) {
        if ( list == null ) {
            return null;
        }

        List<LineView> list1 = new ArrayList<LineView>( list.size() );
        for ( Line line : list ) {
            list1.add( toEntity( line ) );
        }

        return list1;
    }

    @Override
    public List<Line> toDto(List<LineView> value) {
        if ( value == null ) {
            return null;
        }

        List<Line> list = new ArrayList<Line>( value.size() );
        for ( LineView lineView : value ) {
            list.add( toDto( lineView ) );
        }

        return list;
    }

    @Override
    public LineView toEntity(Line value) {
        if ( value == null ) {
            return null;
        }

        LineView.LineViewBuilder lineView = LineView.builder();

        lineView.id( value.getId() );
        lineView.chapterId( value.getChapterId() );
        lineView.sourceKey( value.getSourceKey() );
        lineView.number( value.getNumber() );
        lineView.bookId( value.getBookId() );
        lineView.lineIndex( value.getLineIndex() );
        lineView.originalLine( value.getOriginalLine() );
        lineView.translatedLine( value.getTranslatedLine() );

        return lineView.build();
    }

    @Override
    public Line toDto(LineView value) {
        if ( value == null ) {
            return null;
        }

        Line.LineBuilder line = Line.builder();

        line.id( value.getId() );
        line.chapterId( value.getChapterId() );
        line.sourceKey( value.getSourceKey() );
        line.number( value.getNumber() );
        line.bookId( value.getBookId() );
        line.lineIndex( value.getLineIndex() );
        line.originalLine( value.getOriginalLine() );
        line.translatedLine( value.getTranslatedLine() );

        return line.build();
    }
}
