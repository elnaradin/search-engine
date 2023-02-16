package searchengine.services.indexation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.morphology.LemmaFinderImpl;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Getter
@Setter
@Service
@RequiredArgsConstructor
public class EntitySaver {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final LemmaFinderImpl lemmaFinder;

    protected void indexAndSavePageToDB(Document document, Site site,
                                        String path) throws IOException {
        Optional<Site> optSite = siteRepo
                .findFirstByUrl(site.getUrl());
        if (optSite.isPresent()) {
            optSite.get().setLastError(null);
            optSite.get().setStatusTime(new Date());
            siteRepo.saveAndFlush(optSite.get());
        }
        Page page;
        synchronized (this) {
            Optional<Page> optionalPage = pageRepo
                    .findFirstByPathAndSite(path.isBlank() ? "/" : path, site);
            page = optionalPage.orElseGet(() -> createPage(document, site, path));
            pageRepo.saveAndFlush(page);
        }
        if (page.getCode() < 400) {
            saveLemmasAndIndexes(page);
        }
    }

    private Page createPage(Document document, Site site, String path) {
        Page page = new Page();
        int code = document.connection().response().statusCode();
        page.setSite(site);
        page.setCode(code);
        page.setPath(path.isBlank() ? "/" : path);
        page.setContent(document.html());
        return page;
    }

    private void saveLemmasAndIndexes(Page page) {
        Set<Lemma> lemmas = ConcurrentHashMap.newKeySet();
        Set<Index> indices = ConcurrentHashMap.newKeySet();
        String text = Jsoup.clean(page.getContent(), Safelist.relaxed())
                .replaceAll("[Ёё]", "е");
        Map<String, Integer> lemmasWithRanks =
                lemmaFinder.collectLemmas(text);
        synchronized (this) {
            lemmasWithRanks.forEach((l, rank) -> {
                if (WebScraper.isStopped) {
                    return;
                }
                Lemma lemma = createLemma(l, page);
                lemmas.add(lemma);
                indices.add(createIndex(lemma, page, rank));
            });
            lemmaRepo.saveAllAndFlush(lemmas);
            indexRepo.saveAllAndFlush(indices);
        }
    }


    private Lemma createLemma(String l, Page page) {
        Lemma lemma;
        Optional<Lemma> optLemma = lemmaRepo.findFirstByLemma(l);
        if (optLemma.isPresent()) {
            lemma = optLemma.get();
            lemma.setFrequency(optLemma.get().getFrequency() + 1);
        } else {
            lemma = new Lemma();
            lemma.setLemma(l);
            lemma.setSite(page.getSite());
            lemma.setFrequency(1);
        }
        return lemma;
    }

    private Index createIndex(Lemma lemma, Page page, float rank) {
        Index index = new Index();
        index.setLemma(lemma);
        index.setPage(page);
        index.setRank(rank);
        return index;
    }

    public void saveSite(SiteConfig s, Status status) {
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