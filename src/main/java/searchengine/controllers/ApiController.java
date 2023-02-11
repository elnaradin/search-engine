package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.Response;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.indexation.IndexationService;
import searchengine.services.search.SearchService;
import searchengine.services.statistics.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final StatisticsService statisticsService;
    private final IndexationService indexationService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public Response startIndexing() {
        return indexationService
                .startIndexingAndGetResponse();
    }

    @GetMapping("/stopIndexing")
    public Response stopIndexing() {
        return indexationService
                .stopIndexingAndGetResponse();
    }

    @PostMapping("/indexPage")
    public Response indexPage(String url) {
            return indexationService
                    .indexPageAndGetIndexPageResponse(url);
    }

    @GetMapping("/search")
    public Response search(String query, String site,
                                           Integer offset, Integer limit) {
        return searchService.searchAndGetResponse(query, site, offset, limit);
    }
}
