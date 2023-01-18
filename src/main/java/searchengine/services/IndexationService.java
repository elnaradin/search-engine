package searchengine.services;


public interface IndexationService {
    String IS_STOPPED_BY_USER_MESSAGE = "Индексация остановлена пользователем";
    void clearDB();
    void startIndexing();
    boolean siteIsPresent(String url);
    void indexPage(String url);
    void addSitesToDB();
    boolean isIndexing();
    void stopIndexing();
    boolean isStopped();
}

