package searchengine.services;

import org.jsoup.nodes.Document;
import searchengine.model.Page;
import searchengine.model.Site;

public class PageBuilder {
    public static Page map(Site site, Document document, String path) {
        Page page = new Page();
        int code = document.connection().response().statusCode();
        page.setSite(site);
        page.setCode(code);
        page.setPath(path.isBlank()? "/" : path);
        page.setContent(document.html());
        return page;
    }
}
