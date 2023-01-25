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

@Service
@RequiredArgsConstructor
@Setter
@Getter
public class IndexationServiceImpl implements IndexationService {


    private final SitesList sitesList;
    private ForkJoinPool pool;
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final JsoupSettings settings;
    private final EntitySaver utils;

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
            if (WebScraper.isStopped ) {
                response.setResult(true);
                return response;
            } else if (!isIndexing()) {
                response.setError(ERRORS[1]);
            }
        return response;
    }

    @Override
    public Response indexPageAndGetIndexPageResponse(String url) {
        Response response = new Response();
        try {
            if (siteIsPresent(url)) {
                response.setResult(true);
                new Thread(()-> indexPage(url)).start();
                return response;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        response.setError(ERRORS[2]);
        return response;
    }

    private void clearDB() {
        indexRepo.deleteAllInBatch();
        lemmaRepo.deleteAllInBatch();
        pageRepo.deleteAllInBatch();
        siteRepo.deleteAllInBatch();
    }


    private void startIndexing() {
        WebScraper.isStopped = false;
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

    private boolean siteIsPresent(String url) throws IOException {
        if(Jsoup.connect(url).ignoreHttpErrors(true)
                .ignoreContentType(true).get().connection()
                .response().statusCode() == 404){
            return false;
        }
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

    private void indexPage(String url) {
        try {
            Document document = Jsoup.connect(url).get();
            Site site = findSiteByPageURL(url);
            utils.indexAndSavePageToDB(document, site,
                    url.replace(site.getUrl(), ""));
        } catch (IOException ex) {
            setFailed(ex.getMessage());
        }
    }

    private void addSitesToDB() {
        siteRepo.saveAll(SiteMapper
                .mapAll(sitesList.getSites()));
    }

    private boolean isIndexing() {
        if (pool == null) {
            return false;
        }
        return !WebScraper.isStopped;
    }

    private void stopIndexing() {
        if (!isIndexing()) {
            return;
        }
        WebScraper.isStopped = true;
        setFailed(IS_STOPPED_BY_USER_MESSAGE);
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