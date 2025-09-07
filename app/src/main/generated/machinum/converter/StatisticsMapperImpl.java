package machinum.converter;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import machinum.entity.StatisticsView;
import machinum.model.StatisticsDto;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:04:17+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class StatisticsMapperImpl implements StatisticsMapper {

    @Override
    public List<StatisticsView> toEntity(List<StatisticsDto> list) {
        if ( list == null ) {
            return null;
        }

        List<StatisticsView> list1 = new ArrayList<StatisticsView>( list.size() );
        for ( StatisticsDto statisticsDto : list ) {
            list1.add( toEntity( statisticsDto ) );
        }

        return list1;
    }

    @Override
    public List<StatisticsDto> toDto(List<StatisticsView> value) {
        if ( value == null ) {
            return null;
        }

        List<StatisticsDto> list = new ArrayList<StatisticsDto>( value.size() );
        for ( StatisticsView statisticsView : value ) {
            list.add( toDto( statisticsView ) );
        }

        return list;
    }

    @Override
    public StatisticsView toEntity(StatisticsDto value) {
        if ( value == null ) {
            return null;
        }

        StatisticsView.StatisticsViewBuilder statisticsView = StatisticsView.builder();

        statisticsView.id( value.getId() );
        statisticsView.date( value.getDate() );
        statisticsView.mode( value.getMode() );
        statisticsView.runId( value.getRunId() );
        statisticsView.operationName( value.getOperationName() );
        statisticsView.operationType( value.getOperationType() );
        statisticsView.chapter( value.getChapter() );
        statisticsView.rayId( value.getRayId() );
        statisticsView.operationDate( value.getOperationDate() );
        statisticsView.operationTimeSeconds( value.getOperationTimeSeconds() );
        statisticsView.operationTimeString( value.getOperationTimeString() );
        statisticsView.inputHistoryTokens( value.getInputHistoryTokens() );
        statisticsView.inputHistoryWords( value.getInputHistoryWords() );
        statisticsView.inputTokens( value.getInputTokens() );
        statisticsView.inputWords( value.getInputWords() );
        statisticsView.outputHistoryTokens( value.getOutputHistoryTokens() );
        statisticsView.outputHistoryWords( value.getOutputHistoryWords() );
        statisticsView.outputTokens( value.getOutputTokens() );
        statisticsView.outputWords( value.getOutputWords() );
        statisticsView.conversionPercent( value.getConversionPercent() );
        statisticsView.tokens( value.getTokens() );
        statisticsView.tokensLeft( value.getTokensLeft() );

        return statisticsView.build();
    }

    @Override
    public StatisticsDto toDto(StatisticsView value) {
        if ( value == null ) {
            return null;
        }

        StatisticsDto.StatisticsDtoBuilder statisticsDto = StatisticsDto.builder();

        statisticsDto.id( value.getId() );
        statisticsDto.date( value.getDate() );
        statisticsDto.mode( value.getMode() );
        statisticsDto.runId( value.getRunId() );
        statisticsDto.operationName( value.getOperationName() );
        statisticsDto.operationType( value.getOperationType() );
        statisticsDto.chapter( value.getChapter() );
        statisticsDto.rayId( value.getRayId() );
        statisticsDto.operationDate( value.getOperationDate() );
        statisticsDto.operationTimeSeconds( value.getOperationTimeSeconds() );
        statisticsDto.operationTimeString( value.getOperationTimeString() );
        statisticsDto.inputHistoryTokens( value.getInputHistoryTokens() );
        statisticsDto.inputHistoryWords( value.getInputHistoryWords() );
        statisticsDto.inputTokens( value.getInputTokens() );
        statisticsDto.inputWords( value.getInputWords() );
        statisticsDto.outputHistoryTokens( value.getOutputHistoryTokens() );
        statisticsDto.outputHistoryWords( value.getOutputHistoryWords() );
        statisticsDto.outputTokens( value.getOutputTokens() );
        statisticsDto.outputWords( value.getOutputWords() );
        statisticsDto.conversionPercent( value.getConversionPercent() );
        statisticsDto.tokens( value.getTokens() );
        statisticsDto.tokensLeft( value.getTokensLeft() );

        return statisticsDto.build();
    }
}
