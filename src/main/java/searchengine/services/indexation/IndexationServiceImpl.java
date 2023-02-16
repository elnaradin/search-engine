package searchengine.services.indexation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import searchengine.config.JsoupSettings;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.Response;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

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
    private final EntitySaver entitySaver;


    @Override
    public Response startIndexingAndGetResponse() {
        Response response = new Response();
        if (!isIndexing()) {
            response.setResult(true);
            startIndexing();
        } else {
            response.setError(errors[0]);
            response.setResult(false);
        }
        return response;
    }

    @Override
    public Response stopIndexingAndGetResponse() {
        Response response = new Response();
        stopIndexing();
        if (WebScraper.isStopped) {
            response.setResult(true);
            return response;
        } else if (!isIndexing()) {
            response.setError(errors[1]);
        }
        return response;
    }

    @Override
    public Response indexPageAndGetIndexPageResponse(String url) {
        Response response = new Response();
        try {
            if (siteIsPresent(url)) {
                response.setResult(true);
                new Thread(() -> indexPage(url)).start();
                return response;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        response.setError(errors[2]);
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
        for (SiteConfig s : sitesList.getSites()) {
            entitySaver.saveSite(s, Status.INDEXING);
        }
        pool = new ForkJoinPool();
        ArrayList<Thread> threads = new ArrayList<>();
        List<Site> sites = siteRepo.findAll();
        for (Site site : sites) {
            threads.add(new Thread(() -> {
                pool.invoke(new WebScraper(site, "",
                        siteRepo, pageRepo, settings,
                        entitySaver));
                setIndexed(site);
            }));
        }
        threads.forEach(Thread::start);
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
            if (site != null) {
                entitySaver.indexAndSavePageToDB(document, site,
                        url.replace(site.getUrl(), ""));
            }
        } catch (IOException ex) {
            setFailed("Страницу " + url
                    + " проиндексировать не удалось");
        }
    }

    private boolean siteIsPresent(String url) throws IOException {
        if (!url.matches("https?://[\\w\\W]+")) {
            return false;
        }
        if (Jsoup.connect(url).ignoreHttpErrors(true)
                .ignoreContentType(true).get().connection()
                .response().statusCode() == 404) {
            return false;
        }
        for (SiteConfig site
                : sitesList.getSites()) {
            if (url.startsWith(site.getUrl())) {
                return true;
            }
        }
        return false;
    }


    public boolean isIndexing() {
        if (pool == null) {
            return false;
        }
        return !pool.isQuiescent();
    }

    private void stopIndexing() {
        if (!isIndexing()) {
            return;
        }
        WebScraper.isStopped = true;
        pool.shutdownNow();
        setFailed(IS_STOPPED_BY_USER_MESSAGE);
    }


    private void setIndexed(Site site) {
        if (WebScraper.isStopped) {
            return;
        }
        Optional<Site> optSite = siteRepo
                .findFirstByUrl(site.getUrl());
        if (optSite.isPresent() && !optSite.get()
                .getStatus().equals(Status.FAILED)) {
            optSite.get().setStatus(Status.INDEXED);
            optSite.get().setStatusTime(new Date());
            optSite.get().setLastError(null);
            siteRepo.saveAndFlush(optSite.get());
        }
    }

    private void setFailed(String message) {
        List<Site> sites = siteRepo.findAll();
        try {
            if (pool.awaitTermination(3_000,
                    TimeUnit.MILLISECONDS)) {
                sites.forEach(site -> {
                    site.setStatus(Status.FAILED);
                    site.setLastError(message);
                    siteRepo.saveAllAndFlush(sites);
                });
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}