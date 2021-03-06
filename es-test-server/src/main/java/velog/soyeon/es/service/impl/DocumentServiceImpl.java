package velog.soyeon.es.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;
import velog.soyeon.es.config.EsProperties;
import velog.soyeon.es.service.DocumentService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final RestHighLevelClient client;
    private final EsProperties esProperties;

    private static final String NAME = "name";
    private static final String AGE = "age";
    private static final String SCORE = "score";
    private static final String CLASS = "class";
    private static final String CREATED_AT = "createdAt";

    @Override
    public List<String> getSearch() throws IOException {
        SearchRequest searchRequest = new SearchRequest(esProperties.getStudentIndexName());
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        // ????????? ??????1, ??????22 ??? ??????????????? ?????????.
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder
                .should(QueryBuilders.matchQuery(NAME, "??????1"))
                .should(QueryBuilders.matchQuery(NAME, "??????22"));

        searchSourceBuilder.query(boolQueryBuilder);
        searchRequest.source(searchSourceBuilder);

        SearchHits searchHits = client.search(searchRequest, RequestOptions.DEFAULT).getHits();
        return Optional.ofNullable(searchHits)
                .map(SearchHits::getHits)
                .map(v -> Arrays.stream(v)
                        .map(SearchHit::getSourceAsString)
                        .distinct()
                        .collect(Collectors.toList())
                ).orElse(Collections.emptyList());

        // queryDSL ??? ???????????? ???
        // GET /students/_search
        //{
        //  "query": {
        //    "bool": {
        //      "should": [
        //        {
        //          "match": {
        //            "name": "??????1"
        //          }
        //        },
        //        {
        //          "match": {
        //            "name": "??????22"
        //          }
        //        }
        //      ]
        //    }
        //  }
        //}
    }

    @Override
    public IndexResponse createDocument() throws IOException {
        IndexRequest request = new IndexRequest(esProperties.getStudentIndexName());
        request.source(jsonBuilder()
                .startObject()
                .field(NAME, "??????")
                .field(AGE, 21)
                .field(SCORE, 100)
                .field(CLASS, "B")
                .endObject());
        try {
            return client.index(request, RequestOptions.DEFAULT);
        } catch (ElasticsearchException e) {
            if (e.status() == RestStatus.CONFLICT) {
                log.error("?????? ????????? ?????????????????????.");
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> getDocument(String id) throws IOException {
        GetRequest request = new GetRequest(esProperties.getStudentIndexName(), id);
        GetResponse getResponse = client.get(request, RequestOptions.DEFAULT);
        if (getResponse.isExists()) {
            // ??????????????? ?????? ??????
            return getResponse.getSourceAsMap();
        }
        return null;
    }

    @Override
    public DeleteResponse deleteDocument(String id) throws IOException {
        DeleteRequest deleteRequest = new DeleteRequest(esProperties.getStudentIndexName(), id);
        return client.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    @Override
    public UpdateResponse updateDocumentByScript(String id) throws IOException {
        UpdateRequest updateRequest = new UpdateRequest(esProperties.getStudentIndexName(), id);

        // name ??? ?????? ???????????? ??????
        Map<String, Object> parameterMap = Collections.singletonMap(NAME, "????????????");
        Script inline = new Script(ScriptType.INLINE, "painless", "ctx._source.name = params.name", parameterMap);
        // ?????? ??????????????? ???????????? ?????? ???????????? ScriptType ??? Stored ???
        updateRequest.script(inline);
        // updateRequest.scriptedUpsert(true/false);
        // updateRequest.upsert(inline);

        try {
            return client.update(updateRequest, RequestOptions.DEFAULT);
        } catch (ElasticsearchException e) {
            if (e.status() == RestStatus.NOT_FOUND) {
                System.out.println("???????????? ????????? ???????????? ????????????. ");
            }
        }
        return null;
    }

    @Override
    public UpdateResponse updateDocument(String id) throws IOException {
        // name ??? ?????????????????? ??????
        XContentBuilder builder = jsonBuilder()
                .startObject()
                .field(NAME, "????????????")
                .endObject();

        UpdateRequest updateRequest = new UpdateRequest(esProperties.getStudentIndexName(), id).doc(builder);
        return client.update(updateRequest, RequestOptions.DEFAULT);
    }

    @Override
    public UpdateResponse upsertDocument(String id) throws IOException {
        IndexRequest indexRequest = new IndexRequest(esProperties.getStudentIndexName()).source(jsonBuilder().startObject().field(NAME, "??????").endObject());

        XContentBuilder xContentBuilder = jsonBuilder()
                .startObject()
                .field(CREATED_AT, new Date())
                .endObject();

        UpdateRequest updateRequest = new UpdateRequest(esProperties.getStudentIndexName(), id).doc(xContentBuilder).upsert(indexRequest);
        return client.update(updateRequest, RequestOptions.DEFAULT);
    }

    @Override
    public BulkResponse createDocumentBulk() throws IOException {
        BulkRequest request = new BulkRequest();
        request.add(new IndexRequest(esProperties.getStudentIndexName()).source(XContentType.JSON, NAME, "?????????1"));
        request.add(new IndexRequest(esProperties.getStudentIndexName()).source(XContentType.JSON, NAME, "?????????2"));
        request.add(new IndexRequest(esProperties.getStudentIndexName()).source(XContentType.JSON, NAME, "?????????3"));
        return client.bulk(request, RequestOptions.DEFAULT);
    }

}
