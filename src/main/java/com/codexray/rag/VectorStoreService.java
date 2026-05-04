package com.codexray.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Elasticsearch 向量存储服务。
 * 管理代码切片的向量索引、存储和检索。
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);
    private static final String INDEX_NAME = "code_chunks";
    private static final int DIMENSION = 768;

    private final ElasticsearchClient client;

    public VectorStoreService(ElasticsearchClient client) {
        this.client = client;
        ensureIndex();
    }

    private void ensureIndex() {
        try {
            GetIndexResponse response = client.indices().get(g -> g.index(INDEX_NAME));
            if (response.result().containsKey(INDEX_NAME)) {
                return;
            }
        } catch (Exception ignored) {}

        try {
            client.indices().create(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("task_id", p -> p.keyword(k -> k))
                            .properties("file_path", p -> p.text(t -> t))
                            .properties("start_line", p -> p.integer(i -> i))
                            .properties("end_line", p -> p.integer(i -> i))
                            .properties("symbol_name", p -> p.text(t -> t))
                            .properties("category", p -> p.keyword(k -> k))
                            .properties("content", p -> p.text(t -> t))
                            .properties("embedding", p -> p
                                    .denseVector(d -> d.dims(DIMENSION).index(true).similarity("cosine"))
                            )
                    )
            );
            log.info("Created Elasticsearch index: {}", INDEX_NAME);
        } catch (Exception e) {
            log.warn("Failed to create ES index (may already exist): {}", e.getMessage());
        }
    }

    /**
     * 存储一批代码切片到 ES。
     */
    public void storeChunks(String taskId, List<CodeChunkDoc> chunks) {
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (int i = 0; i < chunks.size(); i++) {
                CodeChunkDoc chunk = chunks.get(i);
                String id = taskId + ":" + i;
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(id)
                                .document(chunk)
                        )
                );
            }

            BulkResponse response = client.bulk(bulkBuilder.build());
            if (response.errors()) {
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .forEach(item -> log.warn("ES bulk error: {}", item.error().reason()));
            }
            log.debug("Stored {} chunks for task {}", chunks.size(), taskId);
        } catch (Exception e) {
            log.error("Failed to store chunks to ES", e);
            throw new RuntimeException("ES store failed: " + e.getMessage(), e);
        }
    }

    /**
     * 向量检索：根据 query 向量搜索最相关的代码切片。
     */
    public List<CodeChunkDoc> search(String taskId, float[] queryVector, int topK, String category) {
        try {
            var searchBuilder = new SearchRequest.Builder()
                    .index(INDEX_NAME)
                    .size(topK)
                    .source(s -> s.filter(f -> f.excludes("embedding")))
                    .query(q -> q
                            .bool(b -> {
                                b.must(m -> m.term(t -> t.field("task_id").value(taskId)));
                                if (category != null && !category.isBlank()) {
                                    b.filter(f -> f.term(t -> t.field("category").value(category)));
                                }
                                return b;
                            })
                    )
                    .knn(k -> k
                            .field("embedding")
                            .queryVector(toFloatList(queryVector))
                            .k((long) topK)
                            .numCandidates(Math.max((long) topK * 10, 100L))
                            .filter(f -> f.term(t -> t.field("task_id").value(taskId)))
                    );

            SearchResponse<CodeChunkDoc> response = client.search(searchBuilder.build(), CodeChunkDoc.class);

            List<CodeChunkDoc> results = new ArrayList<>();
            for (Hit<CodeChunkDoc> hit : response.hits().hits()) {
                CodeChunkDoc doc = hit.source();
                if (doc != null) {
                    results.add(doc);
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Vector search failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 按类别过滤搜索：不使用向量相似度，仅通过类别和关键词过滤获取代码切片。
     * 用于分析阶段按分类提取代码，而非语义搜索。
     */
    public List<CodeChunkDoc> searchByCategory(String taskId, String category, int topK) {
        try {
            SearchResponse<CodeChunkDoc> response = client.search(s -> s
                            .index(INDEX_NAME)
                            .size(topK)
                            .source(src -> src.filter(f -> f.excludes("embedding")))
                            .query(q -> q
                                    .bool(b -> {
                                        b.must(m -> m.term(t -> t.field("task_id").value(taskId)));
                                        if (category != null && !category.isBlank()) {
                                            b.filter(f -> f.term(t -> t.field("category").value(category)));
                                        }
                                        return b;
                                    })
                            ),
                    CodeChunkDoc.class
            );

            List<CodeChunkDoc> results = new ArrayList<>();
            for (Hit<CodeChunkDoc> hit : response.hits().hits()) {
                CodeChunkDoc doc = hit.source();
                if (doc != null) results.add(doc);
            }
            return results;
        } catch (Exception e) {
            log.error("Category search failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 全文检索：按关键词搜索代码切片（符号名、内容）。
     */
    public List<CodeChunkDoc> searchText(String taskId, String query, int topK) {
        try {
            SearchResponse<CodeChunkDoc> response = client.search(s -> s
                            .index(INDEX_NAME)
                            .size(topK)
                            .source(src -> src.filter(f -> f.excludes("embedding")))
                            .query(q -> q
                                    .bool(b -> b
                                            .must(m -> m.term(t -> t.field("task_id").value(taskId)))
                                            .must(m -> m.multiMatch(mm -> mm
                                                    .query(query)
                                                    .fields("symbol_name", "content", "file_path")
                                            ))
                                    )
                            ),
                    CodeChunkDoc.class
            );

            List<CodeChunkDoc> results = new ArrayList<>();
            for (Hit<CodeChunkDoc> hit : response.hits().hits()) {
                CodeChunkDoc doc = hit.source();
                if (doc != null) results.add(doc);
            }
            return results;
        } catch (Exception e) {
            log.error("Text search failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 跨仓库向量检索：不限定 task_id，或按 taskIds 列表过滤。
     */
    public List<CodeChunkDoc> searchCrossRepo(float[] queryVector, List<String> taskIds, int topK) {
        try {
            var searchBuilder = new SearchRequest.Builder()
                    .index(INDEX_NAME)
                    .size(topK)
                    .source(s -> s.filter(f -> f.excludes("embedding")))
                    .query(q -> q
                            .bool(b -> {
                                if (taskIds != null && !taskIds.isEmpty()) {
                                    b.filter(f -> f.terms(t -> t
                                            .field("task_id")
                                            .terms(tv -> tv.value(taskIds.stream()
                                                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                                    .toList()))));
                                }
                                return b;
                            })
                    )
                    .knn(k -> {
                        k.field("embedding")
                         .queryVector(toFloatList(queryVector))
                         .k((long) topK)
                         .numCandidates(Math.max((long) topK * 10, 100L));
                        if (taskIds != null && !taskIds.isEmpty()) {
                            k.filter(f -> f.terms(t -> t
                                    .field("task_id")
                                    .terms(tv -> tv.value(taskIds.stream()
                                            .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                            .toList()))));
                        }
                        return k;
                    });

            SearchResponse<CodeChunkDoc> response = client.search(searchBuilder.build(), CodeChunkDoc.class);
            List<CodeChunkDoc> results = new ArrayList<>();
            for (Hit<CodeChunkDoc> hit : response.hits().hits()) {
                CodeChunkDoc doc = hit.source();
                if (doc != null) results.add(doc);
            }
            return results;
        } catch (Exception e) {
            log.error("Cross-repo vector search failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 跨仓库全文检索：不限定 task_id，或按 taskIds 列表过滤。
     */
    public List<CodeChunkDoc> searchTextCrossRepo(String query, List<String> taskIds, int topK) {
        try {
            SearchResponse<CodeChunkDoc> response = client.search(s -> s
                            .index(INDEX_NAME)
                            .size(topK)
                            .source(src -> src.filter(f -> f.excludes("embedding")))
                            .query(q -> q
                                    .bool(b -> {
                                        if (taskIds != null && !taskIds.isEmpty()) {
                                            b.filter(f -> f.terms(t -> t
                                                    .field("task_id")
                                                    .terms(tv -> tv.value(taskIds.stream()
                                                            .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                                            .toList()))));
                                        }
                                        b.must(m -> m.multiMatch(mm -> mm
                                                .query(query)
                                                .fields("symbol_name", "content", "file_path")
                                        ));
                                        return b;
                                    })
                            ),
                    CodeChunkDoc.class
            );

            List<CodeChunkDoc> results = new ArrayList<>();
            for (Hit<CodeChunkDoc> hit : response.hits().hits()) {
                CodeChunkDoc doc = hit.source();
                if (doc != null) results.add(doc);
            }
            return results;
        } catch (Exception e) {
            log.error("Cross-repo text search failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除指定任务的所有切片。
     */
    public void deleteByTaskId(String taskId) {
        try {
            client.deleteByQuery(d -> d
                    .index(INDEX_NAME)
                    .query(q -> q.term(t -> t.field("task_id").value(taskId)))
            );
            log.info("Deleted ES chunks for task: {}", taskId);
        } catch (Exception e) {
            log.error("Failed to delete chunks for task: {}", taskId, e);
        }
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }

    /**
     * ES 文档模型。
     */
    public record CodeChunkDoc(
            String task_id,
            String file_path,
            int start_line,
            int end_line,
            String symbol_name,
            String category,
            String content,
            List<Float> embedding
    ) {}
}
