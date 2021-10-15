package search.ingester;

public class Env {
    public String AWS_REGION() { return System.getenv("AWS_REGION"); }
    public String ES_ENDPOINT() { return System.getenv("ES_ENDPOINT"); }
    public String ES_REGION() { return System.getenv("ES_REGION"); }
    public String ES_DOCTYPE() { return System.getenv("ES_DOCTYPE"); }
}
