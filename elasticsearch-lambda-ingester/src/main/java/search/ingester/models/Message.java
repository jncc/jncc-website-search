package search.ingester.models;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Message {

    @JsonProperty("S3BucketName")
    private String s3BucketName;

    @JsonProperty("S3Key")
    private String s3Key;

    @JsonProperty("index")
    private String index;

    @JsonProperty("verb")
    private String verb;

    @JsonProperty("document")
    private Document document;

    @JsonProperty("resources")
    private List<Document> resources;

    public String getIndex() { return index; }
    public void setIndex(String index) { this.index = index; }

    public String getVerb() { return verb; }
    public void setVerb(String verb) { this.verb = verb; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public List<Document> getResources() { return resources; }
    public void setResources(List<Document> resources) { this.resources = resources; }

    public String getS3BucketName() { return s3BucketName; }
    public void setS3BucketName(String s3BucketName) { this.s3BucketName = s3BucketName; }

    public String getS3Key() { return s3Key; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }
}

