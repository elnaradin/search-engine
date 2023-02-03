package searchengine.services;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.util.stream.Collectors;


public class WebScraper extends RecursiveAction {
    protected volatile static boolean isStopped = true;
    private final String path;
    private final Site site;
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final JsoupSettings settings;
    private final EntitySaver utils;


    public WebScraper(Site site, String path, SiteRepository siteRepo,
                      PageRepository pageRepo, JsoupSettings settings,
                      EntitySaver utils) {
        this.site = site;
        this.path = path;
        this.siteRepo = siteRepo;
        this.pageRepo = pageRepo;
        this.settings = settings;
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
            utils.indexAndSavePageToDB(document, site, path);
            Set<WebScraper> actionList = ConcurrentHashMap.newKeySet();
            Set<String> urls = getUrls(document);
            for (String url : urls) {
                actionList.add(createActions(url));
            }
            actionList.forEach(ForkJoinTask::join);
        } catch (Exception e) {
            setErrorToSite();
        }
    }

    private Set<String> checkIfStopped(Set<String> urls)
            throws InterruptedException {
        if (isStopped == true) {
            synchronized (this) {
                while (!urls.isEmpty()) {
                    urls.clear();
                    wait();
                }
                notify();
            }
        }
        return urls;
    }


    private WebScraper createActions(String url) throws InterruptedException {
        String path = url.equals(site.getUrl()) ? "/"
                : url.replace(site.getUrl(), "");
        WebScraper action = new WebScraper(
                site, path, siteRepo,
                pageRepo, settings, utils);
        action.fork();
        return action;
    }

    private synchronized Document getDocument() throws IOException, InterruptedException {
        String url = site.getUrl().concat(path);
        Thread.sleep(500);
        return Jsoup.connect(url)
                .userAgent(settings.getUserAgent())
                .referrer(settings.getReferrer())
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .followRedirects(false)
                .get();
    }

    private Set<String> getUrls(Document document) {
        String selector = "a[href]";
        Elements elements = document.select(selector);
        return elements.stream().map(e -> e.absUrl("href"))
                .filter(url -> isCorrectPath(url))
                .collect(Collectors.toSet());
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

    private void setErrorToSite() {
        Optional<Site> optSite = siteRepo.findByUrl(site.getUrl());
        if (optSite.isPresent()) {
            optSite.get().setLastError("Ошибка в процессе обхода сайта");
            siteRepo.saveAndFlush(optSite.get());
        }
    }
}

