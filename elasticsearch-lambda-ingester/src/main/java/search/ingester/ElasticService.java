package search.ingester;

import java.io.IOException;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import search.ingester.models.Document;

public class ElasticService {

    private Env env;
    private static OpenSearchClient osClient;


    public ElasticService(Env env) {
        this.env = env;
    }

    /**
     * Create configured OpenSearch client with AWS SDK v2 transport for native request signing
     *
     * @return A Configured OpenSearch client to send packets to an AWS OpenSearch service
     */
    private static OpenSearchClient getOsClient(Env env) {
        OpenSearchClient client = ElasticService.osClient;

        if (client == null) {
            AwsSdk2Transport transport = new AwsSdk2Transport(
                    ApacheHttpClient.builder().build(),
                    env.ES_ENDPOINT().replace("https://", "").replace("http://", ""),
                    "es",
                    Region.of(env.AWS_REGION()),
                    AwsSdk2TransportOptions.builder()
                            .setCredentials(DefaultCredentialsProvider.create())
                            .build());
            
            client = new OpenSearchClient(transport);
            ElasticService.osClient = client;
        }

        return client;
    }

    public void putDocument(String index, Document doc) throws IOException {

        IndexRequest<Document> req = new IndexRequest.Builder<Document>()
                .index(index)
                .id(doc.getId())
                .document(doc)
                .build();

        IndexResponse resp = ElasticService.getOsClient(env).index(req);

        String result = resp.result().jsonValue();
        if (!("created".equals(result) || "updated".equals(result))) {
            throw new RuntimeException(
                    String.format("Index Response return was not as expected got result '%s'", result));
        }
    }

    public void deleteDocument(String index, String docId) throws IOException {

        DeleteRequest request = new DeleteRequest.Builder()
                .index(index)
                .id(docId)
                .build();
        
        DeleteResponse response = ElasticService.getOsClient(env).delete(request);

        String result = response.result().jsonValue();
        if (!"deleted".equals(result)) {
            // we only have one queue for all environments, so avoid filling it with 404s which
            // can happen more easily in non-live environments
            boolean nonLive404 = !index.startsWith("live") && "not_found".equals(result);
            if (!nonLive404) {
                throw new RuntimeException(
                        String.format("Index Response not as expected. Got result '%s'", result));
            }
        }
    }    
}