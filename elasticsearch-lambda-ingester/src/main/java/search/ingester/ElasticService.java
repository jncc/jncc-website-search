package search.ingester;

import java.io.IOException;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.http.AWSRequestSigningApacheInterceptor;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import search.ingester.models.Document;

public class ElasticService {

    private Env env;
    private static RestHighLevelClient esClient;


    public ElasticService(Env env) {
        this.env = env;
    }

    /**
     * Create configured a High Level Elasticsearch REST client with an AWS http interceptor to sign the data package
     * being sent
     *
     * @return A Configured High Level Elasticsearch REST client to send packets to an AWS ES service
     */
    private static RestHighLevelClient getEsClient(Env env) {
        RestHighLevelClient client = ElasticService.esClient;

        if (client == null) {
            String awsServiceName = "es";
            AWS4Signer signer = new AWS4Signer();
            signer.setServiceName(awsServiceName);
            signer.setRegionName(env.AWS_REGION());
            HttpRequestInterceptor interceptor =
                    new AWSRequestSigningApacheInterceptor(awsServiceName, signer, new DefaultAWSCredentialsProviderChain());
            client = new RestHighLevelClient(
                    RestClient.builder(HttpHost.create(env.ES_ENDPOINT()))
                            .setHttpClientConfigCallback(callback -> callback.addInterceptorLast(interceptor)));

            ElasticService.esClient = client;
        }

        return client;
    }

    public void putDocument(String index, Document doc) throws IOException {

        IndexRequest req = new IndexRequest(index, env.ES_DOCTYPE(), doc.getId());

        Jsonb jsonb = JsonbBuilder.create();
        req.source(jsonb.toJson(doc), XContentType.JSON);

        IndexResponse resp = ElasticService.getEsClient(env).index(req, RequestOptions.DEFAULT);

        if (!(resp.getResult() == DocWriteResponse.Result.CREATED
                || resp.getResult() == DocWriteResponse.Result.UPDATED)) {
            throw new RuntimeException(
                    String.format("Index Response return was not as expected got (%d) with the following " +
                            "returned %s", resp.status().getStatus(), resp.toString()));
        }
    }

    public void deleteDocument(String index, String docId) throws IOException {

        DeleteRequest request = new DeleteRequest(index, env.ES_DOCTYPE(), docId);
        DeleteResponse response = ElasticService.getEsClient(env).delete(request, RequestOptions.DEFAULT);

        if (response.getResult() != DocWriteResponse.Result.DELETED) {
            // we only have one queue for all environments, so avoid filling it with 404s which
            // can happen more easily in non-live environments
            boolean nonLive404 = !index.startsWith("live") && response.status().getStatus() == 404;
            if (!nonLive404) {
                throw new RuntimeException(
                        String.format("Index Response not as expected. Got (%d) with the following " +
                                "returned %s", response.status().getStatus(), response.toString()));
            }
        }
    }    

    public void deleteByParentId(String index, String parentDocId) throws IOException {

        DeleteByQueryRequest req = new DeleteByQueryRequest(index);
        req.setQuery(QueryBuilders.matchQuery("parent_id", parentDocId));

        BulkByScrollResponse res = ElasticService.getEsClient(env).deleteByQuery(req, RequestOptions.DEFAULT);

        // TODO: Need to check the response of this
    }
}