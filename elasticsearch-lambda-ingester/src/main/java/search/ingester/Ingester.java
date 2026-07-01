package search.ingester;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import search.ingester.models.Message;

public class Ingester implements RequestHandler<SQSEvent, Void> {

    // Only set up if we need to read an S3 message, otherwise left as null
    private S3Client s3Client;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Handle an incoming SQS Message and insert into or delete from the relevant search index on a specified AWS
     * Elasticsearch index
     *
     * @param event An incoming SQS event
     * @param context Context object for that incoming event
     * @throws RuntimeException Throws a runtime exception in the case of any caught exceptions
     * @return Returns null if successful or throws a RuntimeException if something goes wrong
     */
    public Void handleRequest(SQSEvent event, Context context) {

        // unless the messages are "batched", we only expect one message,
        // but use a loop anyway in case they are batched in the future
        for (SQSMessage msg : event.getRecords()) {
            
            // uncomment to log the message body in cloudwatch
            System.out.println(":: Message received :: ");
            System.out.println(msg.getBody());

            // workaround Java's checked exceptions
            try {
                // deserialize a Message from the JSON body of the SQS message
                Message message = objectMapper.readValue(msg.getBody(), Message.class);
                handleMessage(message, new Processor(new ElasticService(new Env()), new FileParser()));
            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        // apparently you return null from an AWS Lambda handler on success?
        return null;
    }

    void handleMessage(Message original, Processor processor) throws Exception {

        // the "real" message might be on S3 storage via the SQS Extended Client
        // in which case we will have two properties pointing to the S3 object
        boolean isMessageReallyOnS3 = original.getS3BucketName() != null && original.getS3Key() != null;

        Message m;

        if (isMessageReallyOnS3) {
            this.s3Client = getS3Client();
            m = getMessageFromS3(original.getS3BucketName(), original.getS3Key());
        }
        else {
            m = original;
        }

        processor.process(m);

        if (isMessageReallyOnS3) {
            deleteObjectFromS3(original.getS3BucketName(), original.getS3Key());
        }    
    }

    /**
     * Extracts a message from an S3 file (JSON)
     *
     * @param bucket The bucket that the file exists in
     * @param key The full key of the file in the S3 Bucket
     * @return A translated Message object generated from the JSON read from the given S3 file
     * @throws Exception If the S3 file cannot be streamed down from S3 this error will be thrown
     */
    private Message getMessageFromS3(String bucket, String key) throws Exception {
        // Get the object reference and build a buffered reader around it
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest);
        
        // Extract the JSON object as text from the input stream
        String text = new String(response.readAllBytes(), StandardCharsets.UTF_8);
        response.close();
        
        // Return the extracted message object from the S3 JSON file
        return objectMapper.readValue(text, Message.class);
    }

    /**
     * Deletes a given file from S3 at the provided bucket / key location, this may fail but its not important if it
     * does as it will be eventually caught by the buckets' lifecycle rules
     *
     * @param bucket The bucket to delete from
     * @param key The full key of the object to be removed from the bucket
     */
    private void deleteObjectFromS3(String bucket, String key) {
        DeleteObjectRequest req = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3Client.deleteObject(req);
    }

    /**
     * Returns a configured S3 client using environment variables / default provider chains
     *
     * @return A configured S3 client
     */
    private S3Client getS3Client() {
        return S3Client.builder()
                .region(Region.of(new Env().AWS_REGION()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
