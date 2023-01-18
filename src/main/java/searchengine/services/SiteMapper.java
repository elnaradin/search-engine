package searchengine.services;

import searchengine.model.Site;
import searchengine.model.Status;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SiteMapper {
    public static List<Site> mapAll(List<searchengine.config.Site> sites) {
        List<Site> siteList = new ArrayList<>();
        for (searchengine.config.Site s : sites) {
            String url = s.getUrl().trim().endsWith("/")
                    ? s.getUrl().substring(0, s.getUrl().length() - 1)
                    : s.getUrl();
            Site site = new Site();
            site.setUrl(url);
            site.setName(s.getName());
            site.setStatusTime(new Date());
            site.setStatus(Status.INDEXING);
            siteList.add(site);
        }
        return siteList;
    }

    public static Site map(searchengine.config.Site s) {
        String url = s.getUrl().trim().endsWith("/")
                ? s.getUrl().substring(0, s.getUrl().length() - 1)
                : s.getUrl();
        Site site = new Site();
        site.setUrl(url);
        site.setName(s.getName());
        site.setStatusTime(new Date());
        site.setStatus(Status.INDEXED);

        return site;
    }
}
