package searchengine.services.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexation.EntitySaver;
import searchengine.services.indexation.IndexationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final IndexationService indexationService;
    private final EntitySaver entitySaver;
    private final SitesList sites;
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(indexationService.isIndexing());
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteConfig> sitesList = sites.getSites();
        for (SiteConfig value : sitesList) {
            String url = value.getUrl();
            url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            Optional<searchengine.model.Site> optSite = siteRepo.findFirstByUrl(url);
            if (optSite.isEmpty()) {
                entitySaver.saveSite(value, Status.INDEXED);
            }
            Optional<Site> site = siteRepo.findFirstByUrl(url);
            site.ifPresent(s -> detailed.add(createItem(s, total)));
        }
        return createResponse(total, detailed);
    }

    private StatisticsResponse createResponse(TotalStatistics total,
                                              List<DetailedStatisticsItem> detailed) {
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private DetailedStatisticsItem createItem(searchengine.model.Site site,
                                              TotalStatistics total) {
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        int pages = pageRepo.countPageBySite(site);
        int lemmas = lemmaRepo.countLemmaBySite(site);
        item.setPages(pages);
        item.setLemmas(lemmas);
        item.setStatus(site.getStatus().toString());
        item.setError(site.getLastError());
        item.setStatusTime(site.getStatusTime().getTime());
        total.setPages(total.getPages() + pages);
        total.setLemmas(total.getLemmas() + lemmas);
        return item;
    }
}
