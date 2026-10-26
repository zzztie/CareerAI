package com.zzztie.careerai.service;

import com.pgvector.PGvector;
import com.zzztie.careerai.dto.DocumentChunk;
import org.springframework.ai.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * U46：默认跳过。-Du46.run=true 运行检索评测；再加 -Du46.answers=true
 * 才调用问答模型并记录回答与 token，避免意外产生模型调用费用。
 */
@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "spring.datasource.username=test-only",
        "spring.datasource.password=test-only"
})
@EnabledIfSystemProperty(named = "u46.run", matches = "true")
class RagRetrievalEvaluationTest {

    @Autowired
    private RagIndexService indexService;

    @Autowired
    private RagQaService ragQaService;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier("ragDataSource")
    private DataSource ragDataSource;

    private final TextChunker chunker = new TextChunker();

    @Test
    void compareConfigurations() throws IOException {
        Path dataDir = Path.of(System.getProperty("u46.data.dir", "samples"));
        boolean runAnswers = Boolean.getBoolean("u46.answers");
        Map<String, String> documents = Map.of(
                "R", read(dataDir, "U46_固定简历.txt"),
                "J", read(dataDir, "U46_固定岗位JD.txt"),
                "K", read(dataDir, "U46_固定知识库.txt")
        );
        List<EvalCase> cases = loadCases(dataDir.resolve("U46_评测问题.tsv"));
        String caseId = System.getProperty("u46.case", "");
        if (!caseId.isBlank()) {
            cases = cases.stream().filter(item -> item.id().equals(caseId)).toList();
            if (cases.isEmpty()) {
                throw new IllegalArgumentException("找不到评测题目 ID：" + caseId);
            }
        }
        String scope = caseId.isBlank() ? "" : "_case-" + caseId;
        String configName = System.getProperty("u46.config", "");
        List<Config> configs = List.of(
                new Config("A", 600, 3, 0.5),
                new Config("B", 300, 3, 0.5),
                new Config("C", 600, 5, 0.5),
                new Config("D", 600, 3, 0.6),
                new Config("E", 600, 4, 0.6)
        );
        if (!configName.isBlank()) {
            configs = configs.stream().filter(item -> item.name().equals(configName)).toList();
            if (configs.isEmpty()) {
                throw new IllegalArgumentException("找不到评测配置：" + configName);
            }
        }
        String configScope = configName.isBlank() ? "" : "_config-" + configName;

        String runId = UUID.randomUUID().toString();
        JdbcTemplate jdbc = new JdbcTemplate(ragDataSource);
        List<String> createdKeys = new ArrayList<>();
        List<String> csv = new ArrayList<>();
        csv.add("config,chunk_size,top_k,max_distance,id,category,gold_count,hit_count,all_hit,missed_facts,gold_positions,retrieved_r,retrieved_j,retrieved_k,query_ms,index_ms");
        List<String> answerCsv = new ArrayList<>();
        answerCsv.add("config,id,category,expected_behavior,answer,retrieval_ms,model_ms,prompt_tokens,completion_tokens,factual_correct,citation_supported,refusal_correct");
        boolean completed = false;

        try {
            for (Config config : configs) {
                Map<String, String> keys = new HashMap<>();
                long indexStart = System.nanoTime();

                // 每个配置独立建索引，避免不同切块大小覆盖同一个 documentKey。
                for (String source : List.of("R", "J", "K")) {
                    String key = "u46:" + runId + ":" + config.name() + ":" + source;
                    keys.put(source, key);
                    createdKeys.add(key);
                    List<DocumentChunk> chunks = chunker.splitByParagraph(
                            source + ".txt", documents.get(source), config.chunkSize()
                    );
                    indexService.rebuildDocument(key, chunks);
                }
                long indexMs = millisSince(indexStart);

                for (EvalCase evalCase : cases) {
                    long queryStart = System.nanoTime();
                    Map<String, List<DocumentChunk>> found = new HashMap<>();

                    // 与现有 RagQaService 一样，分别在简历、JD、知识库中检索。
                    for (String source : List.of("R", "J", "K")) {
                        found.put(source, indexService.searchTopKWithinDistance(
                                keys.get(source), evalCase.question(),
                                config.topK(), config.maxDistance()
                        ));
                    }
                    long queryMs = millisSince(queryStart);

                    int hitCount = 0;
                    List<String> missedFacts = new ArrayList<>();
                    for (GoldFact fact : evalCase.goldFacts()) {
                        boolean hit = found.get(fact.source()).stream()
                                .anyMatch(chunk -> chunk.content().contains(fact.phrase()));
                        if (hit) {
                            hitCount++;
                        } else {
                            missedFacts.add(fact.source() + ":" + fact.phrase());
                        }
                    }
                    int goldCount = evalCase.goldFacts().size();
                    String allHit = goldCount == 0 ? "N/A" : Boolean.toString(hitCount == goldCount);
                    List<String> goldPositions = new ArrayList<>();
                    if (!caseId.isBlank()) {
                        // 仅在单题诊断时额外计算原文块的真实距离和排名。
                        float[] queryVector = embeddingModel.embed(evalCase.question());
                        for (GoldFact fact : evalCase.goldFacts()) {
                            List<Map<String, Object>> positions = jdbc.queryForList("""
                                    SELECT chunk_index, distance, rank
                                    FROM (
                                        SELECT chunk_index, content,
                                               embedding <=> ? AS distance,
                                               row_number() OVER (ORDER BY embedding <=> ?) AS rank
                                        FROM rag_chunk
                                        WHERE document_key = ?
                                    ) ranked
                                    WHERE position(? in content) > 0
                                    ORDER BY rank
                                    """,
                                    new PGvector(queryVector), new PGvector(queryVector),
                                    keys.get(fact.source()), fact.phrase()
                            );
                            for (Map<String, Object> position : positions) {
                                goldPositions.add(fact.source() + ":" + fact.phrase()
                                        + "@rank" + position.get("rank")
                                        + ",distance=" + String.format(
                                        Locale.ROOT, "%.4f",
                                        ((Number) position.get("distance")).doubleValue()
                                ));
                            }
                        }
                    }

                    csv.add(String.join(",",
                            config.name(), Integer.toString(config.chunkSize()),
                            Integer.toString(config.topK()), Double.toString(config.maxDistance()),
                            evalCase.id(), evalCase.category(),
                            Integer.toString(goldCount), Integer.toString(hitCount), allHit,
                            csvCell(String.join(";", missedFacts)),
                            csvCell(String.join(";", goldPositions)),
                            Integer.toString(found.get("R").size()),
                            Integer.toString(found.get("J").size()),
                            Integer.toString(found.get("K").size()),
                            Long.toString(queryMs), Long.toString(indexMs)
                    ));

                    if (runAnswers) {
                        // 复用正式问答服务；后三列留给人工按评测题的预期逐项判分。
                        RagQaService.EvaluationResult qa =
                                ragQaService.compareResumeAndJobDetailed(
                                        keys.get("R"), keys.get("J"), keys.get("K"),
                                        evalCase.question(), config.topK(), config.maxDistance()
                                );
                        answerCsv.add(String.join(",",
                                config.name(), evalCase.id(), evalCase.category(),
                                csvCell(evalCase.expectedBehavior()),
                                csvCell(qa.response()),
                                Long.toString(qa.retrievalMs()),
                                Long.toString(qa.modelMs()),
                                token(qa.promptTokens()),
                                token(qa.completionTokens()),
                                "", "", ""
                        ));
                    }
                }
            }
            completed = true;
        } finally {
            try {
                // 模型或数据库中途出错时也保留已完成的行，并用 partial 标明结果不完整。
                String status = completed ? "complete" : "partial";
                Path report = dataDir.resolve(
                        "U46_检索评测结果_" + runId + configScope + scope + "_" + status + ".csv"
                );
                Files.write(report, csv, StandardCharsets.UTF_8);
                System.out.println("U46 检索评测结果：" + report.toAbsolutePath());
                if (runAnswers) {
                    Path answerReport = dataDir.resolve(
                            "U46_问答评测结果_" + runId + configScope + scope + "_" + status + ".csv"
                    );
                    Files.write(answerReport, answerCsv, StandardCharsets.UTF_8);
                    System.out.println("U46 问答评测结果：" + answerReport.toAbsolutePath());
                }
            } finally {
                // 只清理本次运行生成的专用 key，不触碰业务文档。
                for (String key : createdKeys) {
                    jdbc.update("DELETE FROM rag_chunk WHERE document_key = ?", key);
                }
            }
        }
    }

    private String read(Path dir, String name) throws IOException {
        return Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
    }

    private List<EvalCase> loadCases(Path file) throws IOException {
        List<EvalCase> cases = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split("\t", -1);
            if (columns.length != 6) {
                throw new IllegalArgumentException("评测题目第 " + (i + 1) + " 行必须有 6 列");
            }
            List<GoldFact> facts = new ArrayList<>();
            if (!columns[4].equals("无")) {
                for (String item : columns[4].split(";")) {
                    String[] pair = item.split(":", 2);
                    if (pair.length != 2 || !List.of("R", "J", "K").contains(pair[0])) {
                        throw new IllegalArgumentException("无效的证据短语：" + item);
                    }
                    facts.add(new GoldFact(pair[0], pair[1]));
                }
            }
            cases.add(new EvalCase(columns[0], columns[1], columns[2], facts, columns[5]));
        }
        return cases;
    }

    private long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String token(Integer value) {
        return value == null ? "N/A" : value.toString();
    }

    private String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private record Config(String name, int chunkSize, int topK, double maxDistance) {}

    private record GoldFact(String source, String phrase) {}

    private record EvalCase(
            String id, String category, String question,
            List<GoldFact> goldFacts, String expectedBehavior
    ) {}
}
