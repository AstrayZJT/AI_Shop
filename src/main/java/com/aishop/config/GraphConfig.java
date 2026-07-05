package com.aishop.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collections;

import javax.sql.DataSource;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfig {

    @Bean
    public ObjectStreamStateSerializer<MessagesState<String>> graphStateSerializer() {
        return new ObjectStreamStateSerializer<>(MessagesState::new);
    }

    @Bean
    public BaseCheckpointSaver checkpointSaver(DataSource dataSource,
                                               ObjectStreamStateSerializer<MessagesState<String>> serializer) throws Exception {
        if (isPostgres(dataSource)) {
            return PostgresSaver.builder()
                    .datasource(dataSource)
                    .stateSerializer(serializer)
                    .createTables(true)
                    .dropTablesFirst(false)
                    .build();
        }
        return new MemorySaver();
    }

    @Bean
    public StateGraph<MessagesState<String>> assistantGraph(ObjectStreamStateSerializer<MessagesState<String>> serializer) {
        return new StateGraph<>(serializer);
    }

    @Bean
    public CompiledGraph<MessagesState<String>> compiledAssistantGraph(
            StateGraph<MessagesState<String>> assistantGraph,
            ObjectStreamStateSerializer<MessagesState<String>> serializer,
            DataSource dataSource,
            ShopProperties properties) throws Exception {

        AsyncNodeAction<MessagesState<String>> passthrough = state -> java.util.concurrent.CompletableFuture.completedFuture(Collections.emptyMap());

        assistantGraph
                .addNode("start", passthrough)
                .addEdge(GraphDefinition.START, "start")
                .addEdge("start", GraphDefinition.END);

        var builder = CompileConfig.builder()
                .graphId("assistant-workflow")
                .recursionLimit(8);

        if (properties.ai().enabled()) {
            builder.checkpointSaver(checkpointSaver(dataSource, serializer));
        }

        return assistantGraph.compile(builder.build());
    }

    private boolean isPostgres(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return "PostgreSQL".equalsIgnoreCase(metaData.getDatabaseProductName());
        }
    }
}
