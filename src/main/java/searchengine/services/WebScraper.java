package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaSystemException;
import searchengine.config.JsoupSettings;
import searchengine.model.*;

import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;


public class WebScraper extends RecursiveAction {
    private final String path;
    private final Site site;
    @Autowired
    private final SiteRepository siteRepo;
    @Autowired
    private final PageRepository pageRepo;
    private final JsoupSettings settings;
    @Autowired
    private final LemmaRepository lemmaRepo;
    @Autowired
    private final IndexRepository indexRepo;
    @Autowired
    private final IndexationUtils utils;


    public WebScraper(Site site, String path, SiteRepository siteRepo,
                      PageRepository pageRepo, JsoupSettings settings,
                      LemmaRepository lemmaRepo, IndexRepository indexRepo, IndexationUtils utils) {
        this.site = site;
        this.path = path;
        this.siteRepo = siteRepo;
        this.pageRepo = pageRepo;
        this.settings = settings;
        this.lemmaRepo = lemmaRepo;
        this.indexRepo = indexRepo;
        this.utils = utils;
    }

    @Override
    protected void compute() {
        try {
            if (pageRepo.findByPathAndSite(path, site)
                    .isPresent()) {
                return;
            }
            Document document = getDocument();
            utils.savePageToDB(document, site, path);
            Set<WebScraper> taskList = ConcurrentHashMap.newKeySet();
            Set<String> urls = getUrls(document);
            for (String url : urls) {
                taskList.add(createTasks(url));
            }
            taskList.forEach(ForkJoinTask::join);
        } catch (CancellationException | BeanCreationNotAllowedException
                 | JpaSystemException ignored) {
        } catch (Exception e) {
            setErrorToSite(e);
        }
    }

    private WebScraper createTasks(String url) {
        String path = url.equals(site.getUrl()) ? "/"
                : url.replace(site.getUrl(), "");
        WebScraper task = new WebScraper(
                site, path, siteRepo,
                pageRepo, settings,
                lemmaRepo, indexRepo, utils);
        task.fork();
        return task;
    }

    private Document getDocument() throws IOException, InterruptedException {
        String url = site.getUrl() + path;
        Thread.sleep(500);
        return Jsoup.connect(url)
                .userAgent(settings.getUserAgent())
                .referrer(settings.getReferrer())
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .timeout(40_000)
                .get();
    }

    private Set<String> getUrls(Document document) {
        String attribute = "href";
        Elements elements = document.select("a[href]");
        Set<String> urls = ConcurrentHashMap.newKeySet();
        for (Element element : elements) {
            String url = element.absUrl(attribute);
            if (!isCorrectPath(url)) {
                continue;
            }
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            urls.add(url);
        }
        return urls;
    }


    private boolean isCorrectPath(String url) {
        if (!url.startsWith(site.getUrl())) {
            return false;
        }
        String regex = "[\\w\\W]+(\\.pdf|\\.PDF|\\.doc|\\.DOC" +
                "|\\.png|\\.PNG|\\.jpe?g|\\.JPE?G|\\.JPG" +
                "|\\.php[\\W\\w]|#[\\w\\W]*|\\?[\\w\\W]+)$";
        return !url.matches(regex);
    }

    private void setErrorToSite(Exception e) {

        Optional<Site> optSite = siteRepo.findByUrl(site.getUrl());
        if (optSite.isPresent()) {
            optSite.get().setLastError(e.getMessage());
            siteRepo.saveAndFlush(optSite.get());
        }
    }
}

