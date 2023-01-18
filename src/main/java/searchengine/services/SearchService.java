package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.statistics.Response;

public interface SearchService {
    ResponseEntity<Response> getResponse(String query, String site, Integer offset, Integer limit);
}
