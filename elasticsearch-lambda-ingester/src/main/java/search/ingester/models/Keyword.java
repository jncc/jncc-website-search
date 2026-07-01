package search.ingester.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;

public class Keyword {

    @NotBlank
    @JsonProperty("vocab")
    public String vocab;

    @NotBlank
    @JsonProperty("value")
    public String value;

    public String getVocab() { return vocab; }
    public void setVocab(String vocab) { this.vocab = vocab; }
    
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
