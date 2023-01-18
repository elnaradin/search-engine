package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@Service
@RequiredArgsConstructor
public class IndexationUtils {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;


    public  Set<Page> pages = ConcurrentHashMap.newKeySet();
    public  Set<Index> indexes = ConcurrentHashMap.newKeySet();
    public  Set<Lemma> lemmas = ConcurrentHashMap.newKeySet();


    public void savePageToDB(Document document, Site site, String path) throws IOException {
        Page page;
        Optional<Page> optPage = pageRepo
                .findPage(path, site);
        page = optPage.orElseGet(() -> PageBuilder.map(site, document, path));
        pageRepo.saveAndFlush(page);
        Optional<Site> optSite = siteRepo
                .findByUrl(site.getUrl());
        optSite.get().setStatusTime(new Date());
        siteRepo.saveAndFlush(optSite.get());
        saveLemmasAndIndexes(page);
    }


    private void saveLemmasAndIndexes(Page page) throws IOException {
        if (page.getCode() >= 400) {
            return;
        }
        String text = Jsoup.clean(page.getContent(), Safelist.none());
        Map<String, Integer> lemmaSet =
                LemmaFinder.getInstance().collectLemmas(text);
        lemmaSet.forEach((l, r) -> saveLemmas(l, r, page));
    }

    private void saveLemmas(String l, float rank,
                            Page page) {
        Lemma lemma = new Lemma();
        Optional<Lemma> optLemma = lemmaRepo.findByLemma(l);
        if (optLemma.isPresent()) {
            lemma = optLemma.get();
            lemma.getSites().add(page.getSite());
            lemma.setFrequency(optLemma.get().getFrequency() + 1);
        } else {
            lemma.setLemma(l);
            lemma.getSites().add(page.getSite());
            lemma.setFrequency(1);
        }
        lemmaRepo.saveAndFlush(lemma);
        saveIndexes(lemma, page, rank);
    }

    private void saveIndexes(Lemma lemma, Page page, float rank) {
        Optional<Index> optIndex = indexRepo.findByLemmaAndPage(lemma, page);
        Index index = new Index();
        if (optIndex.isPresent()) {
            index = optIndex.get();
        }
        index.setLemma(lemma);
        index.setPage(page);
        index.setRank(rank);
        indexRepo.saveAndFlush(index);
    }
}