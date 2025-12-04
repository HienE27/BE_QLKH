package com.example.aiservice.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.grpc.SearchResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;

@Service
@Slf4j
public class MilvusService {

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private int milvusPort;

    @Value("${milvus.collection.name:receipt_embeddings}")
    private String collectionName;

    private MilvusServiceClient milvusClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            milvusClient = new MilvusServiceClient(
                    ConnectParam.newBuilder()
                            .withHost(milvusHost)
                            .withPort(milvusPort)
                            .build());

            // Tạo collection nếu chưa tồn tại
            createCollectionIfNotExists();

            log.info("Milvus connected successfully to {}:{}", milvusHost, milvusPort);
        } catch (Exception e) {
            log.error("Failed to connect to Milvus", e);
            // Không throw exception để service vẫn có thể chạy mà không có Milvus
        }
    }

    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            milvusClient.close();
        }
    }

    private void createCollectionIfNotExists() {
        try {
            // Kiểm tra collection đã tồn tại chưa
            R<Boolean> hasCollectionR = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build());

            if (hasCollectionR.getData()) {
                log.info("Collection {} already exists", collectionName);
                return;
            }

            // Tạo collection mới
            List<FieldType> fields = Arrays.asList(
                    FieldType.newBuilder()
                            .withName("id")
                            .withDataType(DataType.Int64)
                            .withPrimaryKey(true)
                            .withAutoID(true)
                            .build(),
                    FieldType.newBuilder()
                            .withName("receipt_type")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(50)
                            .build(),
                    FieldType.newBuilder()
                            .withName("supplier_name")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(255)
                            .build(),
                    FieldType.newBuilder()
                            .withName("customer_name")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(255)
                            .build(),
                    FieldType.newBuilder()
                            .withName("embedding")
                            .withDataType(DataType.FloatVector)
                            .withDimension(768) // Dimension của embedding (có thể thay đổi)
                            .build(),
                    FieldType.newBuilder()
                            .withName("metadata")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(2000)
                            .build());

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDescription("Receipt embeddings for similarity search")
                    .withFieldTypes(fields)
                    .build();

            R<?> createR = milvusClient.createCollection(createParam);
            if (createR.getStatus() == R.Status.Success.getCode()) {
                log.info("Collection {} created successfully", collectionName);
            } else {
                log.error("Failed to create collection: {}", createR.getMessage());
            }
        } catch (Exception e) {
            log.error("Error creating collection", e);
        }
    }

    /**
     * Lưu embedding vào Milvus
     */
    public void saveEmbedding(String receiptType, String supplierName, String customerName,
            List<Float> embedding, Map<String, Object> metadata) {
        if (milvusClient == null) {
            log.warn("Milvus client not available, skipping save");
            return;
        }

        try {
            List<Object> receiptTypes = Collections.singletonList(receiptType);
            List<Object> supplierNames = Collections.singletonList(supplierName != null ? supplierName : "");
            List<Object> customerNames = Collections.singletonList(customerName != null ? customerName : "");
            List<List<Float>> embeddings = Collections.singletonList(embedding);

            // Convert metadata to JSON string
            String metadataJson = "{}";
            if (metadata != null) {
                try {
                    metadataJson = objectMapper.writeValueAsString(metadata);
                } catch (Exception e) {
                    log.warn("Failed to serialize metadata to JSON, using toString", e);
                    metadataJson = metadata.toString();
                }
            }
            List<Object> metadatas = Collections.singletonList(metadataJson);

            List<InsertParam.Field> fields = Arrays.asList(
                    new InsertParam.Field("receipt_type", receiptTypes),
                    new InsertParam.Field("supplier_name", supplierNames),
                    new InsertParam.Field("customer_name", customerNames),
                    new InsertParam.Field("embedding", embeddings),
                    new InsertParam.Field("metadata", metadatas));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();

            R<?> insertR = milvusClient.insert(insertParam);
            if (insertR.getStatus() == R.Status.Success.getCode()) {
                log.info("Embedding saved successfully");
            } else {
                log.error("Failed to save embedding: {}", insertR.getMessage());
            }
        } catch (Exception e) {
            log.error("Error saving embedding to Milvus", e);
        }
    }

    /**
     * Tìm kiếm tương tự dựa trên embedding
     */
    public List<Map<String, Object>> searchSimilar(List<Float> queryEmbedding, int topK) {
        if (milvusClient == null) {
            log.warn("Milvus client not available, returning empty results");
            return Collections.emptyList();
        }

        try {
            List<String> outputFields = Arrays.asList("receipt_type", "supplier_name", "customer_name", "metadata");

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(MetricType.L2)
                    .withOutFields(outputFields)
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(queryEmbedding))
                    .withVectorFieldName("embedding")
                    .build();

            R<?> searchR = milvusClient.search(searchParam);
            if (searchR.getStatus() != R.Status.Success.getCode()) {
                log.error("Search failed: {}", searchR.getMessage());
                return Collections.emptyList();
            }

            // Get search results - SearchResultsWrapper constructor takes SearchResults
            Object searchData = searchR.getData();
            if (searchData == null) {
                return Collections.emptyList();
            }

            // Cast to SearchResults and create wrapper
            if (!(searchData instanceof SearchResults)) {
                log.error("Unexpected search result type: {}", searchData.getClass().getName());
                return Collections.emptyList();
            }

            try {
                SearchResults searchResults = (SearchResults) searchData;
                // SearchResultsWrapper constructor takes the results data
                // Based on SDK docs, it should accept the results from SearchResults
                SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResults.getResults());

                List<Map<String, Object>> resultList = new ArrayList<>();

                for (int i = 0; i < wrapper.getIDScore(0).size(); i++) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", wrapper.getIDScore(0).get(i).getLongID());
                    result.put("score", wrapper.getIDScore(0).get(i).getScore());

                    // Lấy các field khác
                    if (wrapper.getFieldData("receipt_type", 0) != null) {
                        result.put("receipt_type", wrapper.getFieldData("receipt_type", 0).get(i));
                    }
                    if (wrapper.getFieldData("supplier_name", 0) != null) {
                        result.put("supplier_name", wrapper.getFieldData("supplier_name", 0).get(i));
                    }
                    if (wrapper.getFieldData("customer_name", 0) != null) {
                        result.put("customer_name", wrapper.getFieldData("customer_name", 0).get(i));
                    }
                    if (wrapper.getFieldData("metadata", 0) != null) {
                        result.put("metadata", wrapper.getFieldData("metadata", 0).get(i));
                    }

                    resultList.add(result);
                }

                return resultList;
            } catch (Exception reflectionEx) {
                log.error("Error accessing search results via reflection", reflectionEx);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error searching in Milvus", e);
            return Collections.emptyList();
        }
    }
}
