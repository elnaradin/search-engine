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
            String url = removeLastDash(s.getUrl());
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
        String url = removeLastDash(s.getUrl());
        Site site = new Site();
        site.setUrl(url);
        site.setName(s.getName());
        site.setStatusTime(new Date());
        site.setStatus(Status.INDEXED);
        return site;
    }
    private static String removeLastDash(String url){
        return url.trim().endsWith("/")
                ? url.substring(0, url.length() - 1)
                : url;
    }
}
