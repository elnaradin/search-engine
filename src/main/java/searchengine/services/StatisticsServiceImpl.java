package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final SitesList sites;
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(!WebScraper.isStopped);
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for (int i = 0; i < sitesList.size(); i++) {
            String url = sitesList.get(i).getUrl();
            url = url.endsWith("/")? url.substring(0, url.length() - 1): url;
            Optional<searchengine.model.Site> optSite = siteRepo.findByUrl(url);
            if (optSite.isEmpty()) {
                searchengine.model.Site s = SiteMapper.map(sitesList.get(i));
                siteRepo.saveAndFlush(s);
            }
            searchengine.model.Site site = siteRepo.findByUrl(url).get();
            detailed.add(getItem(site, total));
        }
        return getResponse(total, detailed);
    }

    private StatisticsResponse getResponse(TotalStatistics total,
                                           List<DetailedStatisticsItem> detailed) {
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private DetailedStatisticsItem getItem(searchengine.model.Site site,
                                           TotalStatistics total) {
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        int pages = pageRepo.countPages(site);
        int lemmas = lemmaRepo.countLemmas(site);
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
