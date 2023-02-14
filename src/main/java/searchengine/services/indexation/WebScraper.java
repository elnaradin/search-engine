package searchengine.services.indexation;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.config.JsoupSettings;
import searchengine.model.*;

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
    private final EntitySaver entitySaver;


    public WebScraper(Site site, String path,
                      SiteRepository siteRepo,
                      PageRepository pageRepo,
                      JsoupSettings settings,
                      EntitySaver entitySaver) {
        this.site = site;
        this.path = path;
        this.siteRepo = siteRepo;
        this.pageRepo = pageRepo;
        this.settings = settings;
        this.entitySaver = entitySaver;
    }

    @Override
    protected void compute() {
        try {
            if (pageRepo.existsByPathAndSite(path, site)
                    || isStopped == true) {
                return;
            }
            Document document = getDocument();
            entitySaver.indexAndSavePageToDB(document, site, path);
            Set<WebScraper> actionList = ConcurrentHashMap.newKeySet();
            Set<String> urls = (getUrls(document));
            for (String url : urls) {
                actionList.add(createAction(url));
            }
            ForkJoinTask.invokeAll(actionList);
        } catch (CancellationException ignore) {
        } catch (Exception e) {
            e.printStackTrace();
            setErrorToSite(e);
        }
    }


    private WebScraper createAction(String url) throws InterruptedException {
        String path = url.equals(site.getUrl()) ? "/"
                : url.replace(site.getUrl(), "");
        WebScraper action = new WebScraper(
                site, path, siteRepo,
                pageRepo, settings, entitySaver);
        return action;
    }

    private Document getDocument() throws IOException,
            InterruptedException {
        String url = site.getUrl().concat(path);
        Thread.sleep(500);
        return Jsoup.connect(url)
                .userAgent(settings.getUserAgent())
                .referrer(settings.getReferrer())
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .followRedirects(false)
                .timeout(10_000)
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

    private void setErrorToSite(Exception e) {
        Optional<Site> optSite = siteRepo
                .findFirstByUrl(site.getUrl());
        if (optSite.isPresent()) {
            optSite.get().setStatus(Status.FAILED);
            optSite.get().setLastError("Произошла ошибка " +
                    "при парсинге страницы: " + site.getUrl() + path
                    + " Сообщение ошибки: " + e.toString());
            siteRepo.saveAndFlush(optSite.get());
        }
    }
}

