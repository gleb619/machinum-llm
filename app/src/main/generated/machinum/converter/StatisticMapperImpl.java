package machinum.converter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import machinum.entity.StatisticEntity;
import machinum.model.Statistic;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:04:18+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class StatisticMapperImpl implements StatisticMapper {

    @Override
    public List<StatisticEntity> toEntity(List<Statistic> list) {
        if ( list == null ) {
            return null;
        }

        List<StatisticEntity> list1 = new ArrayList<StatisticEntity>( list.size() );
        for ( Statistic statistic : list ) {
            list1.add( toEntity( statistic ) );
        }

        return list1;
    }

    @Override
    public List<Statistic> toDto(List<StatisticEntity> value) {
        if ( value == null ) {
            return null;
        }

        List<Statistic> list = new ArrayList<Statistic>( value.size() );
        for ( StatisticEntity statisticEntity : value ) {
            list.add( toDto( statisticEntity ) );
        }

        return list;
    }

    @Override
    public StatisticEntity toEntity(Statistic value) {
        if ( value == null ) {
            return null;
        }

        StatisticEntity.StatisticEntityBuilder statisticEntity = StatisticEntity.builder();

        statisticEntity.id( value.getId() );
        statisticEntity.date( value.getDate() );
        statisticEntity.position( value.getPosition() );
        statisticEntity.mode( value.getMode() );
        statisticEntity.runId( value.getRunId() );
        statisticEntity.operationName( value.getOperationName() );
        statisticEntity.operationType( value.getOperationType() );
        if ( value.getChapter() != null ) {
            statisticEntity.chapter( String.valueOf( value.getChapter() ) );
        }
        statisticEntity.rayId( value.getRayId() );
        statisticEntity.operationDate( value.getOperationDate() );
        if ( value.getOperationTimeSeconds() != null ) {
            statisticEntity.operationTimeSeconds( value.getOperationTimeSeconds().intValue() );
        }
        statisticEntity.operationTimeString( value.getOperationTimeString() );
        statisticEntity.inputHistoryTokens( value.getInputHistoryTokens() );
        statisticEntity.inputHistoryWords( value.getInputHistoryWords() );
        statisticEntity.inputTokens( value.getInputTokens() );
        statisticEntity.inputWords( value.getInputWords() );
        statisticEntity.outputHistoryTokens( value.getOutputHistoryTokens() );
        statisticEntity.outputHistoryWords( value.getOutputHistoryWords() );
        statisticEntity.outputTokens( value.getOutputTokens() );
        statisticEntity.outputWords( value.getOutputWords() );
        if ( value.getConversionPercent() != null ) {
            statisticEntity.conversionPercent( BigDecimal.valueOf( value.getConversionPercent() ) );
        }
        statisticEntity.tokens( value.getTokens() );
        statisticEntity.tokensLeft( value.getTokensLeft() );
        statisticEntity.aiOptions( value.getAiOptions() );
        List<Statistic.StatisticMessage> list = value.getMessages();
        if ( list != null ) {
            statisticEntity.messages( new ArrayList<Statistic.StatisticMessage>( list ) );
        }

        return statisticEntity.build();
    }

    @Override
    public Statistic toDto(StatisticEntity value) {
        if ( value == null ) {
            return null;
        }

        Statistic.StatisticBuilder statistic = Statistic.builder();

        statistic.id( value.getId() );
        statistic.date( value.getDate() );
        statistic.position( value.getPosition() );
        statistic.mode( value.getMode() );
        statistic.runId( value.getRunId() );
        statistic.operationName( value.getOperationName() );
        statistic.operationType( value.getOperationType() );
        if ( value.getChapter() != null ) {
            statistic.chapter( Integer.parseInt( value.getChapter() ) );
        }
        statistic.rayId( value.getRayId() );
        statistic.operationDate( value.getOperationDate() );
        if ( value.getOperationTimeSeconds() != null ) {
            statistic.operationTimeSeconds( value.getOperationTimeSeconds().longValue() );
        }
        statistic.operationTimeString( value.getOperationTimeString() );
        statistic.inputHistoryTokens( value.getInputHistoryTokens() );
        statistic.inputHistoryWords( value.getInputHistoryWords() );
        statistic.inputTokens( value.getInputTokens() );
        statistic.inputWords( value.getInputWords() );
        statistic.outputHistoryTokens( value.getOutputHistoryTokens() );
        statistic.outputHistoryWords( value.getOutputHistoryWords() );
        statistic.outputTokens( value.getOutputTokens() );
        statistic.outputWords( value.getOutputWords() );
        if ( value.getConversionPercent() != null ) {
            statistic.conversionPercent( value.getConversionPercent().doubleValue() );
        }
        statistic.tokens( value.getTokens() );
        statistic.tokensLeft( value.getTokensLeft() );
        statistic.aiOptions( value.getAiOptions() );
        List<Statistic.StatisticMessage> list = value.getMessages();
        if ( list != null ) {
            statistic.messages( new ArrayList<Statistic.StatisticMessage>( list ) );
        }

        return statistic.build();
    }
}
