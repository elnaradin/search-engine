package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.Response;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexationService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private String error[] = {"Индексация уже запущена",
            "Индексация не запущена",
            "Данная страница находится за пределами сайтов, " +
            "указанных в конфигурационном файле"};
    private final StatisticsService statisticsService;
    private final IndexationService indexationService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Response> startIndexing() {
        Response response = new Response();
        indexationService.clearDB();
        indexationService.addSitesToDB();
        if (!indexationService.isIndexing()) {
            indexationService.startIndexing();
            response.setResult(true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else if (indexationService.isIndexing()) {
            response.setResult(false);
            response.setError(error[0]);
        }
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Response> stopIndexing() {
        Response response = new Response();
        if (indexationService.isIndexing()) {
            indexationService.stopIndexing();
            if (indexationService.isStopped()) {
                response.setResult(true);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
        }
        if (!indexationService.isIndexing()) {
            response.setResult(false);
            response.setError(error[1]);
        }
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Response> indexPage( String url) {
        Response response = new Response();
        if (indexationService.siteIsPresent(url)
                && indexationService.isStopped()) {
            indexationService.indexPage(url);
            response.setResult(true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            response.setError(error[2]);
        }
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    @GetMapping("/search")
    public ResponseEntity<Response> search(String query, String site,
                                           Integer offset, Integer limit){
        return searchService.getResponse(query, site, offset, limit);
    }
}
