package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response {
    private boolean result;
    private String error;
    private Integer count;
    private List<searchengine.dto.statistics.Data> data;
}
