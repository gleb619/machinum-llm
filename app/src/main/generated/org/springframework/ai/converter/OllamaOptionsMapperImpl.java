package org.springframework.ai.converter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:49:42+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class OllamaOptionsMapperImpl implements OllamaOptionsMapper {

    @Override
    public OllamaOptions copy(OllamaOptions ollamaOptions) {
        if ( ollamaOptions == null ) {
            return null;
        }

        OllamaOptions.Builder ollamaOptions1 = OllamaOptions.builder();

        if ( ollamaOptions.getModel() != null ) {
            ollamaOptions1.model( Enum.valueOf( OllamaModel.class, ollamaOptions.getModel() ) );
        }
        ollamaOptions1.format( ollamaOptions.getFormat() );
        ollamaOptions1.keepAlive( ollamaOptions.getKeepAlive() );
        ollamaOptions1.truncate( ollamaOptions.getTruncate() );
        ollamaOptions1.useNUMA( ollamaOptions.getUseNUMA() );
        ollamaOptions1.numCtx( ollamaOptions.getNumCtx() );
        ollamaOptions1.numBatch( ollamaOptions.getNumBatch() );
        ollamaOptions1.numGPU( ollamaOptions.getNumGPU() );
        ollamaOptions1.mainGPU( ollamaOptions.getMainGPU() );
        ollamaOptions1.lowVRAM( ollamaOptions.getLowVRAM() );
        ollamaOptions1.f16KV( ollamaOptions.getF16KV() );
        ollamaOptions1.logitsAll( ollamaOptions.getLogitsAll() );
        ollamaOptions1.vocabOnly( ollamaOptions.getVocabOnly() );
        ollamaOptions1.useMMap( ollamaOptions.getUseMMap() );
        ollamaOptions1.useMLock( ollamaOptions.getUseMLock() );
        ollamaOptions1.numThread( ollamaOptions.getNumThread() );
        ollamaOptions1.numKeep( ollamaOptions.getNumKeep() );
        ollamaOptions1.seed( ollamaOptions.getSeed() );
        ollamaOptions1.numPredict( ollamaOptions.getNumPredict() );
        ollamaOptions1.topK( ollamaOptions.getTopK() );
        ollamaOptions1.topP( ollamaOptions.getTopP() );
        ollamaOptions1.minP( ollamaOptions.getMinP() );
        ollamaOptions1.tfsZ( ollamaOptions.getTfsZ() );
        ollamaOptions1.typicalP( ollamaOptions.getTypicalP() );
        ollamaOptions1.repeatLastN( ollamaOptions.getRepeatLastN() );
        ollamaOptions1.temperature( ollamaOptions.getTemperature() );
        ollamaOptions1.repeatPenalty( ollamaOptions.getRepeatPenalty() );
        ollamaOptions1.presencePenalty( ollamaOptions.getPresencePenalty() );
        ollamaOptions1.frequencyPenalty( ollamaOptions.getFrequencyPenalty() );
        ollamaOptions1.mirostat( ollamaOptions.getMirostat() );
        ollamaOptions1.mirostatTau( ollamaOptions.getMirostatTau() );
        ollamaOptions1.mirostatEta( ollamaOptions.getMirostatEta() );
        ollamaOptions1.penalizeNewline( ollamaOptions.getPenalizeNewline() );
        List<String> list = ollamaOptions.getStop();
        if ( list != null ) {
            ollamaOptions1.stop( new ArrayList<String>( list ) );
        }
        ollamaOptions1.toolCallbacks( toolCallbackListToToolCallbackArray( ollamaOptions.getToolCallbacks() ) );
        ollamaOptions1.toolNames( stringSetToStringArray( ollamaOptions.getToolNames() ) );
        ollamaOptions1.internalToolExecutionEnabled( ollamaOptions.getInternalToolExecutionEnabled() );
        Map<String, Object> map = ollamaOptions.getToolContext();
        if ( map != null ) {
            ollamaOptions1.toolContext( new LinkedHashMap<String, Object>( map ) );
        }

        return ollamaOptions1.build();
    }

    protected ToolCallback[] toolCallbackListToToolCallbackArray(List<ToolCallback> list) {
        if ( list == null ) {
            return null;
        }

        ToolCallback[] toolCallbackTmp = new ToolCallback[list.size()];
        int i = 0;
        for ( ToolCallback toolCallback : list ) {
            toolCallbackTmp[i] = toolCallback;
            i++;
        }

        return toolCallbackTmp;
    }

    protected String[] stringSetToStringArray(Set<String> set) {
        if ( set == null ) {
            return null;
        }

        String[] stringTmp = new String[set.size()];
        int i = 0;
        for ( String string : set ) {
            stringTmp[i] = string;
            i++;
        }

        return stringTmp;
    }
}
