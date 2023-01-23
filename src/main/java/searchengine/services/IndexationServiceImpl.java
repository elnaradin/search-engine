package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import searchengine.config.JsoupSettings;
import searchengine.config.SitesList;
import searchengine.dto.statistics.Response;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Setter
@Getter
public class IndexationServiceImpl implements IndexationService {

    private String singlePageUrl;
    private final SitesList sitesList;
    private ForkJoinPool pool;
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final JsoupSettings settings;
    private final IndexationUtils utils;
    private boolean isStopped;

    @Override
    public Response startIndexingAndGetResponse() {
        Response response = new Response();

        if (!isIndexing()) {
            response.setResult(true);
            startIndexing();
        } else {
            response.setError(ERRORS[0]);
            response.setResult(false);
        }

        return response;
    }

    @Override
    public Response stopIndexingAndGetResponse() {
        Response response = new Response();
        stopIndexing();
        if (pool.isShutdown()) {
            response.setResult(true);
            return response;
        } else if (!isIndexing()) {
            response.setError(ERRORS[1]);
        }
        return response;
    }

    @Override
    public Response getIndexPageResponse() {
        Response response = new Response();
        if (siteIsPresent()) {
            response.setResult(true);
            return response;
        }
        response.setError(ERRORS[2]);
        return response;
    }

    public void clearDB() {
        indexRepo.deleteAllInBatch();
        lemmaRepo.deleteAllInBatch();
        pageRepo.deleteAllInBatch();
        siteRepo.deleteAllInBatch();
    }


    public void startIndexing() {
        clearDB();
        addSitesToDB();
        pool = new ForkJoinPool();
        ArrayList<Thread> threads = new ArrayList<>();
        List<Site> sites = siteRepo.findAll();
        for (Site site : sites) {
            threads.add(new Thread(() -> {
                pool.invoke(new WebScraper(site, "",
                        siteRepo, pageRepo, settings,
                        lemmaRepo, indexRepo, utils));
                setIndexed(site);
            }));
        }
        threads.forEach(Thread::start);
    }

    public boolean siteIsPresent() {
        for (searchengine.config.Site site
                : sitesList.getSites()) {
            if (singlePageUrl.startsWith(site.getUrl())) {
                return true;
            }
        }
        return false;
    }

    private Site findSiteByPageURL(String url) {
        List<Site> siteList = siteRepo.findAll();
        for (Site site : siteList) {
            if (url.startsWith(site.getUrl())) {
                return site;
            }
        }
        return null;
    }

    public void indexPage(String url) {
        singlePageUrl = url;
        if (!siteIsPresent()) {
            return;
        }
        try {
            Document document = Jsoup.connect(url).get();
            Site site = findSiteByPageURL(url);
            utils.savePageToDB(document, site,
                    url.replace(site.getUrl(), ""));
        } catch (IOException ex) {
            setFailed(ex.getMessage());
        }
    }

    public void addSitesToDB() {
        siteRepo.saveAll(SiteMapper
                .mapAll(sitesList.getSites()));
    }

    public boolean isIndexing() {
        if (pool == null) {
            return false;
        }
        return !pool.isShutdown()
                || !pool.isTerminated();
    }

    public void stopIndexing() {
        if (!isIndexing()) {
            return;
        }
        while (!pool.isTerminated()) {
            pool.shutdownNow();
        }
        setFailed(IS_STOPPED_BY_USER_MESSAGE);
    }

    public boolean isStopped() {
        if (pool != null) {
            try {
                return pool.isShutdown() && pool
                        .awaitTermination(100_000,
                                TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    private void setIndexed(Site site) {
        if (!pool.isShutdown()) {
            Optional<Site> optSite = siteRepo
                    .findByUrl(site.getUrl());
            if (optSite.isPresent() && !optSite.get()
                    .getStatus().equals(Status.FAILED)) {
                optSite.get().setStatus(Status.INDEXED);
                siteRepo.saveAndFlush(optSite.get());
            }
        }
    }

    private void setFailed(String message) {
        List<Site> sites = siteRepo.findAll();
        sites.forEach(site -> {
            site.setStatus(Status.FAILED);
            site.setLastError(message);
            siteRepo.saveAllAndFlush(sites);
        });
    }
}