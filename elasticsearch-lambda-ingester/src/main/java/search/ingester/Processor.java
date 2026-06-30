package search.ingester;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import search.ingester.models.Document;
import search.ingester.models.Message;

public class Processor {

    private ElasticService elasticService;
    private FileParser fileParser;

    public Processor(ElasticService elasticService, FileParser fileParser) {
        this.elasticService = elasticService;
        this.fileParser = fileParser;
    }

    public void process(Message m) throws IOException {

        switch (m.getVerb()) {
        case "upsert":
            processUpsert(m);
            break;
        case "delete":
            processDelete(m);
            break;
        case "spike":
            processSpike(m);
            break;
        default:
            throw new RuntimeException(
                    String.format("Expected verb to be 'upsert' or 'delete' but got %s", m.getVerb()));
        }
    }

    private void processUpsert(Message m) throws IOException {
        Document doc = m.getDocument();

        System.out.println(
                ":: Upserting doc " + doc.getId() + " for site " + doc.getSite() + " in index " + m.getIndex() + " ::");

        // Prepare document
        prepareDocument(doc);

        // Upload document
        upsertDocument(m.getIndex(), doc);
    }

    /**
     * Prepares an individual document to be indexed in an Elasticsearch instance,
     * extract text from any attached base64 encoded file, validate the document and
     * do some general cleanup before forwarding the document to Elasticsearch
     * 
     * @param doc The Document object to prepare
     * @return A finalised document ready to be pushed into an ElasticSearch index
     * @throws IOException
     */
    private void prepareDocument(Document doc) throws IOException {
        validateInput(doc);
        extractContentFromFileBase64IfNecessary(doc);
        DocumentTweaker.setContentTruncatedField(doc);
        DocumentTweaker.setTimestamp(doc);
        validateDocument(doc);
    }

    private void validateInput(Document doc) {
        if (org.apache.commons.lang3.StringUtils.isBlank(doc.getContent()) 
                && org.apache.commons.lang3.StringUtils.isBlank(doc.getFileBase64())) {
            throw new RuntimeException("Document must have either content or file_base64 provided");
        }
    }    

    /**
     * Upserts a prepared document into the current ElasticSearch index
     * 
     * @param index The index to put document into on the instance
     * @param doc The document to put into the given index
     * @throws IOException
     */
    private void upsertDocument(String index, Document doc) throws IOException {
        elasticService.putDocument(index, doc);
    }

    private void extractContentFromFileBase64IfNecessary(Document doc) {	
        // if this doc represents a "file" (e.g. a PDF) then it will have a file_base64	
        // field	
        // which we need to extract into the content field etc.	
        if (doc.getFileBase64() != null) {	
            try {	
                // note this function mutates its argument (and returns it for good measure!)	
                doc = fileParser.parseFile(doc);	
            } catch (Exception err) {	
                throw new RuntimeException(err);	
            }	
        }	
    }
    
    private void validateDocument(Document doc) {
        Validator validator = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()
            .getValidator();
        Set<ConstraintViolation<Document>> violations = validator.validate(doc);

        if (violations.size() > 0) {
            throw new RuntimeException(violations.stream()
                    .map(violation -> violation.getPropertyPath().toString() + ": " + violation.getMessage())
                    .collect(Collectors.joining("\n")));
        }
    }

    private void processDelete(Message m) throws IOException {
        String index = m.getIndex();
        Document doc = m.getDocument();

        System.out.println(
                ":: Deleting doc " + doc.getId() + " for site " + doc.getSite() + " in index " + m.getIndex() + " ::");

        elasticService.deleteDocument(index, doc.getId());
    }

    private void processSpike(Message m) throws IOException {
        // temporary method for testing on AWS Lambda...
    }
}