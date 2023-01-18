package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import searchengine.config.JsoupSettings;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexationServiceImpl implements IndexationService {
    private final SitesList sitesList;
    private ForkJoinPool pool;

    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final JsoupSettings settings;
    private final IndexationUtils utils;


    @Override
    public void clearDB() {
        indexRepo.deleteAllInBatch();
        lemmaRepo.deleteAllInBatch();
        pageRepo.deleteAllInBatch();
        siteRepo.deleteAllInBatch();
    }


    @Override
    public void startIndexing() {
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


    @Override
    public boolean siteIsPresent(String url) {
        for (searchengine.config.Site site
                : sitesList.getSites()) {
            if (url.startsWith(site.getUrl())) {
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

    @Override
    public void indexPage(String url) {
        try {
            Document document = Jsoup.connect(url).get();
            Site site = findSiteByPageURL(url);
            utils.savePageToDB(document, site, url.replace(site.getUrl(), ""));
        } catch (IOException ex) {
            setFailed(ex.getMessage());
        }
    }

    @Override
    public void addSitesToDB() {
        siteRepo.saveAll(SiteMapper
                .mapAll(sitesList.getSites()));
    }



    @Override
    public boolean isIndexing() {
        if (pool == null) {
            return false;
        }
        return !pool.isShutdown() || !pool.isTerminated();
    }

    @Override
    public void stopIndexing() {
        while (!pool.isTerminated()) {
            pool.shutdownNow();
        }
        setFailed(IndexationService
                .IS_STOPPED_BY_USER_MESSAGE);
    }

    @Override
    public boolean isStopped() {
        if (pool != null) {
            return pool.isShutdown();
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