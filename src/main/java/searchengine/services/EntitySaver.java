package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;


@Getter
@Setter
@Service
@RequiredArgsConstructor
@Scope("prototype")
public class EntitySaver {
    private final SitesList sites;
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final LemmaFinder lemmaFinder;


    protected void indexAndSavePageToDB(Document document, Site site,
                                        String path) throws IOException {
        Page page = createPage(site, document, path);
        Optional<Site> optSite = siteRepo
                .findByUrl(site.getUrl());
        if (optSite.isPresent()) {
            optSite.get().setStatusTime(new Date());
            siteRepo.saveAndFlush(optSite.get());
        }
        if (!WebScraper.isStopped) {
            pageRepo.saveAndFlush(page);
            saveLemmasAndIndexes(page);
        }
    }

    private Page createPage(Site site, Document document, String path) {
        Page page = new Page();
        int code = document.connection().response().statusCode();
        page.setSite(site);
        page.setCode(code);
        page.setPath(path.isBlank() ? "/" : path);
        page.setContent(document.html());
        return page;
    }

    private void saveLemmasAndIndexes(Page page) {
        if (page.getCode() >= 400) {
            return;
        }
        String text = Jsoup.clean(page.getContent(), Safelist.none())
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ");
        Map<String, Integer> lemmaSet =
                lemmaFinder.collectLemmas(text);
        lemmaSet.forEach((l, r) -> saveLemmas(l, r, page));
    }

    private void saveLemmas(String l, float rank, Page page) {
        Lemma lemma = new Lemma();
        Optional<Lemma> optLemma = lemmaRepo.findByLemma(l);
        if (optLemma.isPresent()) {
            lemma = optLemma.get();
            lemma.setSite(page.getSite());
            lemma.setFrequency(optLemma.get().getFrequency() + 1);
        } else {
            lemma.setLemma(l);
            lemma.setSite(page.getSite());
            lemma.setFrequency(1);
        }
        if (!WebScraper.isStopped) {
            synchronized (this) {
                lemmaRepo.saveAndFlush(lemma);
            }
            saveIndexes(lemma, page, rank);
        }
    }

    private void saveIndexes(Lemma lemma, Page page, float rank) {
        Optional<Index> optIndex = indexRepo
                .findByLemmaAndPage(lemma, page);
        Index index = new Index();
        if (optIndex.isPresent()) {
            index = optIndex.get();
        }
        index.setLemma(lemma);
        index.setPage(page);
        index.setRank(rank);
        if (!WebScraper.isStopped) {
            synchronized (this) {
                indexRepo.saveAndFlush(index);
            }
        }
    }

    protected void saveSite(searchengine.config.Site s, Status status) {
        String url = removeLastDash(s.getUrl());
        Site site = new Site();
        site.setUrl(url);
        site.setName(s.getName());
        site.setStatusTime(new Date());
        site.setStatus(status);
        siteRepo.saveAndFlush(site);
    }

    private String removeLastDash(String url) {
        return url.trim().endsWith("/")
                ? url.substring(0, url.length() - 1)
                : url;
    }
}