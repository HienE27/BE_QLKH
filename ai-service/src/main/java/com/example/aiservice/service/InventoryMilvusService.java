package com.example.aiservice.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Milvus service dành riêng cho các snapshot tồn kho (inventory snapshot).
 * Mục tiêu: lưu vector mô tả trạng thái tồn kho hiện tại và tìm lại các kỳ có trạng thái tương tự.
 */
@Service
@Slf4j
public class InventoryMilvusService {

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private int milvusPort;

    @Value("${milvus.inventory.collection.name:inventory_snapshots}")
    private String collectionName;

    private MilvusServiceClient milvusClient;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PostConstruct
    public void init() {
        try {
            milvusClient = new MilvusServiceClient(
                    ConnectParam.newBuilder()
                            .withHost(milvusHost)
                            .withPort(milvusPort)
                            .build());

            createCollectionIfNotExists();
            log.info("[InventoryMilvus] Connected successfully to {}:{}", milvusHost, milvusPort);
        } catch (Exception e) {
            log.error("[InventoryMilvus] Failed to connect to Milvus", e);
            // Không throw để hệ thống vẫn chạy nếu Milvus không sẵn sàng
        }
    }

    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            milvusClient.close();
        }
    }

    private void createCollectionIfNotExists() {
        if (milvusClient == null) {
            return;
        }

        try {
            R<Boolean> hasCollectionR = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build());

            if (hasCollectionR.getData()) {
                log.info("[InventoryMilvus] Collection {} already exists", collectionName);
                return;
            }

            List<FieldType> fields = Arrays.asList(
                    FieldType.newBuilder()
                            .withName("id")
                            .withDataType(DataType.Int64)
                            .withPrimaryKey(true)
                            .withAutoID(true)
                            .build(),
                    FieldType.newBuilder()
                            .withName("snapshot_time")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(32)
                            .build(),
                    FieldType.newBuilder()
                            .withName("summary_hash")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(64)
                            .build(),
                    FieldType.newBuilder()
                            .withName("embedding")
                            .withDataType(DataType.FloatVector)
                            .withDimension(768)
                            .build(),
                    FieldType.newBuilder()
                            .withName("metadata")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(4000)
                            .build());

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDescription("Inventory snapshot embeddings for similarity search")
                    .withFieldTypes(fields)
                    .build();

            R<?> createR = milvusClient.createCollection(createParam);
            if (createR.getStatus() == R.Status.Success.getCode()) {
                log.info("[InventoryMilvus] Collection {} created successfully", collectionName);
            } else {
                log.error("[InventoryMilvus] Failed to create collection: {}", createR.getMessage());
            }
        } catch (Exception e) {
            log.error("[InventoryMilvus] Error creating collection", e);
        }
    }

    /**
     * Lưu embedding snapshot tồn kho vào Milvus.
     *
     * @param snapshotText  Chuỗi text mô tả snapshot (dùng để hash / debug)
     * @param embedding     Vector embedding 768 chiều
     * @param metadataJson  Metadata JSON (đã được serialize sẵn)
     */
    public void saveSnapshot(String snapshotText, List<Float> embedding, String metadataJson) {
        if (milvusClient == null) {
            log.warn("[InventoryMilvus] Client not available, skip saveSnapshot");
            return;
        }

        try {
            String snapshotTime = LocalDateTime.now().format(DATE_TIME_FORMATTER);
            String hash = Integer.toHexString(Objects.hashCode(snapshotText));

            List<Object> snapshotTimes = Collections.singletonList(snapshotTime);
            List<Object> hashes = Collections.singletonList(hash);
            List<List<Float>> embeddings = Collections.singletonList(embedding);
            List<Object> metadatas = Collections.singletonList(metadataJson != null ? metadataJson : "{}");

            List<InsertParam.Field> fields = Arrays.asList(
                    new InsertParam.Field("snapshot_time", snapshotTimes),
                    new InsertParam.Field("summary_hash", hashes),
                    new InsertParam.Field("embedding", embeddings),
                    new InsertParam.Field("metadata", metadatas));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();

            R<?> insertR = milvusClient.insert(insertParam);
            if (insertR.getStatus() == R.Status.Success.getCode()) {
                log.info("[InventoryMilvus] Snapshot saved successfully (time={}, hash={})", snapshotTime, hash);
            } else {
                log.error("[InventoryMilvus] Failed to save snapshot: {}", insertR.getMessage());
            }
        } catch (Exception e) {
            log.error("[InventoryMilvus] Error saving snapshot to Milvus", e);
        }
    }

    /**
     * Tìm các snapshot tồn kho tương tự nhất.
     */
    public List<Map<String, Object>> searchSimilar(List<Float> embedding, int topK) {
        if (milvusClient == null) {
            log.warn("[InventoryMilvus] Client not available, returning empty results");
            return Collections.emptyList();
        }

        try {
            List<String> outputFields = Arrays.asList("snapshot_time", "summary_hash", "metadata");

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(MetricType.L2)
                    .withOutFields(outputFields)
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(embedding))
                    .withVectorFieldName("embedding")
                    .build();

            R<?> searchR = milvusClient.search(searchParam);
            if (searchR.getStatus() != R.Status.Success.getCode()) {
                log.error("[InventoryMilvus] Search failed: {}", searchR.getMessage());
                return Collections.emptyList();
            }

            Object data = searchR.getData();
            if (!(data instanceof SearchResults)) {
                log.error("[InventoryMilvus] Unexpected search result type: {}", data != null ? data.getClass() : "null");
                return Collections.emptyList();
            }

            SearchResults searchResults = (SearchResults) data;
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResults.getResults());

            List<Map<String, Object>> resultList = new ArrayList<>();
            for (int i = 0; i < wrapper.getIDScore(0).size(); i++) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", wrapper.getIDScore(0).get(i).getLongID());
                result.put("score", wrapper.getIDScore(0).get(i).getScore());

                if (wrapper.getFieldData("snapshot_time", 0) != null) {
                    result.put("snapshot_time", wrapper.getFieldData("snapshot_time", 0).get(i));
                }
                if (wrapper.getFieldData("summary_hash", 0) != null) {
                    result.put("summary_hash", wrapper.getFieldData("summary_hash", 0).get(i));
                }
                if (wrapper.getFieldData("metadata", 0) != null) {
                    result.put("metadata", wrapper.getFieldData("metadata", 0).get(i));
                }

                resultList.add(result);
            }

            return resultList;
        } catch (Exception e) {
            log.error("[InventoryMilvus] Error searching snapshots in Milvus", e);
            return Collections.emptyList();
        }
    }
}


