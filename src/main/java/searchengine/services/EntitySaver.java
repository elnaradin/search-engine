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


@Getter
@Setter
@Service
@RequiredArgsConstructor
public class EntitySaver {
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final LemmaFinder lemmaFinder;

    public void indexAndSavePageToDB(Document document, Site site, String path) throws IOException {
        Optional<Page> optPage = pageRepo
                .findPage(path, site);
        Page page = optPage.orElseGet(() -> PageBuilder.map(site, document, path));
        Optional<Site> optSite = siteRepo
                .findByUrl(site.getUrl());
        optSite.get().setStatusTime(new Date());

        if (!WebScraper.isStopped) {
            pageRepo.saveAndFlush(page);
            siteRepo.saveAndFlush(optSite.get());
        } else {
            return;
        }
        saveLemmasAndIndexes(page);
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

    private void saveLemmas(String l, float rank,
                            Page page) {
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
            lemmaRepo.saveAndFlush(lemma);
        } else {
            return;
        }
        saveIndexes(lemma, page, rank);
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
            indexRepo.saveAndFlush(index);
        }
    }
}