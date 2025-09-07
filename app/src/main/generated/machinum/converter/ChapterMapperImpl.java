package machinum.converter;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import machinum.entity.ChapterEntity;
import machinum.model.Chapter;
import machinum.model.Character;
import machinum.model.ObjectName;
import machinum.model.Scene;
import machinum.processor.core.ChapterWarning;
import machinum.repository.ChapterRepository;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:49:42+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class ChapterMapperImpl implements ChapterMapper {

    @Override
    public List<ChapterEntity> toEntity(List<Chapter> list) {
        if ( list == null ) {
            return null;
        }

        List<ChapterEntity> list1 = new ArrayList<ChapterEntity>( list.size() );
        for ( Chapter chapter : list ) {
            list1.add( toEntity( chapter ) );
        }

        return list1;
    }

    @Override
    public List<Chapter> toDto(List<ChapterEntity> value) {
        if ( value == null ) {
            return null;
        }

        List<Chapter> list = new ArrayList<Chapter>( value.size() );
        for ( ChapterEntity chapterEntity : value ) {
            list.add( toDto( chapterEntity ) );
        }

        return list;
    }

    @Override
    public ChapterEntity toEntity(Chapter value) {
        if ( value == null ) {
            return null;
        }

        ChapterEntity.ChapterEntityBuilder chapterEntity = ChapterEntity.builder();

        chapterEntity.id( value.getId() );
        chapterEntity.bookId( value.getBookId() );
        chapterEntity.sourceKey( value.getSourceKey() );
        chapterEntity.number( value.getNumber() );
        chapterEntity.title( value.getTitle() );
        chapterEntity.translatedTitle( value.getTranslatedTitle() );
        chapterEntity.text( value.getText() );
        chapterEntity.cleanChunks( value.getCleanChunks() );
        chapterEntity.proofreadText( value.getProofreadText() );
        chapterEntity.translatedText( value.getTranslatedText() );
        chapterEntity.translatedChunks( value.getTranslatedChunks() );
        chapterEntity.summary( value.getSummary() );
        chapterEntity.consolidatedSummary( value.getConsolidatedSummary() );
        List<String> list = value.getKeywords();
        if ( list != null ) {
            chapterEntity.keywords( new ArrayList<String>( list ) );
        }
        chapterEntity.selfConsistency( value.getSelfConsistency() );
        List<String> list1 = value.getQuotes();
        if ( list1 != null ) {
            chapterEntity.quotes( new ArrayList<String>( list1 ) );
        }
        List<Character> list2 = value.getCharacters();
        if ( list2 != null ) {
            chapterEntity.characters( new ArrayList<Character>( list2 ) );
        }
        chapterEntity.themes( value.getThemes() );
        chapterEntity.perspective( value.getPerspective() );
        chapterEntity.tone( value.getTone() );
        chapterEntity.foreshadowing( value.getForeshadowing() );
        List<ObjectName> list3 = value.getNames();
        if ( list3 != null ) {
            chapterEntity.names( new ArrayList<ObjectName>( list3 ) );
        }
        List<Scene> list4 = value.getScenes();
        if ( list4 != null ) {
            chapterEntity.scenes( new ArrayList<Scene>( list4 ) );
        }
        List<ChapterWarning> list5 = value.getWarnings();
        if ( list5 != null ) {
            chapterEntity.warnings( new ArrayList<ChapterWarning>( list5 ) );
        }

        return chapterEntity.build();
    }

    @Override
    public Chapter toDto(ChapterEntity value) {
        if ( value == null ) {
            return null;
        }

        Chapter.ChapterBuilder chapter = Chapter.builder();

        chapter.id( value.getId() );
        chapter.number( value.getNumber() );
        chapter.title( value.getTitle() );
        chapter.translatedTitle( value.getTranslatedTitle() );
        chapter.text( value.getText() );
        chapter.cleanChunks( value.getCleanChunks() );
        chapter.proofreadText( value.getProofreadText() );
        chapter.translatedText( value.getTranslatedText() );
        chapter.translatedChunks( value.getTranslatedChunks() );
        chapter.summary( value.getSummary() );
        chapter.consolidatedSummary( value.getConsolidatedSummary() );
        List<String> list = value.getKeywords();
        if ( list != null ) {
            chapter.keywords( new ArrayList<String>( list ) );
        }
        chapter.selfConsistency( value.getSelfConsistency() );
        List<String> list1 = value.getQuotes();
        if ( list1 != null ) {
            chapter.quotes( new ArrayList<String>( list1 ) );
        }
        List<Character> list2 = value.getCharacters();
        if ( list2 != null ) {
            chapter.characters( new ArrayList<Character>( list2 ) );
        }
        chapter.themes( value.getThemes() );
        chapter.perspective( value.getPerspective() );
        chapter.tone( value.getTone() );
        chapter.foreshadowing( value.getForeshadowing() );
        List<ObjectName> list3 = value.getNames();
        if ( list3 != null ) {
            chapter.names( new ArrayList<ObjectName>( list3 ) );
        }
        List<Scene> list4 = value.getScenes();
        if ( list4 != null ) {
            chapter.scenes( new ArrayList<Scene>( list4 ) );
        }
        chapter.bookId( value.getBookId() );
        chapter.sourceKey( value.getSourceKey() );
        List<ChapterWarning> list5 = value.getWarnings();
        if ( list5 != null ) {
            chapter.warnings( new ArrayList<ChapterWarning>( list5 ) );
        }

        return chapter.build();
    }

    @Override
    public Chapter toDto(ChapterRepository.ChapterTitleDto chapterTitleDto) {
        if ( chapterTitleDto == null ) {
            return null;
        }

        Chapter.ChapterBuilder chapter = Chapter.builder();

        chapter.id( chapterTitleDto.getId() );
        chapter.number( chapterTitleDto.getNumber() );
        chapter.title( chapterTitleDto.getTitle() );
        chapter.translatedTitle( chapterTitleDto.getTranslatedTitle() );

        return chapter.build();
    }
}
