package search.ingester.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.Pattern;
import java.util.List;

public class Document {

    @NotBlank
    @JsonProperty("id")
    private String id;

    @NotBlank
    @Pattern(regexp = "datahub|website|website-assets|mhc|sac", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Site must be datahub|website|website-assets|mhc|sac")
    @JsonProperty("site")
    private String site;

    @NotBlank
    @JsonProperty("title")
    private String title;

    //@NotEmpty
    @JsonProperty("keywords")
    private List<Keyword> keywords;

    @JsonProperty("content")
    private String content;

    @JsonProperty("content_truncated")
    private String contentTruncated;

    @JsonProperty("resource_type")
    private String resourceType;

    @JsonProperty("file_base64")
    private String fileBase64;

    @JsonProperty("file_bytes")
    private Integer fileBytes;

    @JsonProperty("file_extension")
    private String fileExtension;

    @NotBlank
    @JsonProperty("url")
    private String url;

    @Pattern(regexp = "^([\\+-]?\\d{4}(?!\\d{2}\\b))((-?)((0[1-9]|1[0-2])(\\3([12]\\d|0[1-9]|3[01]))?|W([0-4]\\d|5[0-2])(-?[1-7])?|(00[1-9]|0[1-9]\\d|[12]\\d{2}|3([0-5]\\d|6[1-6])))([T\\s]((([01]\\d|2[0-3])((:?)[0-5]\\d)?|24\\:?00)([\\.,]\\d+(?!:))?)?(\\17[0-5]\\d([\\.,]\\d+)?)?([zZ]|([\\+-])([01]\\d|2[0-3]):?([0-5]\\d)?)?)?)?$", message = "Must be an ISO 8601 date")
    @JsonProperty("published_date")
    private String publishedDate;

    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", message = "ID must be a UUID")
    @JsonProperty("asset_id")
    private String assetId;

    @NotBlank
    @JsonProperty("timestamp_utc")
    private String timestampUtc;
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<Keyword> getKeywords() { return keywords;}
    public void setKeywords(List<Keyword> keywords) { this.keywords = keywords; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getContentTruncated() { return contentTruncated; }
    public void setContentTruncated(String contentTruncated) { this.contentTruncated = contentTruncated; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getFileBase64() { return fileBase64; }
    public void setFileBase64(String fileBase64) { this.fileBase64 = fileBase64; }

    public Integer getFileBytes() { return fileBytes; }
    public void setFileBytes(Integer fileBytes) { this.fileBytes = fileBytes; }

    public String getFileExtension() { return fileExtension; }
    public void setFileExtension(String fileExtension) { this.fileExtension = fileExtension; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getPublishedDate() { return publishedDate; }
    public void setPublishedDate(String publishedDate) { this.publishedDate = publishedDate; }

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }

    public String getTimestampUtc() { return timestampUtc; }
    public void setTimestampUtc(String timestampUtc) { this.timestampUtc = timestampUtc; }

    public ImmutablePair<Boolean, String> nonAnnotationValidation() {
        if (StringUtils.isBlank(fileBase64) && StringUtils.isBlank(content)) {
            return new ImmutablePair<>(false, "content and content_base64 fields are blank");
        }
        return new ImmutablePair<>(true, null);
    }
}