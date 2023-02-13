package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import searchengine.config.SitesList;
import searchengine.dto.statistics.Data;
import searchengine.dto.statistics.Response;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.morphology.LemmaFinderImpl;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private Float maxRelevanceValue;
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final SitesList sitesList;
    private final LemmaFinderImpl lemmaFinder;
    private final SnippetCreator snippetCreator;

    @Override
    public Response searchAndGetResponse(String query, String site,
                                         Integer offset, Integer limit) {
        Response response = new Response();
        Set<String> lemmas = lemmaFinder.getLemmaSet(query
                .replaceAll("[Ёё]", "е"));
        List<Lemma> sortedLemmas = findLemmasInDBAndSort(lemmas);
        if (site != null && !siteIsPresent(site)) {
            response.setError(errors[0]);
            return response;
        }
        if (CollectionUtils.isEmpty(lemmas)) {
            response.setError(errors[1]);
            return response;
        }
        if (sortedLemmas == null) {
            response.setError(errors[2]);
            return response;
        }
        return createResponseWithData(limit, offset,
                sortedLemmas, site);
    }

    private List<Lemma> findLemmasInDBAndSort(Set<String> lemmas) {
        List<Lemma> sortedLemmas = lemmaRepo
                .findByLemmas(lemmas);
        if (sortedLemmas.size() < lemmas.size()) {
            return null;
        }
        return sortedLemmas;
    }

    private boolean siteIsPresent(String site) {
        return sitesList.getSites().stream()
                .anyMatch(s -> (s
                        .getUrl().endsWith("/") ? s
                        .getUrl().substring(0, s
                                .getUrl().length() - 1) : s
                        .getUrl()).equals(site));
    }

    private Response createResponseWithData(int limit, int offset,
                                            List<Lemma> sortedLemmas,
                                            String site) {
        int endIndex = offset + limit;
        Response response = new Response();
        Set<Page> pages = findPages(sortedLemmas, site);
        if (CollectionUtils.isEmpty(pages)) {
            response.setError(errors[3]);
            return response;
        }
        List<Data> dataList = createDataList(sortedLemmas, pages);
        response.setCount(dataList.size());
        response.setResult(true);
        response.setData(dataList.subList(offset,
                Math.min(endIndex, dataList.size())));
        return response;
    }

    private List<Data> createDataList(List<Lemma> sortedLemmas,
                                      Set<Page> pages) {
        List<Data> dataList = new ArrayList<>();
        for (Page page : pages) {
            String content = page.getContent();
            Data data = collectData(page, content, sortedLemmas);
            dataList.add(data);
        }
        dataList.sort(Collections.reverseOrder());
        return dataList;
    }

    private List<Site> getSites(String site) {
        List<Site> sites = new ArrayList<>();
        Optional<Site> optSite = siteRepo.findFirstByUrl(site);
        if (site != null && optSite.isPresent()) {
            sites.add(optSite.get());
        } else {
            sites = siteRepo.findAll();
        }
        return sites;
    }


    private Data collectData(Page page, String content,
                             List<Lemma> sortedLemmas) {
        Data data = new Data();
        data.setSite(page.getSite().getUrl());
        data.setSiteName(page.getSite().getName());
        data.setUri(page.getPath());
        data.setTitle(findTitle(content));
        data.setRelevance(getRelevance(page));
        String text = Jsoup.clean(content, Safelist.relaxed())
                .replaceAll("&nbsp;", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("https?://[\\w\\W]\\S+", "")
                .replaceAll("\\s*\\n+\\s*", " · ");
        data.setSnippet(snippetCreator
                .createSnippet(text, sortedLemmas));
        return data;
    }

    private String findTitle(String content) {
        String titleStart = "<title>";
        String titleEnd = "</title>";
        if (content.contains(titleStart)) {
            int start = content.indexOf(titleStart)
                    + titleStart.length();
            int end = content.indexOf(titleEnd);
            return content.substring(start, end);
        }
        return null;
    }

    private float getRelevance(Page page) {
        if (maxRelevanceValue == null) {
            maxRelevanceValue = indexRepo.getMaxValue();
        }
        return indexRepo.getRelevance(page,
                maxRelevanceValue);
    }

    private Set<Page> findPages(List<Lemma> sortedLemmas, String site) {
        List<Site> sites = getSites(site);
        List<Lemma> nonFrequentLemmas = pickNonFrequentLemmas(sortedLemmas);
        Set<Page> pages = pageRepo
                .findByLemmasAndSites(nonFrequentLemmas, sites);
        for (Lemma sortedLemma : nonFrequentLemmas) {
            Set<Page> foundPages = pageRepo
                    .findByOneLemmaAndSitesAndPages(sortedLemma,
                            sites, pages);
            pages.clear();
            pages.addAll(foundPages);
        }
        return pages;
    }
    private List<Lemma> pickNonFrequentLemmas(List<Lemma> sortedLemmas){
        int maxLemmaFrequency = 250;
        List<Lemma> lowFrequencyLemmas = new ArrayList<>();
        for (Lemma lemma : sortedLemmas) {
            if (lemma.getFrequency() < maxLemmaFrequency) {
                lowFrequencyLemmas.add(lemma);
            }
        }
        return lowFrequencyLemmas;
    }
}